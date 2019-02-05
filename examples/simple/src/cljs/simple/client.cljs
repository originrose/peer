(ns simple.client
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [reagent.core :as reagent]
            [cljs.core.async :refer [put! chan <! >! timeout close!]]
            [peer.net :as net]))

(enable-console-print!)

(def SERVER-URL "ws://localhost:4242/connect")

(def a* (reagent/atom nil))
(def b* (reagent/atom nil))
(def c* (reagent/atom nil))
(def vals* (reagent/atom nil))

(defn example-requests
  [conn]
  (go
    (reset! a* (<! (net/request conn 'hello-world)))
    (reset! b* (<! (net/request conn 'multiply-ints [10 20])))))

(defn listen-to-counter
  [conn]
  (let [cnt-chan (net/subscribe conn 'second-counter)]
    (go-loop []
      (when-let [v (<! cnt-chan)]
        (reset! c* v)
        (recur)))))

(defn rand-multiply
  [conn v*]
  (go
    (reset! v* (<! (net/request conn 'multiply-ints [(rand-int 10) (rand-int 10)])))))

(defn test-page
  [conn]
  (let [data* (reagent/atom 100)
        val-chan (net/subscribe conn 'rand-value-eventer 100 200 500)
        v* (atom 0)]
    (go-loop []
      (when-let [v (<! val-chan)]
        (reset! data* v)
        (recur)))
    (example-requests conn)
    (fn []
      [:div
       [:h3 "Test page"]
       [:div.examples
        [:div "hello: " @a*]
        [:div "10 * 20 = " @b*]
        [:div "count: " @c*]
        [:div {:style {:padding "5px"
                       :color :white
                       :background-color :green}
               :on-click #(rand-multiply conn v*)}
         "Press Me"]
        [:div (str @v*)]
        [:div {:style {:padding "5px"
                       :color :white
                       :background-color :blue
                       :width @data*}}
         (str (int @data*))]]])))

(defn ^:export -main
  []
  (go
    (let [conn (<! (net/connect SERVER-URL))]
      (net/event conn 'hello-event)
      (listen-to-counter conn)
      (go-loop []
        (println "rpc-map: " @(:rpc-map* conn))
        (<! (timeout 100))
        (recur))
      (reagent/render [test-page conn] (.getElementById js/document "app")))))

(-main)
