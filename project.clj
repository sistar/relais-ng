(defproject relais-ng "0.1.0-SNAPSHOT"
            :description "FIXME: write description"
            :dependencies [
                           ;; clojure-core
                           [org.clojure/clojure "1.6.0"]
                           [org.clojure/tools.cli "0.3.1"]
                           [org.clojure/data.zip "0.1.1"]
                           [org.clojure/tools.namespace "0.2.4"]
                           [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                           [org.clojure/tools.logging "0.3.1"]
                           [org.clojure/data.json "0.2.4"]
                           [overtone/at-at "1.2.0"]

                           ;; misc external
                           [me.raynes/fs "1.4.5"]
                           [clojure-lanterna "0.9.4"]

                           ;; blue haired stuart's libraries
                           [com.stuartsierra/dependency "0.1.1"]
                           [com.stuartsierra/component "0.2.2"]


                           [metosin/ring-swagger "0.15.0"]
                           [metosin/compojure-api "0.16.4"]
                           [metosin/ring-http-response "0.5.2"]
                           [metosin/ring-swagger-ui "2.0.17"]
                           [http-kit "2.1.18"]
                           [clj-http "1.0.1"]
                           [clojurewerkz/machine_head "1.0.0-beta8"]

                           ;; raspi io
                           [com.pi4j/pi4j-core "0.0.5"]

                           ;; omg logging
                           [org.slf4j/jcl-over-slf4j "1.7.10"]
                           [ch.qos.logback/logback-classic "1.1.2"]]
            :exclusions [commons-logging/commons-logging]
            :main relais-ng.main
            :uberjar-name "server.jar"
            :profiles {:uberjar {:resource-paths ["swagger-ui"]}
                       :a {:aot :all}
                       :dev     {:dependencies [[javax.servlet/servlet-api "2.5"]]
                                 :plugins      [[lein-ring "0.8.13"]]}})
