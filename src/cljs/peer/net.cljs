(ns peer.net
  (:require
    [taoensso.timbre :as log]
    [chord.client :refer [ws-ch]]
    [cljs.core.async :refer [<! >! put!] :as async]
    [goog.string :as gstring :refer [format]])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def DEFAULT-HOST "localhost")
(def DEFAULT-PORT 4242)
(def DEFAULT-PATH "connect")

(def RPC-TIMEOUT 3000)
(def DISPATCH-BUFFER-SIZE 64) ; # of incoming msgs to buffer

(defn- websocket-chan
  [url peer-id on-error on-connect]
  (go
    (let [conn (<! (ws-ch url {:format :transit-json}))
          {:keys [ws-channel error]} conn
          _ (>! ws-channel {:type :connect :peer-id peer-id})
          {:keys [message error]} (<! ws-channel)]
      (if error
        (when on-error
          (on-error {:error :connect
                     :host url}))
        (do
          (when on-connect
            (on-connect message))
          ws-channel)))))

(defn- dispatch-events
  "Dispatch messages from server socket onto the event chan, where
  they can easily be subscribed to by :event type. "
  [server-chan dispatch-chan on-event on-error]
  (go-loop []
    (let [packet (<! server-chan)
          {:keys [message error]} packet]
      (if error
        (when on-error
          (on-error error))
        (do
          (when on-event
            (on-event message))
          (>! dispatch-chan message)
          (recur))))))

(defn disconnect!
  [{:keys [peer-chan on-disconnect] :as conn}]
  (put! peer-chan {:event :disconnect})
  (async/close! peer-chan)
  (when on-disconnect
    (on-disconnect conn)))

(defn event
  [{:keys [peer-chan] :as conn} event & args]
  (let [e {:event event :args args :id (random-uuid)}]
    (put! peer-chan e)))

(defn- event-type-chan
  "Returns a channel onto which all server events of a specific type will be placed."
  [event-chan event-type & [buf]]
  (let [c (if buf (async/chan buf) (async/chan))]
    (async/sub event-chan event-type c)
    c))

(defn- close-event-type-chan
  "Unsubscribe an event type channel from receiving server events."
  [event-chan event-type ch]
  (async/unsub event-chan event-type ch))

(defn- handle-rpc-responses
  [event-chan rpc-map*]
  (let [rpc-events (event-type-chan event-chan :rpc-response)]
    (go-loop []
      (let [{id :id :as event} (<! rpc-events)
            id (str id)
            rpc-map @rpc-map*]
        (when-let [res-chan (get rpc-map id)]
          (>! res-chan event)
          (swap! rpc-map* dissoc id)))
      (recur))))

(defn request
  "Make an RPC request to the server. Returns a channel that will receive the result, or nil on error.
  (The error will be logged to the console.)"
  [{:keys [rpc-map* peer-chan timeout on-error] :as conn} fun & [args]]
  (let [req-id (str (random-uuid))
        res-chan (async/chan)
        t-out (async/timeout (or timeout RPC-TIMEOUT))
        event {:event :rpc :id req-id :fn fun :args (or args [])}]
    (swap! rpc-map* assoc req-id res-chan)
    (go
      (>! peer-chan event)
      (let [[v port] (alts! [res-chan t-out])]
        (cond
          ; The request timed out
          (= port t-out) (do
                           (swap! rpc-map* dissoc req-id)
                           (when on-error
                             (on-error (assoc event :error :timeout)))
                           {:event event
                            :peer-error :timout})

          ; Got response
          (and (= port res-chan)
               (not (nil? (:result v))))      (:result v)

          ; Got error
          (and (= port res-chan)
            (:error v)) (do (when on-error
                              (on-error (merge event v)))
                            {:event event
                             :peer-error (:error v)}))))))

; TODO: connect up the API so it works the same on the client as the server,
; allowing the server to call functions and make subscriptions on the client.
(defn connect
  "Returns a peer connection that can be used to send events, make rpc requests, and
  subscribe to peer event channels."
  [& {:keys [url api host port path on-error on-disconnect on-event on-connect]
      :or {host DEFAULT-HOST
           port DEFAULT-PORT
           path DEFAULT-PATH
           on-error #(log/error "Error: " %)}
      :as args}]
  (go
    (let [url           (or url (format "ws://%s:%s/%s" host port path))
          id            (random-uuid)
          peer-chan     (<! (websocket-chan url id on-error on-connect))
          dispatch-chan (async/chan DISPATCH-BUFFER-SIZE)
          event-chan    (async/pub dispatch-chan :event)
          rpc-map*      (atom {})]
      (dispatch-events peer-chan dispatch-chan on-event on-error)
      (handle-rpc-responses event-chan rpc-map*)
      {:peer-chan peer-chan
       :event-chan event-chan
       :on-error on-error
       :on-disconnect on-disconnect
       :on-event on-event
       :rpc-map* rpc-map*
       :subscription-map* (atom {})
       :api* (atom api)})))

(defn subscribe
  "Subscribe to a remote topic.  Returns a channel that can receive async messages
  published to from the connection."
  [{:keys [event-chan subscription-map* peer-chan] :as conn} topic & args]
  (let [flow-events (event-type-chan event-chan :publication (async/sliding-buffer 1))
        id (random-uuid)
        publication-chan (async/chan 1 (filter #(= id (:id %))))
        value-chan (async/chan 1 (map :value))
        event {:event :subscription
               :id id
               :fn topic
               :args (or args [])}]
    (swap! subscription-map* assoc value-chan id)
    (put! peer-chan event)
    (async/pipe flow-events publication-chan)
    (async/pipe publication-chan value-chan)
    value-chan))

(defn unsubscribe
  "Unsubscribe from a remote topic channel for a connection."
  [{:keys [subscription-map* peer-chan] :as conn} ch]
  (let [id (get @subscription-map* ch)
        event {:event :unsubscription :id id}]
    (put! peer-chan event)
    (async/close! ch)
    (swap! subscription-map* dissoc id)))
