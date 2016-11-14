(ns think.peer.server
  (:require [clojure.core.async :refer [go <! >!] :as async]
            [org.httpkit.server :as http-kit]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [bidi.bidi :refer [match-route]]
            [bidi.ring :refer [make-handler resources-maybe]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [think.peer.net :as net]))

(def DEFAULT-PORT 4242)
(defonce server* (atom nil))

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
     (include-js "js/think.peer.js")]))

(defn test-page
  []
  (page
   [:body
    [:div#app
     [:h3 "Clojurescript has not been compiled..."]]
    (include-js "js/test/think.peer.tests.js")]))


(defn make-app
  []
  (let [routes ["" [["test" test-page]
                    ["connect" net/connect-client]
                    ["/" {["" (resources-maybe {:prefix "public"})] home-page} ]
                    [true "Not Found"]]]
        router (make-handler routes)]
    #(match-route routes %)))

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
