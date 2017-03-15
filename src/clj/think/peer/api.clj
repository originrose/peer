(ns think.peer.api
  (:require [clojure.repl :as repl]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as string]
            clojure.test))

(defn namespaces
  "List all namespaces."
  [req]
  (sort (map ns-name (all-ns))))

(defn ns-vars
  "Get all of the vars from a namespace."
  [ns-sym]
  (vals (ns-publics ns-sym)))

(defn ns-fns
  "Get all of the function vars in a namespace."
  [ns-sym]
  (filter #(clojure.test/function? (var-get %)) (ns-vars ns-sym)))

(defn var-info
  "Get the source and metadata for a var in a namespace."
  [ns-val the-var]
  (let [src (repl/source-fn (symbol (str ns-val) (str the-var)))]
    (println "got source: " src)
    src))

(defn- with-partial-args
  "Takes a function f and variadic args. Creates a new partial function closing
  over those args aswell as adding a partial-args value into the functions meta data.
  Also transfers the input functions meta-data to the new partial version with the addition
  over partial-args."
  [f & args]
  (let [meta-data (meta f)]
    (with-meta
      (apply partial f args)
      (assoc meta-data :partial-args args))))

(defn wrap-args
  "Takes a map of functions and a coll of args to close over for each function."
  [fns args]
  (reduce
    (fn [fns [k f]]
      (assoc fns k (with-partial-args f args)))
    {}
    fns))

(defn ns-api
  "Return an API spec given a namespace."
  [ns-sym & {:keys [partial-args]}]
  (let [fns (ns-fns ns-sym)
        {:keys [event rpc subscription]} (group-by #(or (:api/type (meta %)) :rpc) fns)
        fns->map #(into {} (map (fn [f] [(:name (meta f)) f]) %))
        fns-map  (fns->map rpc)]
    {:rpc          (if partial-args
                     (apply wrap-args fns-map partial-args)
                     fns-map)
     :event        (fns->map event)
     :subscription (fns->map subscription)}))

(defn html-function-doc
  [f-var]
  (let [{:keys [arglists doc line file name ns]} (meta f-var)
        src (repl/source-fn (symbol (str ns) (str name)))]
    [:div.function
     [:h3 (apply str (for [args arglists]
                       (format "(%s %s)\n" name args)))]
     [:div.file-info
      [:div.doc doc]
      [:pre.source src]
      ;[:span.line-info (format "%s:%s" file line)]
      ]]))

(defn html-handler-docs
  [{:keys [rpc event subscription]}]
  [:div.api-handler-docs
   [:h1 "API Documentation"]
   [:div.events
    [:h2 "Event Handlers"]
    [:ul
     (map-indexed (fn [i [k v]]
                    ^{:key (str "event-" i)}(html-function-doc v))
                  event)]]
   [:div.functions
    [:h2 "RPC Functions"]
    [:ul
     (map-indexed (fn [i [k v]]
                    ^{:key (str "rpc-" i)}(html-function-doc v))
                  rpc)]]
   [:div.subscriptions
    [:h2 "Subscriptions"]
    [:ul
     (map-indexed (fn [i [k v]]
                    ^{:key (str "subscription-" i)}(html-function-doc v))
                  subscription)]]])


(defn api-handler
  [handlers req]
  (let [parsed (re-find #"api/v([0-9]+)/(.*)/(.*)" (:uri req))]
    (if (some nil? parsed)
      [:div.error "Invalid request:" (:uri req)]
      (let [[_ version msg-type fn-name] parsed
            handler (get-in handlers [(keyword msg-type) (symbol fn-name)])]
        (try (handler (:params req))
             (catch Exception e
               [:div "Exception: " (str e)]))))))
