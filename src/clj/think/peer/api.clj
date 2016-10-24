(ns think.peer.api
  (:require [clojure.repl :as repl]
            clojure.test))

(defn namespaces
  [req]
  (sort (map ns-name (all-ns))))

(defn ns-vars
  [ns-sym]
  (vals (ns-publics ns-sym)))

(defn ns-fns
  [ns-sym]
  (filter #(clojure.test/function? (var-get %)) (ns-vars ns-sym)))

(defn var-info
  [{:keys [args]}]
  (let [src (repl/source-fn (symbol (str (:ns args)) (str (:var args))))]
    (println "got source: " src)
    src))

(defn ns-api
  "Return an API spec given a namespace."
  [ns-sym]
  (let [fns (ns-fns ns-sym)
        {:keys [event rpc subscription]} (group-by #(or (:api/type (meta %)) :rpc) fns)
        fns->map #(into {} (map (fn [f] [(:name (meta f)) f]) %))]
    {:rpc (fns->map rpc)
     :event (fns->map event)
     :subscription (fns->map subscription)}))

