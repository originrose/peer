(ns rpc
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go go-loop >!! <!! <! >! chan alts! timeout sliding-buffer]]
            [gniazdo.core :as ws]
            [think.peer.net :as net]
            [think.peer.api :as api]
            [test-api]
            [util :refer [edn->transit transit->edn with-peer-server]]))

(deftest echo-test
  (let [error-count* (atom 0)
        connected?* (atom false)
        disconnected?* (atom false)
        server (net/listen :listener (net/peer-listener
                                       {:api (api/ns-api 'test-api)
                                        :on-connect (fn [e]
                                                      (reset! connected?* true))
                                        :on-disconnect (fn [e]
                                                      (reset! disconnected?* true))
                                        :on-error (fn [e]
                                                    ;(println "error: " e)
                                                    (swap! error-count* inc))})
                           :port 4242)]
    (try
      (let [ch (chan)
            data "hello world"
            socket (ws/connect "ws://localhost:4242/connect"
                               :on-receive #(>!! ch %))]
        (ws/send-msg socket (edn->transit {:type :connect :client-id (java.util.UUID/randomUUID)}))
        (<!! ch)
        (ws/send-msg socket (edn->transit {:event :rpc :id (java.util.UUID/randomUUID) :fn 'echo :args [data]}))
        (is (= data
               (:result (transit->edn (<!! ch)))))

        (ws/send-msg socket (edn->transit {:event :rpc :id (java.util.UUID/randomUUID)
                                           :fn 'bad-function :args []}))

        (ws/send-msg socket (edn->transit {:event :rpc :id (java.util.UUID/randomUUID)
                                           :fn 'bad-function :args []}))

        (is (contains? (:result (transit->edn (<!! ch))) :error))
        (is (contains? (:result (transit->edn (<!! ch))) :error))
        (ws/close socket)
        (is (true? @connected?*))
        (is (= 2 @error-count*))
        (is (true? @disconnected?*)))
      (finally
        (net/close server)))))
