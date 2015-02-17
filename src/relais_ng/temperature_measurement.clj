(ns relais-ng.temperature-measurement
  (:require [com.stuartsierra.component :as component]
            [clojure.java.shell :as sh]
            [schema.core :as s]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [relais-ng.settings :as settings]
            [me.raynes.fs :as fs]
            [overtone.at-at :as at]
            [relais-ng.utils :as u]
            [relais-ng.thingspeak :as ts])
  (:import (java.io File)))
(s/defschema Measurement {:temperature Double :humidity Double})
(s/defschema GenericMeasurement [{:name String :value String}])
(defn receive-measurement
  "measurement via POST"
  [tm id body]
  (log/info "MM" id body)
  )
(defn measure
  [self]
  (if (some? (:measure-script self))
    (let [result (sh/sh "python" (:measure-script self))
          out (:out result)
          _ (log/debug "out" out)
          parsed (json/read-str out :key-fn keyword)]
      (if (< (:humidity parsed) 101)
        (do
          (log/info "parsed acceptable data.." parsed)
          (ts/write (:thing-speak self) parsed "livingroom")
          (dosync (ref-set (:measurement self) parsed)))))
    (do (log/error "no measurement script - self:" self)
        nil)))

(defrecord TempMeasure [settings thing-speak]
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
          measurement (ref {})
          new-self (assoc component
                     :executor executor
                     :measure-script sc
                     :measurement measurement)
          new-self-2 (assoc new-self :schedule (at/every u/minute
                                                         #(try (measure new-self)
                                                               (catch Throwable e (log/error e e)))
                                                         executor))]
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


