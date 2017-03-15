(ns rpc
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go go-loop >!! <!! <! >! chan alts! timeout sliding-buffer]]
            [gniazdo.core :as ws]
            [think.peer.net :as net]
            [think.peer.api :as api]
            [test-api]
            [util :refer [edn->transit transit->edn with-peer-server]]))

(use-fixtures :each with-peer-server)

(deftest echo-test
  (let [ch (chan)
        data "hello world"
        socket (ws/connect "ws://localhost:4242/connect"
                           :on-receive #(>!! ch %))]
    (ws/send-msg socket (edn->transit {:type :connect :client-id (java.util.UUID/randomUUID)}))
    (<!! ch)
    (ws/send-msg socket (edn->transit {:event :rpc :id (java.util.UUID/randomUUID) :fn 'echo :args [data]}))
    (->> (<!! ch)
         (transit->edn)
         (:result)
         (= data)
         (is))
    (ws/close socket)))
