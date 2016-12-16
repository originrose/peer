(ns server
  (:require [think.peer.net :as net]
            [think.peer.server :as server])
  (:gen-class))

(defn -main
  [& args]
  (server/start))
