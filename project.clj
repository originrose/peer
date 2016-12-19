(defproject thinktopic/think.peer "0.2.0"
  :description "P2P - Clojure(Script) style"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.taoensso/timbre "4.7.4"]
                 [http-kit "2.1.18"]
                 [ring/ring-defaults "0.2.1"]
                 [bidi "2.0.13"]
                 [jarohen/chord "0.7.0" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.async "0.2.395"]]

  :plugins [[lein-figwheel "0.5.8"]
            [s3-wagon-private "1.2.0"]]

  :profiles {:dev {:dependencies [[reagent "0.6.0"]]}}

  :main think.peer.net-server-test

  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj"]

  :figwheel {:builds-to-start ["dev" "test"]}

  :cljsbuild {:builds
              [{:id "dev"
                :figwheel true
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
                           :asset-path "js/test/out"
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
