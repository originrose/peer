(defproject peer "0.1.0-SNAPSHOT"
  :description "P2P - ClojureScript style"
  :min-lein-version "2.0.0"
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :dependencies [[org.clojure/clojure             "1.5.1"]
                 [org.clojure/clojurescript       "0.0-1913"]
                 [org.clojure/core.async          "0.1.242.0-44b1e3-alpha"]
                 [prismatic/dommy                 "0.1.1"]
                 [org.clojure/data.json           "0.2.1"]
                 [crate                           "0.2.4"]
                 [com.cemerick/clojurescript.test "0.0.4"]]


  :test-paths ["src/test/clojure"]

  :hooks [leiningen.cljsbuild]

  :plugins [[lein-cljsbuild "0.3.4"]]

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
