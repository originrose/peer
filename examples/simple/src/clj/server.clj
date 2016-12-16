(ns server
  (:require [think.peer.net :as net]
            [think.peer.api :as api]
            [api]
            [think.peer.server :as server])
  (:gen-class))

(defn -main
  [& args]
  (server/start (api/ns-api 'api) 4242))
