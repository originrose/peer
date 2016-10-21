(ns think.peer.repl
  (:require [clojure.repl :as repl]))

;; Some experimental reflection capabilities to power code browser, editor,
;; repl, experiment platform.

(defn namespaces
  [req]
  (sort (map ns-name (all-ns))))

(defn ns-vars
  [{:keys [args]}]
  (keys (ns-publics (:ns args))))

(defn var-info
  [{:keys [args]}]
  (let [src (repl/source-fn (symbol (str (:ns args)) (str (:var args))))]
    (println "got source: " src)
    src))

