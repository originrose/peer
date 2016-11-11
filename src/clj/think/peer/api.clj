(ns think.peer.api
  (:require [clojure.repl :as repl]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as string]
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
  [ns-val the-var]
  (let [src (repl/source-fn (symbol (str ns-val) (str the-var)))]
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

(defn function-doc
  [f-var]
  (let [{:keys [arglists doc line file name ns]} (meta f-var)
        src (repl/source-fn (symbol (str ns) (str name)))]
    [:div.function
     [:h3 name]
     [:div.file-info
      [:span (format "%s:%s" file line)]
      [:div.doc doc]
      [:pre.source src]]]))

(defn handler-docs
  [{:keys [rpc event subscription]}]
  [:div.api-handlers
   [:h1 "API Documentation"]
   [:div.events
    [:h2 "Event Handlers"]
    [:ul
     (map-indexed (fn [i [k v]]
                    ^{:key (str "event-" i)}(function-doc v))
                  rpc)]]
   [:div.functions
    [:h2 "Functions"]
    [:ul
     (map-indexed (fn [i [k v]]
                    ^{:key (str "rpc-" i)}(function-doc v))
                  rpc)]]
   [:div.subscriptions
    [:h2 "Subscriptions"]
    [:ul
     (map-indexed (fn [i [k v]]
                    ^{:key (str "subscription-" i)}(function-doc v))
                  subscription)]]])

(defn api-handler
  [handlers req]
  (let [parsed (re-find #"api/v([0-9]+)/(.*)/(.*)" (:uri req))]
    (if (some nil? parsed)
      [:div.error "Invalid request:" (:uri req)]
      (let [[_ version msg-type fn-name] parsed]
        (try ((get-in handlers [(keyword msg-type) (symbol fn-name)]))
             (catch Exception e
               [:div "Exception: " (str e)]))))))

