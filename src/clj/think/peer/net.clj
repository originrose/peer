(ns think.peer.net
  (:require
    [taoensso.timbre :as log]
    [chord.http-kit :refer [with-channel]]
    [chord.format :as cf]
    [clojure.core.async :refer [<! >! go go-loop] :as async])
  (:import [org.fressian.handlers WriteHandler ReadHandler]
           [org.fressian.impl ByteBufferInputStream BytesOutputStream]))

(defonce clients* (atom {}))

(defmulti event-handler (fn [req] (:event req)))
(defmulti rpc-handler (fn [req] (:method req)))

(defmethod event-handler :default
  [req]
  (log/info "Unhandled net-event: " req))

(defmethod rpc-handler :default
  [req]
  (log/info "Unhandled rpc-call: " req))

(defn mapply
  "Apply a map as keyword args to a function.
   e.g.
      (mapply foo {:a 2}) => (foo :a 2)
  "
  [f & args]
  (apply f (apply concat (butlast args) (last args))))

(defn rpc-event-handler
  [handlers {:keys [chan id] :as req}]
  (log/info "rpc-event-handler: " req)
  (go
    (if-let [handler (get-in handlers [:rpc (:method req)])]
      (let [v (apply handler (:args req))
            res (if (satisfies? clojure.core.async.impl.protocols/ReadPort v)
                  (<! v)
                  v)]
        (>! chan {:event :rpc-response :id id :result res}))
      (log/info "Unhandled rpc-request: " req))))

(defn disconnect-client
  [client-id]
  (let [c (get @clients* client-id)]
    (swap! clients* dissoc client-id)
    (when c (async/close! c))))

(defn client-listener
  "Setup a listener go-loop to receive messages from a client."
  [handlers client-id client-chan]
  (go-loop []
    (let [{:keys [message error]} (<! client-chan)]
      (if error
        (do
          (log/warn "client error or bad message: ")
          (log/warn error)
          (disconnect-client client-id))
        (do
          (when message
            (log/info message)
            (let [message (assoc message
                                 :client-id client-id
                                 :chan (get @clients* client-id))
                  event-type (:event message)
                  handler (get-in handlers [:event event-type])]
              (if handler
                (if (= event-type :rpc)
                  (handler handlers message)
                  (handler message))
                (log/info "Unhandled event: " message)))
            (recur)))))))

(defn connect-client
  "Accepts a map of handlers and an http request.  The map holds hanlders in this shape:

  {:event {'ping #'my.ns/ping}
   :rpc {...}
   :subscribe {...}
  }
  "
  [handlers req & [options]]
  (let [handlers (assoc-in handlers [:event :rpc] #'rpc-event-handler)]
    (with-channel req ws-ch {:format :transit-json}
      (go
        (let [{:keys [message error]} (<! ws-ch)]
          (if error
            (log/warn "Client connect error:" error)
            (let [client-id (:client-id message)]
              (swap! clients* assoc client-id ws-ch)
              (>! ws-ch {:type :connect-reply :success true})
              (client-listener handlers client-id ws-ch))))))))
