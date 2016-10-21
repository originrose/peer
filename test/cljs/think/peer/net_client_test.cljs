(ns think.peer.net-client-test
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [reagent.core :as reagent]
            [cljs.core.async :refer [put! chan <! >! timeout close!]]
            [think.peer.net :as net]))

(enable-console-print!)

(def SERVER-URL "ws://localhost:4242/connect")

(defn test-page
  []
  (let [data* (reagent/atom false)]
    (go
      (let [v (<! (net/request :foo))]
        (println "got data: " v)
        (reset! data* v)))
    (fn []
      [:div
       [:h3 "Test page"]
       (if-let [data @data*]
         [:div
          [:h4 "Received data:"]
          [:div (str data)]]
         [:div "no data yet..."])])))

(defn ^:export -main
  []
  (go
    (println "connecting to server")
    (<! (net/connect-to-server SERVER-URL))
    (println "rendering test component")
    (reagent/render [test-page] (.getElementById js/document "app"))
    (println "mounted")))

(-main)
