(defproject thinktopic/peer "0.1.0-SNAPSHOT"
  :description "P2P - Clojure(Script) style"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [http-kit "2.1.18"]
                 [jarohen/chord "0.7.0" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/clojurescript "1.9.229"]
                 [org.clojure/core.async "0.2.391"]
                 ;[org.clojure/data.json "0.2.6"]
                 [thinktopic/aljabr "0.1.1"]
                 [net.mikera/vectorz-clj "0.45.0"]
                 ]

  :plugins [[lein-figwheel "0.5.8"]]

  :source-paths ["src/clj"]
  :test-paths ["test/clj"]

  :cljsbuild {:builds [{:id "dev"
                        :figwheel true
                        :source-paths ["src/cljs"]
                        :compiler {:output-to "public/js/think.peer.js"
                                   :output-dir "public/js/out"
                                   :optimizations :none
                                   :pretty-print  true}}

                       {:id "test"
                        :source-paths ["test/cljs" "src/cljs"]
                        :compiler {:output-to "public/js/test/think.peer.tests.js"
                                   :output-dir "public/js/test/out"
                                   :optimizations :none
                                   :pretty-print  true}}
                       ]
})
