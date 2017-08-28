(ns simple.server
  (:require [simple.api]
            [think.peer.net :as peer])
  (:gen-class))

(defn -main
  [& args]
  (let [port 4242]
    (peer/listen {:port port
                  :api-ns 'simple.api
                  :js "js/think.peer.js"
                  :css "css/styles.css"})
    (println "Started peer server on port:" port)))
