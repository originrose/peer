(ns test-api
  (:require [clojure.core.async :refer [chan go-loop >! close!]]))

(defn ^{:api/type :rpc}
  echo
  "A simple function that returns its input"
  [in]
  in)

(defn ^{:api/type :event}
  click
  "An event handler that prints a msg."
  [msg]
  (println "event: " msg))

(defn ^{:api/type :rpc}
  test-handler
  [req v a b]
  (+ v a b))

(defn ^{:api/type :rpc}
  bad-function
  "Trigger an error to test error handling."
  []
  (throw (Exception. "Test error")))

(defn ^{:api/type :subscription}
  counter
  "A simple function that writes to a channel continuously"
  []
  (let [ch (chan)]
    (go-loop [i 10]
             (>! ch i)
             (if (pos? i)
               (recur (dec i))
               (close! ch)))
    ch))
