(defproject simple "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://thinktopic.com"
  :license {:name "Copyright © 2016 ThinkTopic, LLC"
            :url "http://thinktopic.com"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.293"]
                 [thinktopic/think.peer "0.2.0-SNAPSHOT"]
                 [reagent "0.6.0"]]
  :main ^:skip-aot server
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}

  :plugins [[lein-figwheel "0.5.8"]]
  :figwheel {:builds-to-start ["dev"]}

  :source-paths ["src/clj"]

  :cljsbuild {:builds
              [{:id "dev"
                :figwheel true
                :source-paths ["src/cljs"]
                :compiler {:main "client"
                           :asset-path "js/out"
                           :output-to "resources/public/js/think.peer.js"
                           :output-dir "resources/public/js/out"
                           :optimizations :none
                           :pretty-print  true}}]})
