(defproject thinktopic/peer "0.1.0-SNAPSHOT"
  :description "P2P - Clojure(Script) style"
  :dependencies [[org.clojure/clojure "1.9.0-alpha9"]
                 [http-kit "2.1.18"]
                 [jarohen/chord "0.7.0" :exclusions [org.clojure/tools.reader]]
                 ;[com.stuartsierra/component "0.2.3"]

                 [org.clojure/clojurescript "1.9.89"]
                 [org.clojure/core.async "0.2.385"]
                 [prismatic/dommy                 "1.1.0"]
                 [org.clojure/data.json           "0.2.6"]
                 [crate                           "0.2.4"]

                 ]

  :hooks [leiningen.cljsbuild]
  :plugins [[lein-cljsbuild "1.1.3"]]

  :cljsbuild {:builds [{:id "whitespace"
                        :source-paths ["src"]
                        :notify-command ["growlnotify" "-m"]
                        :compiler {:output-to "public/js/app.js"
                                   :optimizations :whitespace
                                   :warnings      true
                                   :pretty-print  true}}

                       {:id "advanced"
                        :source-paths ["src"]
                        :notify-command ["growlnotify" "-m"]
                        :compiler {:output-to "public/js/app.js"
                                   :optimizations :advanced
                                   :warnings      true
                                   :pretty-print  false
                                   :externs ["resources/externs.js"]}}

                       ; {:id "test"
                       ;  :source-paths ["test" "src"]
                       ;  :notify-command ["growlnotify" "-m"]
                       ;  :compiler {:output-to "public/js/tests.js"
                       ;             :optimizations :whitespace
                       ;             :warnings      true
                       ;             :pretty-print  true}}
                                   ]

              ;:test-commands {"unit-tests" ["./orchard" "-test"]}
              })
