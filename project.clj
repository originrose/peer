(defproject thinktopic/think.peer "0.2.1"
  :description "P2P - Clojure(Script) style"
  :dependencies [[org.clojure/clojure "1.9.0-alpha13"]
                 [com.taoensso/timbre "4.7.4"]
                 [http-kit "2.1.18"]
                 [ring/ring-defaults "0.2.1"]
                 [bidi "2.0.13"]
                 [jarohen/chord "0.7.0" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.async "0.2.395"]]

  :plugins [[s3-wagon-private "1.3.0"]]

  :source-paths ["src/clj" "src/cljs"]

  :profiles {:test {:dependencies [[stylefruits/gniazdo "1.0.0"]
                                   [com.cognitect/transit-clj "0.8.297"]]}}

  :clean-targets ^{:protect false} ["resources/public/js/test/"]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "" "--no-sign"] ; disable signing
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :repositories  {"snapshots"  {:url "s3p://thinktopic.jars/snapshots/"
                                :no-auth true
                                :releases false
                                :sign-releases false}
                  "releases"  {:url "s3p://thinktopic.jars/releases/"
                               :no-auth true
                               :snapshots false
                               :sign-releases false}})
