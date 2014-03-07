(defproject clojure-irc-bot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.ini4j/ini4j "0.5.2"]
                 [clj-http "0.7.9"]
                 [org.clojure/data.json "0.2.4"]
                 [org.clojure/data.csv "0.1.2"]]
  :main ^:skip-aot clojure-irc-bot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
