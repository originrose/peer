(ns pubsub
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go go-loop >!! <!! <! >! chan alts! timeout sliding-buffer]]
            [gniazdo.core :as ws]
            [think.peer.net :as net]
            [think.peer.api :as api]
            [util :refer [edn->transit transit->edn with-peer-server]]))

(use-fixtures :each with-peer-server)

(deftest pubsub-test
  (let [ch (chan)
        socket (ws/connect "ws://localhost:4242/connect"
                           :on-receive #(>!! ch %))]
    (ws/send-msg socket (edn->transit {:type :connect :client-id (java.util.UUID/randomUUID)}))
    (<!! ch)
    (ws/send-msg socket (edn->transit {:event :subscription :id (java.util.UUID/randomUUID) :fn 'counter :args []}))
    (dotimes [i 11]
      (->> (<!! ch)
           (transit->edn)
           (:value)
           (= (- 10 i))
           (is)))
    (ws/close socket)))

