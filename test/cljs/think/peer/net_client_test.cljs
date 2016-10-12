 (ns think.peer.net-client-test)
;;   (:require-macros [cljs.core.async.macros :refer [go go-loop]])
;;   (:require [goog.dom :as dom]
;;             [goog.events :as events]
;;             [reagent.core :as reagent]
;;             [cljs.core.async :refer [put! chan <! >! timeout close!]]
;;             [think.peer.net :as net]))

;; (enable-console-print!)

;; (def SERVER-URL "ws://localhost:4242/connect")

;; (defn page
;;   []
;;   (let [data* (reagent/atom false)]
;;     (go
;;       (let [v (<! (net/request :foo))]
;;         (reset! data* v)))
;;     (fn []
;;       (if @data*
;;         [:div @data*]
;;         [:div "no data yet..."]))))

;; (defn ^:export -main
;;   []
;;   (go
;;     (println "connecting to server")
;;     (<! (net/connect-to-server SERVER-URL))
;;     (println "rendering test component")
;;     (let [elem (.getElementById js/document "app")]
;;       (reagent/render [page] elem))
;;     (println "ready")))

;; (-main)
