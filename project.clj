(defproject thinktopic/think.peer "0.1.0-no-fressian-SNAPSHOT"
  :description "P2P - Clojure(Script) style"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [http-kit "2.1.18"]
                 [ring/ring-defaults "0.2.1"]
                 [compojure "1.5.1"]
                 [net.mikera/vectorz-clj "0.45.0"]
                 [thinktopic/matrix.fressian "0.3.0-SNAPSHOT"]

                 [jarohen/chord "0.7.0" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/clojurescript "1.9.229"]
                 [org.clojure/core.async "0.2.391"]
                 [thinktopic/aljabr "0.1.1"]]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.8"]
            [s3-wagon-private "1.2.0"]]

  :hooks [leiningen.cljsbuild]

  :profiles {:dev {;:resource-paths ["dummy-data"]
                   :dependencies [[reagent "0.6.0"]]}}

  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj"]

  :figwheel {:builds-to-start ["dev" "test"]}

  :cljsbuild {:builds
              [{:id "dev"
                :figwheel true
                :jar true
                :source-paths ["src/cljs" "test/cljs"]
                :compiler {:main "think.peer.net-client-test"
                           :asset-path "js/out"
                           :output-to "resources/public/js/think.peer.js"
                           :output-dir "resources/public/js/out"
                           :optimizations :none
                           :pretty-print  true}}

               {:id "test"
                :figwheel true
                :source-paths ["src/cljs" "test/cljs"]
                :compiler {:main "think.peer.net-client-test"
                           :output-to "resources/public/js/test/think.peer.tests.js"
                           :output-dir "resources/public/js/test/out"
                           :optimizations :none
                           :pretty-print  true}}
               ]}



  :repositories  {"snapshots"  {:url "s3p://thinktopic.jars/snapshots/"
                                :passphrase :env
                                :username :env
                                :releases false}
                  "releases"  {:url "s3p://thinktopic.jars/releases/"
                               :passphrase :env
                               :username :env
                               :snapshots false
                               :sign-releases false}}
)
