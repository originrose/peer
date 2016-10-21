(ns think.peer.net
  (:require [chord.http-kit :refer [with-channel]]
            [chord.format :as cf]
            [clojure.core.async :refer [<! >! go go-loop] :as async]))

(defonce clients* (atom {}))

(defmulti event-handler (fn [req] (:event req)))
(defmulti rpc-handler (fn [req] (:method req)))

(defmethod event-handler :default
  [req]
  (println "Unhandled net-event: " req))

(defmethod rpc-handler :default
  [req]
  (println "Unhandled rpc-call: " req))

(defn mapply
  "Apply a map as keyword args to a function.
   e.g.
      (mapply foo {:a 2}) => (foo :a 2)
  "
  [f & args]
  (apply f (apply concat (butlast args) (last args))))

(defmethod event-handler :rpc
  [{:keys [chan id] :as req}]
  (go
    (let [v (rpc-handler req)
          res (if (satisfies? clojure.core.async.impl.protocols/ReadPort v)
                (<! v)
                v)]
      (>! chan {:event :rpc-response :id id :result res}))))

(defn disconnect-client
  [client-id]
  (let [c (get @clients* client-id)]
    (swap! clients* dissoc client-id)
    (when c (async/close! c))))

(defn client-listener
  "Setup a listener go-loop to receive messages from a client."
  [client-id client-chan]
  (go-loop []
    (let [{:keys [message error]} (<! client-chan)]
      (if error
        (do
          (println "client error or bad message: ")
          (println error)
          (disconnect-client client-id))
        (do
          (when message
            (println message)
            (event-handler  (assoc message
                                      :client-id client-id
                                      :chan (get @clients* client-id)))
            (recur)))))))

(defn connect-client
  [req]
  (with-channel req ws-ch {:format :transit-json}
    (go
      (let [{:keys [message error]} (<! ws-ch)]
        (if error
          (println "Client connect error:" error)
          (let [client-id (:client-id message)]
            (swap! clients* assoc client-id ws-ch)
            (>! ws-ch {:type :connect-reply :success true})
            (client-listener client-id ws-ch)))))))

