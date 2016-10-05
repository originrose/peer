(ns think.peer.server
  (:require [clojure.core.async :refer [go <! >!] :as async]
            [clojure.core.matrix :as mat]
            [org.httpkit.server :as http-kit]
            [compojure.core :refer [routes GET PUT POST]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [think.peer.net :as net]
            [think.peer.nrepl :as nrepl]))

(def DEFAULT-PORT 4242)
(defonce server* (atom nil))

(mat/set-current-implementation :vectorz)

(defn page
  [body]
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     (include-css "css/style.css")]
    body]))

(defn home-page []
  (page
    [:body
     [:h1 "Home page"]
     [:div#app
      [:h3 "Clojurescript has not been compiled..."]]
     (include-js "js/externs/canvasjs.min.js")
     (include-js "js/think.peer.js")
     ;(include-js "js/out/goog/base.js")
     ;[:script "goog.require('think.peer.core');"]
     ]))

(defn test-page
  []
  (page
    [:body
     [:div#app
      [:h3 "Clojurescript has not been compiled..."]]
     (include-js "js/externs/canvasjs.min.js")
     ;(include-js "js/test/out/goog/base.js")
     (include-js "js/test/think.peer.tests.js")
     ;[:script "goog.require('think.peer.core');"]
     ]))

(defn make-app
  []
  (let [routes (routes
                 (GET "/" [] (home-page))
                 (GET "/test" [] (test-page))
                 (GET "/connect" req (net/connect-client req))
                 ;(GET "/nrepl" req (nrepl/connect req))
                 (resources "/")
                 (not-found "Not Found"))]
    (wrap-defaults routes site-defaults)))

(def app (make-app))

(defn start
  [& [port]]
  (let [port (or port DEFAULT-PORT)]
    (if @server*
      @server*
      (reset! server* (http-kit/run-server #'app {:port port})))))

(defn stop
  []
  (@server*)
  (reset! server* nil))

