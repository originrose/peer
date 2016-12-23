(ns think.peer.server
  (:require [clojure.core.async :refer [go <! >!] :as async]
            [org.httpkit.server :as http-kit]
            [ring.middleware.resource :refer [wrap-resource]]
            [bidi.ring :refer [make-handler]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [think.peer.api :as api]
            [think.peer.net :as net]))

(def DEFAULT-PORT 4242)
(defonce server* (atom nil))

(defn page
  [body]
  {:status 200
   :headers {}
   :body (html
          [:html
           [:head
            [:meta {:charset "utf-8"}]
            [:meta {:name "viewport"
                    :content "width=device-width, initial-scale=1"}]
            (include-css "css/style.css")]
           body])})

(defn home-page
  [request]
  (page
    [:body
     [:h1 "Home page"]
     [:div#app
      [:h3 "Clojurescript has not been compiled..."]]
     (include-js "js/think.peer.js")]))

(defn test-page
  [request]
  (page
   [:body
    [:div#app
     [:h3 "Clojurescript has not been compiled..."]]
    (include-js "js/test/think.peer.tests.js")]))

(defn start
  [source-ns-api & [port]]
  (if @server*
    @server*
    (let [port (or port DEFAULT-PORT)
          app (-> (make-handler ["/" {"" home-page
                                      "test" test-page
                                      "connect" (partial net/connect-client source-ns-api)}])
                  (wrap-resource "public"))]
      (println "=============================")
      (println "Starting server on port:" port)
      (reset! server* (http-kit/run-server app {:port port})))))

(defn stop
  []
  (@server*)
  (reset! server* nil))
