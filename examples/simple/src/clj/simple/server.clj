(ns simple.server
  (:require [simple.api]
            [think.peer.net :as peer])
  (:gen-class))

(defn -main
  [& args]
  (peer/listen {:port 4242
                :api-ns 'simple.api
                :js "js/think.peer.js"
                :css "css/styles.css"}))
