(ns net-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go go-loop >!! <!! <! >! chan alts! timeout sliding-buffer]]
            [taoensso.timbre :as log]
            [gniazdo.core :as ws]
            [think.peer.net :as net]
            [think.peer.api :as api]
            [test-api]
            [util :refer [edn->transit transit->edn]]))

(defn send-msg
  [socket msg]
  (ws/send-msg socket (edn->transit msg)))

(defn connect
  []
  (let [ch (chan)
        socket (ws/connect "ws://localhost:4242/connect" :on-receive #(>!! ch %))]
    (send-msg socket {:type :connect :client-id (java.util.UUID/randomUUID)})
    (is (= :connect-reply) (:type (<!! ch)))
    [ch socket]))

(defn request
  [socket fn-name & args]
  (send-msg socket {:event :rpc :id (java.util.UUID/randomUUID)
                    :fn fn-name :args args}))

(defn subscription
  [socket fn-name & args]
  (send-msg socket {:event :subscription :id (java.util.UUID/randomUUID)
                    :fn fn-name :args args}))

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
                                                               ctx)}]})
                           :port 4242})]
    (try
      (let [data "hello world"
            [ch socket] (connect)]
        (request socket 'echo data)
        (is (= data (:result (transit->edn (<!! ch)))))
        (request socket 'bad-function)
        (request socket 'bad-function)
        (is (contains? (:result (transit->edn (<!! ch))) :error))
        (is (contains? (:result (transit->edn (<!! ch))) :error))
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
          (let [msg (transit->edn (<!! ch))]
            (->> msg
                 (:value)
                 (= (- 10 i))
                 (is))))
        (ws/close socket))
      (finally
        (net/close server)))))

(defn test-handler
  [v a b]
  (+ v a b))

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
            (println "merge response... " (merge (:response context) v))
            (assoc context :response (merge (:response context) v)))})

(def log-timer
  {:name ::log-timer
   :enter (fn [context] (assoc context :start-time (System/currentTimeMillis)))
   :leave (fn [context] (assoc-in context [:response :response-time] (- (System/currentTimeMillis) (:start-time context))))})

(deftest middleware-test
  (let [msgs* (atom [])
        server (net/listen {:listener (net/peer-listener
                                       {:api {:rpc {'test-handler #'test-handler}}
                                        :middleware [log-timer
                                                     (partial-args [80])
                                                     (merge-response {:foo 42})]})
                           :port 4242})]
    (try
      (let [[ch socket] (connect)
            _ (request socket 'test-handler 20 100)
            response (transit->edn (<!! ch))]
        (println "response: " response)
        (is (= 200 (:result response)))
        (is (contains? response :response-time))
        (is (= 42 (:foo response)))
        (ws/close socket))
      (finally
        (net/close server)))))
