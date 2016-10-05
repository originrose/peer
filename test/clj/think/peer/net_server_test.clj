(ns think.peer.net-server-test
  (:require [think.peer.net :as net]
            [think.peer.server :as server]))

(defmethod net/rpc-handler :foo
  [req]
  (str "big number: " (rand-int 1000000)))

(server/start)
