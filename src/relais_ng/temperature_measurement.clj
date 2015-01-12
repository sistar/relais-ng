(ns relais-ng.temperature-measurement
  (:require [com.stuartsierra.component :as component]
            [clojure.java.shell :as sh]
            [schema.core :as s]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [relais-ng.settings :as settings]
            [me.raynes.fs :as fs]
            [overtone.at-at :as at]
            [relais-ng.utils :as u])
  (:import (java.io File)))
(s/defschema Measurement {:temperature Double :humidity Double})

(defn measure
  [self]
  (if (some? (:measure-script self))
    (let [result (sh/sh "python" (:measure-script self))
          out (:out result)
          _ (log/debug "out" out)
          parsed (json/read-str out :key-fn keyword)
          _ (log/debug "parsed.." parsed)]
      (dosync (ref-set (:measurement self)  parsed)))
    (do (log/error "no measurement script - self:" self)
        nil)))

(defrecord TempMeasure [settings]
  component/Lifecycle
  (start [component]
    (log/info ";; Starting Temperature Measurement Unit")
    (let [sc (settings/get-setting settings :measure-script)
          _ (log/debug "checking" sc (type sc))
          scf (File. sc)
          script-found (fs/file? scf)
          _ (if-not script-found
              (log/error "could not find python script for DHT-XX interaction: " scf (fs/file? scf)))
          executor (at/mk-pool)
          do-measure (settings/get-setting settings :do-measure)
          measurement (ref {})
          new-self (assoc component
                     :executor executor
                     :measure-script sc
                     :measurement measurement)
          new-self-2 (assoc new-self :schedule (if do-measure
                                                 (at/every u/minute
                                                           #(try (measure new-self)
                                                                 (catch Throwable e (log/error e e)))
                                                           executor)
                                                 nil))

          ]
      new-self-2
      ))
  (stop [component]
    (println ";; Stopping Temperature Measurement Unit")
    ))

(defn create-temp-measurement
  [deps]
  (map->TempMeasure {}))


(defn get-Measurement
  [self] :- Measurement
  @(:measurement self))


