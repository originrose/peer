(ns think.peer.net
  (:require
    [chord.client :refer [ws-ch]]
    [cljs.core.async :refer [<! >! put!] :as async])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def DEFAULT-HOST "localhost")
(def DEFAULT-PORT 4242)
(def RPC-TIMEOUT 3000)
(def DISPATCH-BUFFER-SIZE 64) ; # of incoming msgs to buffer

(def log println)

(defonce CLIENT-ID (random-uuid))
(defonce rpc-map* (atom {}))
(defonce subscription-map* (atom {}))
(defonce server-chan* (atom nil))
(defonce event-chan* (atom nil))

(defn setup-websocket
  [url]
  (go
    (let [conn (<! (ws-ch url {:format :transit-json}))
          {:keys [ws-channel error]} conn
          _ (>! ws-channel {:type :connect :client-id CLIENT-ID})
          {:keys [message error]} (<! ws-channel)]
      (if error
        (do
          (log "Error connecting to server:")
          (js/console.log error))
        (do
          (log "Connected to server!")
          ws-channel)))))

(defn dispatch-server-events
  [server-chan dispatch-chan]
  "Dispatch messages from server socket onto the event chan, where
  they can easily be subscribed to by :event type. "
  (go-loop []
    (let [packet (<! server-chan)
          {:keys [message error]} packet]
      (if error
        (log "Websocket Error on server channel: " error)
        (do
          ;(log "Server Event: " (:event message))
          (>! dispatch-chan message)
          (recur))))))

(defn server-event-chan
  "Given the websocket channel, returns a core.async/pub channel that can be
  subscribed to in order to receive server events of a specific type."
  [server-chan]
  (let [dispatch-chan (async/chan DISPATCH-BUFFER-SIZE)
        event-chan    (async/pub dispatch-chan :event)]
    (dispatch-server-events server-chan dispatch-chan)
    event-chan))

(defn disconnect-server
  []
  (put! @server-chan* {:event :disconnect})
  (reset! server-chan* nil))

(defn send-event
  [event & args]
  (let [e {:event event :args args :id (random-uuid)}]
    (log "sending event: " e)
    (put! @server-chan* e)))

(defn subscribe-server-event
  "Returns a channel onto which all server events of a specific type will be placed."
  [event-type & [buf]]
  (let [c (if buf (async/chan buf) (async/chan))]
    (async/sub @event-chan* event-type c)
    c))

(defn unsubscribe-server-event
  "Unsubscribe a channel from receiving server events."
  [event-type ch]
  (async/unsub @event-chan* event-type ch))

(defn handle-rpc-events
  []
  (let [rpc-events (subscribe-server-event :rpc-response)]
    (go-loop []
      (let [{:keys [id] :as event} (<! rpc-events)]
        (log "rpc response: " event)
        (when-let [res-chan (get @rpc-map* id)]
          (>! res-chan event)
          (swap! rpc-map* dissoc id)))
      (recur))))

(defn request
  "Make an RPC request to the server.  Returns a channel that will receive the result, or nil on error.
  (The error will be logged to the console.)"
  [fun & [args]]
  (let [req-id (random-uuid)
        res-chan (async/chan)
        t-out (async/timeout RPC-TIMEOUT)
        event {:event :rpc :id req-id :fn fun :args (or args [])}]
    (log "request:" event)
    (swap! rpc-map* assoc req-id res-chan)
    (go
      (>! @server-chan* event)
      (let [[v port] (alts! [res-chan t-out])]
        (cond
          (= port t-out) (do ; Timeout
                           (swap! rpc-map* dissoc req-id)
                           (throw
                             (js/Error. (str "RPC Timeout: " fun))))

          (and (= port res-chan) ; Got response
               (:result v))      (:result v)

          (and (= port res-chan) ; Got error
               (:error v))       (log (str "RPC Error: " (:error v))))))))

(defn connect-to-server
  [url]
  (go
    (let [server-chan (<! (setup-websocket url))
          event-chan  (server-event-chan server-chan)]
      (reset! server-chan* server-chan)
      (reset! event-chan* event-chan)
      (handle-rpc-events))))

(defn subscribe-to
  "Returns a channel that will receive the data for a given publication."
  [subscription & args]
  (let [flow-events (subscribe-server-event :publication (async/sliding-buffer 1))
        id (random-uuid)
        publication-chan (async/chan 1 (filter #(= id (:id %))))
        value-chan (async/chan 1 (map :value))
        event {:event :subscription :id id
               :fn subscription :args (or args [])}]
    (log "subscription: " event)
    (swap! subscription-map* assoc value-chan id)
    (put! @server-chan* event)
    (async/pipe flow-events publication-chan)
    (async/pipe publication-chan value-chan)
    value-chan))

(defn unsubscribe-from
  [ch]
  (let [id (get subscription-map* ch)
        event {:event :unsubscription :id id}]
    (swap! subscription-map* dissoc id)
    (put! @server-chan* event)))
