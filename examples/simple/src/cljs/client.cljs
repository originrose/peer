(ns client
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [reagent.core :as reagent]
            [cljs.core.async :refer [put! chan <! >! timeout close!]]
            [think.peer.net :as net]
            #_[think.ui.components.canvas :refer [canvas-graph]]))

(enable-console-print!)

(def SERVER-URL "ws://localhost:4242/connect")

(def a* (reagent/atom nil))
(def b* (reagent/atom nil))
(def c* (reagent/atom nil))
(def vals* (reagent/atom nil))

(defn example-requests
  []
  (go
    (reset! a* (<! (net/request 'hello-world)))
    (reset! b* (<! (net/request 'multiply-ints [10 20])))))

(defn listen-to-counter
  []
  (let [cnt-chan (net/subscribe-to 'second-counter)]
    (go-loop []
      (when-let [v (<! cnt-chan)]
        (reset! c* v)
        (recur)))))

(defn update-chart
  [opts*]
  (let [val-chan (net/subscribe-to 'rand-value-eventer 0 10 250)]
    (go-loop []
      (when-let [v (<! val-chan)]
       (swap! opts* update-in [:data 0 :dataPoints]
              (fn [data]
                (conj data (clj->js {:x (count data) :y v}))))
       (recur)))))

(defn test-page
  []
  (let [data* (reagent/atom false)
        graph-opts* (reagent/atom
                     {:title {:test "Streaming Chart"}
                      :zoomEnabled false
                      :animationEnabled true
                      :showLegend false
                      :panEnabled false
                      :axisX {:title "Time"
                              :minimum 0}
                      :data [{:type "spline"
                              :dataPoints []}]})]
    (example-requests)
    (update-chart graph-opts*)
    (fn []
      [:div
       [:h3 "Test page"]
       [:div.examples
        [:div "hello: " @a*]
        [:div "10 * 20 = " @b*]
        [:div "count: " @c*]
        #_[:div [canvas-graph graph-opts*]]
        (if-let [data @data*]
          [:div
           [:h4 "Received data:"]
           [:div (str data)]]
          [:div "no data yet..."])]])))

(defn ^:export -main
  []
  (go
    (<! (net/connect-to-server SERVER-URL))
    (net/send-event 'hello-event)
    (listen-to-counter)
    (reagent/render [test-page] (.getElementById js/document "app"))))

(-main)
