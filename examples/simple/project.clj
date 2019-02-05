(defproject simple "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [peer "0.4.0-SNAPSHOT"]
                 [reagent "0.6.0"]]
  :main ^:skip-aot simple.server
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :plugins [[lein-figwheel "0.5.8"]]
  :figwheel {:builds-to-start ["dev"]}
  :source-paths ["src/clj"]
  :clean-targets ^{:protect false} [:target-path "figwheel_server.log" "resources/public/js"]
  :cljsbuild {:builds
              [{:id "dev"
                :figwheel true
                :source-paths ["src/cljs"]
                :compiler {:main "simple.client"
                           :asset-path "js/out"
                           :output-to "resources/public/js/peer.js"
                           :output-dir "resources/public/js/out"
                           :optimizations :none
                           :pretty-print  true}}]})
