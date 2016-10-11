(ns think.peer.net
  (:require [chord.http-kit :refer [with-channel]]
            [chord.format :as cf]
            [clojure.core.async :refer [<! >! go go-loop] :as async]
            [clojure.core.matrix :as mat]
            [thinktopic.matrix.fressian :as mfress]
            [clojure.data.fressian :as fressian]
            [clojure.repl :as repl])
  (:import [org.fressian.handlers WriteHandler ReadHandler]
           [org.fressian.impl ByteBufferInputStream BytesOutputStream]))

#_(defmethod cf/formatter* :fressian [_]
  (reify cf/ChordFormatter
    (freeze [_ obj]
      ;(println "writing fressian data:" (keys obj))
      (let [arr (fressian/write obj :handlers (mfress/array-write-handlers mikera.arrayz.impl.AbstractArray))]
        (ByteBufferInputStream. arr)))

    (thaw [_ s]
      (try
        (fressian/read s :handlers (mfress/array-read-handlers))
      (catch Exception e
        (println "Error reading fressian:")
        (println e)
        (clojure.stacktrace/print-stack-trace e))))))

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

;; Some experimental reflection capabilities to power code browser, editor,
;; repl, experiment platform.
(defmethod rpc-handler :namespaces
  [req]
  (sort (map ns-name (all-ns))))

(defmethod rpc-handler :ns-vars
  [{:keys [args]}]
  (keys (ns-publics (:ns args))))

(defmethod rpc-handler :var-info
  [{:keys [args]}]
  (let [src (repl/source-fn (symbol (str (:ns args)) (str (:var args))))]
    (println "got source: " src)
    src))

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
  (with-channel req ws-ch ;{:format :fressian}
    (go
      (let [{:keys [message error]} (<! ws-ch)]
        (if error
          (println "Client connect error:" error)
          (let [client-id (:client-id message)]
            (swap! clients* assoc client-id ws-ch)
            (>! ws-ch {:type :connect-reply :success true})
            (client-listener client-id ws-ch)))))))

