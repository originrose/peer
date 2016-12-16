(defproject thinktopic/think.peer "0.2.0-SNAPSHOT"
  :description "P2P - Clojure(Script) style"
  :dependencies [[org.clojure/clojure "1.9.0-alpha13"]
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

  :clean-targets ^{:protect false} ["resources/public/js/test/"]

  :figwheel {:builds-to-start ["dev" "test"]}

  :repositories  {"snapshots"  {:url "s3p://thinktopic.jars/snapshots/"
                                :passphrase :env
                                :username :env
                                :releases false}
                  "releases"  {:url "s3p://thinktopic.jars/releases/"
                               :passphrase :env
                               :username :env
                               :snapshots false
                               :sign-releases false}})
