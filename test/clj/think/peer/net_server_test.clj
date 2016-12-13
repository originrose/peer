(ns think.peer.net-server-test
  (:require [think.peer.net :as net]
            [think.peer.server :as server])
  (:gen-class))

;; (defmethod net/rpc-handler :foo
;;   [req]
;;   {:string "foo"
;;    :long 2342342
;;    :double 23.223
;;    :keyword :foo
;;    :symbol 'asdf
;;    :map {:a 1 :b 2}
;;    :vector [1 2 3 4 5]})

(defn -main
  [& args]
  (server/start))
