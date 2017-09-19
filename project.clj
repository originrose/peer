(defproject thinktopic/think.peer "0.3.1-SNAPSHOT"
  :description "P2P - Clojure(Script) style"
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.async "0.3.442"]
                 [io.pedestal/pedestal.interceptor "0.5.2"]
                 [com.taoensso/timbre "4.8.0"]
                 [http-kit "2.2.0"]
                 [jarohen/chord "0.8.1" :exclusions [org.clojure/tools.reader]]
                 [ring/ring-defaults "0.2.3"]
                 [bidi "2.0.16"]
                 [stylefruits/gniazdo "1.0.0"]]

  :source-paths ["src/clj" "src/cljs"]

  :plugins [[s3-wagon-private "1.3.0"]]

  :profiles {:test {:dependencies [[stylefruits/gniazdo "1.0.0"]
                                   [com.cognitect/transit-clj "0.8.300"]]}}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "" "--no-sign"] ; disable signing
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :clean-targets ^{:protect false} [:target-path "figwheel_server.log" "resources/public/js/test/"]

  :repositories  {"snapshots" {:url "s3p://thinktopic.jars/snapshots/"
                               :no-auth true
                               :releases false
                               :sign-releases false}
                  "releases" {:url "s3p://thinktopic.jars/releases/"
                              :no-auth true
                              :snapshots false
                              :sign-releases false}})
