(ns think.peer.net
  (:require
    [chord.client :refer [ws-ch]]
    [fressian-cljs.core :as fressian]
    [fressian-cljs.reader :as freader]
    [chord.format :as cf]
    [chord.format.fressian]
    [thinktopic.matrix.fressian :as mf]
    [cljs.core.async :refer [<! >! put!] :as async]
    [clojure.core.matrix :as mat])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def RPC-TIMEOUT 3000)
(def DISPATCH-BUFFER-SIZE 64) ; # of incoming msgs to buffer

(def log println)

(defonce CLIENT-ID (random-uuid))
(defonce rpc-map* (atom {}))
(defonce server-chan* (atom nil))
(defonce event-chan* (atom nil))

(defmethod cf/formatter* :fressian [_]
  (reify cf/ChordFormatter
    (freeze [writer obj]
      (fressian/write obj))

    (thaw [_ s]
      (fressian/read s :handlers (merge fressian/cljs-read-handler mf/READ-HANDLERS)))))

(defn setup-websocket
  [url]
  (go
    (let [conn (<! (ws-ch url {:format :fressian}))
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
  [event & [args]]
  (let [e {:event event :args (or args {})}]
    (println "sending event: " e)
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
        (when-let [res-chan (get @rpc-map* id)]
          (>! res-chan event)
          (swap! rpc-map* dissoc id)))
      (recur))))

(defn request
  "Make an RPC request to the server.  Returns a channel that will receive the result, or nil on error.
  (The error will be logged to the console.)"
  [method & [args]]
  (let [req-id (random-uuid)
        res-chan (async/chan)
        t-out (async/timeout RPC-TIMEOUT)
        event {:event :rpc :id req-id :method method :args (or args {})}]
    (println "event: " event)
    (swap! rpc-map* assoc req-id res-chan)
    (go
      (>! @server-chan* event)
      (let [[v port] (alts! [res-chan t-out])]
        (cond
          (= port t-out) (do ; Timeout
                           (swap! rpc-map* dissoc req-id)
                           (log (str "RPC Timeout: " method))
                           (throw
                             (js/Error. (str "RPC Timeout: " method))))

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
  [output]
  (let [flow-events (subscribe-server-event :flow (async/sliding-buffer 1))
        out-key (keyword output)]
    (send-event :subscribe {:output output})
    (async/map
      (fn [event]
        (get (:data event) out-key))

      [(async/filter<
        (fn [{:keys [data] :as event}]
          (and data (contains? data out-key)))
        flow-events)])))

;(defn unsubscribe-to-element-output
;  "Unsubscribe from an elements output port."
;  [output ch]
;  (send-event :unsubscribe {:output output})
;  (unsubscribe-server-event "flow" ch))
