(ns think.peer.net
  (:require
    [taoensso.timbre :as log]
    [chord.http-kit :refer [with-channel]]
    [chord.format :as cf]
    [clojure.core.async :refer [<! >! go go-loop] :as async]
    [think.peer.api :as api])
  (:import [org.fressian.handlers WriteHandler ReadHandler]
           [org.fressian.impl ByteBufferInputStream BytesOutputStream]))

(defonce clients* (atom {}))

(defn mapply
  "Apply a map as keyword args to a function.
   e.g.
      (mapply foo {:a 2}) => (foo :a 2)
  "
  [f & args]
  (apply f (apply concat (butlast args) (last args))))

(defn run-handler
  [handler req]
  (let [msg-args (:args req)
        n (count msg-args)
        arglists (:arglists (meta handler))
        arg-counts (map count arglists)
        ok? (some #(= n %) arg-counts)
        _ (println "arglists: " arglists)
        optional? (first (filter #(= '& (first %)) arglists))]
    (cond
      ok?  (apply handler msg-args)
      optional? (handler)
      :default
      (throw (ex-info (str "Incorrect number of arguments passed to function: "
                  n " for function " handler " with arglists " arglists)
                      {})))))

(defn event-handler
  [client-id handlers req]
  (if-let [handler (get-in handlers [:event (:event req)])]
    (run-handler handler req)
    (log/info "Unhandled event: " req)))

(defn rpc-event-handler
  [client-id handlers {:keys [chan id] :as req}]
  (go
    (if-let [handler (get-in handlers [:rpc (:fn req)])]
      (let [v (run-handler handler req)
            res (if (satisfies? clojure.core.async.impl.protocols/ReadPort v)
                  (<! v)
                  v)]
        (>! chan {:event :rpc-response :id id :result res}))
      (log/info "Unhandled rpc-request: " req))))

(defn subscription-event-handler
  [client-id handlers {:keys [chan id] :as req}]
  (go
    (if-let [handler (get-in handlers [:subscription (:fn req)])]
      (let [pub-chan (apply handler (:args req))]
        (if (satisfies? clojure.core.async.impl.protocols/ReadPort pub-chan)
          (do
            (let [event-chan (async/chan 1 (map (fn [v] {:event :publication :id id :value v})))]
              (async/pipe pub-chan event-chan)
              (async/pipe event-chan chan)
              ;(go-loop []
              ;  (when-let [v (<! event-chan)]
              ;    (log/info "publication [" client-id id "]: " v)
              ;    (recur)))
              )
            (swap! clients* assoc-in [client-id :subscriptions id] pub-chan))
          (throw (ex-info (str "Subscription function didn't return a publication channel:" req)
                          {}))))
      (throw (ex-info (str "Unhandled subscription request: " req)
                      {})))))

(defn unsubscription-event-handler
  [client-id {:keys [chan id] :as req}]
  (let [c (get-in @clients* [client-id :subscriptions id])]
    (swap! clients* update-in [client-id :subscriptions] dissoc id)
    (async/close! c)))

(defn disconnect-client
  [client-id]
  (let [client (get @clients* client-id)]
    (swap! clients* dissoc client-id)
    (async/close! (:chan client))
    (doseq [[sub-id sub-chan] (:subscriptions client)]
      (async/close! sub-chan))))

(defn disconnect-all-clients
  []
  (doseq [client-id (keys @clients*)]
    (disconnect-client client-id)))

(defn client-listener
  "Setup a listener go-loop to receive messages from a client."
  [handlers client-id client-chan]
  (go-loop []
    (let [{:keys [message error] :as packet} (<! client-chan)]
      (if (or (nil? packet) error)
        (do
          (log/info "client disconnect")
          (disconnect-client client-id))
        (do
          (when message
            (log/info message)
            (let [message (assoc message
                                 :client-id client-id
                                 :chan client-chan)
                  event-type (:event message)]
              (cond
                (= event-type :rpc) (rpc-event-handler client-id handlers message)
                (= event-type :subscription) (subscription-event-handler client-id handlers message)
                (= event-type :unsubscription) (unsubscription-event-handler client-id message)
                :default (event-handler client-id handlers message)))
            (recur)))))))

(defn connect-client
  "Accepts a map of handlers and an http request.  The map holds hanlders in this shape:

  {:event {'ping #'my.ns/ping}
   :rpc {...}
   :subscription {...}}"
  [handlers req & [options]]
  (with-channel req ws-ch {:format :transit-json}
    (go
      (let [{:keys [message error]} (<! ws-ch)]
        (if error
          (log/warn "Client connect error:" error)
          (let [client-id (:client-id message)]
            (swap! clients* assoc client-id {:chan ws-ch :subscriptions {}})
            (>! ws-ch {:type :connect-reply :success true})
            (client-listener handlers client-id ws-ch)))))))
