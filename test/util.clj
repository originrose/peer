(ns util
  (:require [clojure.test :refer :all]
            [cognitect.transit :as transit]
            [think.peer.server :as server]
            [think.peer.api :as api]
            [test-api])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn with-peer-server
  [test-fn]
  (server/start (api/ns-api 'test-api) 4242)
  (test-fn)
  (server/stop))

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
