(ns relais-ng.temperature-measurement
  (:require [com.stuartsierra.component :as component]
            [clojure.java.shell :as sh]
            [schema.core :as s]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [relais-ng.settings :as settings]
            [me.raynes.fs :as fs]))
(s/defschema Measurement {:temperature Double :humidity Double})

(defrecord TempMeasure [settings]
  component/Lifecycle
  (start [component]
    (println ";; Starting Temperature Measurement Unit")
    (let [sc (settings/get-setting settings :measure-script)
          _ (log/debug "checking" sc (type sc))
          script-found (fs/file? sc)
          _ (if-not script-found
              (log/error "could not find python script for DHT-XX interaction: " sc (fs/file? "/var/opt/relais-ng/dht-22-sample-mock.py") (fs/file? sc) ))
          ]
      (-> (assoc component :measure-script sc)
          )))
  (stop [component]
    (println ";; Stopping Temperature Measurement Unit")
    ))

(defn create-temp-measurement
  [deps]
  (map->TempMeasure {}))


(defn get-Measurement
  [self] :- Measurement
  (let [
        value (:measure-script self)
        result (sh/sh "python" value)
        out (:out result)
        _ (log/trace "out" out)
        parsed (json/read-str out :key-fn keyword)
        _ (println "parsed.." parsed)]
    parsed))


