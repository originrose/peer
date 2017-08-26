(ns util
  (:require [clojure.test :refer :all]
            [cognitect.transit :as transit]
            [think.peer.api :as api]
            [think.peer.net :as net]
            [test-api])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn with-peer-server
  [test-fn]
  (let [server (net/listen :listener (net/peer-listener
                                       {:api (api/ns-api 'test-api)})
                           :port 4242)]
    (test-fn)
    (net/close server)))

(defn edn->transit
  [edn]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer edn)
    (.toString out)))

(defn transit->edn
  [transit_]
  (-> (.getBytes transit_ "UTF-8")
      (ByteArrayInputStream.)
      (transit/reader :json)
      (transit/read)))

