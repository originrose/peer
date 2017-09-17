(ns think.peer.util
  (:require [clojure.test :refer :all]
            [cognitect.transit :as transit]
            [think.peer.api :as api]
            [think.peer.net :as net]
            [test-api])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn edn->transit
  [edn]
  (let [out (ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer edn)
    (.toString out)))

(defn transit->edn
  [transit_]
  (-> (.getBytes transit_ "UTF-8")
      (ByteArrayInputStream.)
      (transit/reader :json)
      (transit/read)))

(defn transit-bytes->edn
  [byte-stream]
  (transit/read (transit/reader byte-stream :json)))
