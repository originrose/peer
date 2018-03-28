(ns net-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go go-loop >!! <!! <! >! chan alts! timeout sliding-buffer]]
            [taoensso.timbre :as log]
            [gniazdo.core :as ws]
            [org.httpkit.client :as http]
            [think.peer.net :as net]
            [think.peer.api :as api]
            [io.pedestal.interceptor.chain :as chain]
            [test-api]
            [think.peer.util :as util]))

(defn send-msg
  [socket msg]
  (ws/send-msg socket (util/edn->transit msg)))

(defn connect
  []
  (let [ch (chan)
        socket (ws/connect "ws://localhost:4242/connect" :on-receive #(>!! ch %))]
    (send-msg socket {:type :connect :client-id (java.util.UUID/randomUUID)})
    (is (= :connect-reply) (:type (<!! ch)))
    [ch socket]))

(defn request
  [socket fn-name & args]
  (send-msg socket {:event :rpc
                    :id (java.util.UUID/randomUUID)
                    :fn fn-name
                    :args args}))

(defn subscription
  [socket fn-name & args]
  (send-msg socket {:event :subscription
                    :id (java.util.UUID/randomUUID)
                    :fn fn-name
                    :args args}))

(defn pq
  [m c]
  (println m (:request c) (map :name (:io.pedestal.interceptor.chain/queue c)))
  c)

(defn ps
  [m c]
  (println m (:response c) (map :name (:io.pedestal.interceptor.chain/stack c)))
  c)

(deftest echo-test
  (let [error-count* (atom 0)
        connected?* (atom false)
        disconnected?* (atom false)
        server (net/listen {:listener (net/peer-listener
                                       {:api (api/ns-api 'test-api)
                                        :on-connect (fn [e]
                                                      (reset! connected?* true))
                                        :on-disconnect (fn [e]
                                                      (reset! disconnected?* true))
                                        :middleware [{:name ::error.handler
                                                      :error (fn [ctx e]
                                                               (swap! error-count* inc)
                                                               (assoc ctx :io.pedestal.interceptor.chain/error e))}
                                                     #_{:name ::foo
                                                      :enter (fn [c] (pq "enter" c))
                                                      :leave (fn [c] (ps "leave" c))
                                                      }
                                                     ]})
                           :port 4242})]
    (try
      (let [data "hello world"
            [ch socket] (connect)]
        (request socket 'echo data)
        (is (= data (:result (util/transit->edn (<!! ch)))))
        (request socket 'bad-function)
        (request socket 'bad-function)
        (is (contains? (util/transit->edn (<!! ch)) :error))
        (is (contains? (util/transit->edn (<!! ch)) :error))
        (ws/close socket)
        (is (true? @connected?*))
        (is (= 2 @error-count*))
        (is (true? @disconnected?*)))
      (finally
        (net/close server)))))


(deftest pubsub-test
  (let [server (net/listen {:listener (net/peer-listener
                                        {:api (api/ns-api 'test-api)})
                            :port 4242})]
    (try
      (let [[ch socket] (connect)]
        (subscription socket 'counter)
        (dotimes [i 11]
          (let [msg (util/transit->edn (<!! ch))]
            (->> msg
                 (:value)
                 (= (- 10 i))
                 (is))))
        (ws/close socket))
      (finally
        (net/close server)))))

(def assoc-uuid
  {:name ::attach-uuid
   :enter (fn [context] (assoc context ::uuid (java.util.UUID/randomUUID)))})

(defn partial-args
  [args]
  {:name ::partial-args
   :enter (fn [{:keys [request] :as context}]
            (assoc-in context [:request :args] (concat args (:args request))))})

(defn merge-response
  [v]
  {:name ::merge-response
   :leave (fn [context]
            (assoc context :response (merge (:response context) v)))})

(def log-timer
  {:name ::log-timer
   :enter (fn [context] (assoc context :start-time (System/currentTimeMillis)))
   :leave (fn [context] (assoc-in context [:response :response-time] (- (System/currentTimeMillis) (:start-time context))))})

(deftest middleware-test
  (let [server (net/listen {:listener (net/peer-listener
                                       {:api {:rpc {'test-handler #'test-api/test-handler}}
                                        :middleware [log-timer
                                                     (partial-args [80])
                                                     (merge-response {:foo 42})]})
                           :port 4242})]
    (try
      (let [[ch socket] (connect)
            _ (request socket 'test-handler 20 100)
            response (util/transit->edn (<!! ch))]
        (is (= 200 (:result response)))
        (is (contains? response :response-time))
        (is (= 42 (:foo response)))
        (ws/close socket))
      (finally
        (net/close server)))))

(deftest http-api-test
  (let [server (net/listen {:port 4242
                            :api-ns 'test-api})]
    (try
      (let [msg (util/edn->transit {:id (util/uuid) :args [80 20 100]})
            res (http/put "http://localhost:4242/api/v0/rpc/test-handler"
                           {:body msg})
            response (util/transit->edn (:body @res))]
        (is (= 200 (:result response))))
      (finally
        (net/close server)))))
