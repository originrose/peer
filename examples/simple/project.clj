(defproject simple "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [thinktopic/think.peer "0.2.0-SNAPSHOT"]]
  :main ^:skip-aot simple.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}

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
