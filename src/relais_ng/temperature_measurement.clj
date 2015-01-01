(ns relais-ng.temperature-measurement
  (:require [com.stuartsierra.component :as component]
            [clojure.java.shell :as sh]
            [schema.core :as s]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [relais-ng.settings :as settings]
            [me.raynes.fs :as fs])
  (:import (java.io File)))
(s/defschema Measurement {:temperature Double :humidity Double})

(defrecord TempMeasure [settings]
  component/Lifecycle
  (start [component]
    (println ";; Starting Temperature Measurement Unit")
    (let [sc (settings/get-setting settings :measure-script)
          _ (log/debug "checking" sc (type sc))
          scf (File. sc)
          script-found (fs/file? scf)
          _ (if-not script-found
              (log/error "could not find python script for DHT-XX interaction: " scf  (fs/file? scf)))
          ]
      (assoc component :measure-script sc)
      ))
  (stop [component]
    (println ";; Stopping Temperature Measurement Unit")
    ))

(defn create-temp-measurement
  [deps]
  (map->TempMeasure {}))


(defn get-Measurement
  [self] :- Measurement
  (if (some? (:measure-script self))
    (let [result (sh/sh "python" (:measure-script self))
          out (:out result)
          _ (log/trace "out" out)
          parsed (json/read-str out :key-fn keyword)
          _ (println "parsed.." parsed)]
      parsed)
    nil))


