(ns think.peer.test-api
  (:require
    [clojure.spec :as s]
    [clojure.core.async :refer [go go-loop <! >! chan alts! timeout sliding-buffer]]
    [taoensso.timbre :as log]
    [config.core :refer [env]]
    [think.peer.net :as net]))

(defn times-two
  [v]
  (* 2 v))

(defn hello-world
  []
  "hello world")

(defn hello-world
  "A simple function"
  []
  "Hello World!")

(defn multiply-ints
  "A function with args."
  [a b]
  (* a b))

(s/fdef multiply-ints
  :args (s/cat :start int? :end int?)
  :ret int?
  :fn #(= (:ret %) (* (-> % :args :a)
                      (-> % :args :b))))
(defn ^{:api/type :event}
  hello-event
  "A simple function"
  []
  (println "Hello event!!!"))

(defn ^{:api/type :subscription}
  second-counter
  "A counter that sends an event with an incrementing value every second.
  {:n <value>}
  "
  []
  (let [publication-chan (chan (sliding-buffer 1))]
    (go-loop [n 0]
      (<! (timeout 1000))
      (when (>! publication-chan n)
        (recur (inc n))))
    publication-chan))

(defn ^{:api/type :subscription}
  rand-value-eventer
  "A sample publisher that sends a rand value within the given range every
  period-ms milliseconds.
  "
  [min-val max-val period-ms]
  (let [publication-chan (chan (sliding-buffer 1))]
    (go-loop []
      (<! (timeout period-ms))
      (when (>! publication-chan (+ min-val (* (rand) (- max-val min-val))))
        (recur)))
    publication-chan))
