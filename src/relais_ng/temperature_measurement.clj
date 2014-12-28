(ns relais-ng.temperature-measurement
  (:require [com.stuartsierra.component :as component]
            [clojure.java.shell :as sh]
            [schema.core :as s]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))
(s/defschema Measurement {:temperature Double :humidity Double})

(defrecord TempMeasure [native?]
  component/Lifecycle
  (start [component]
    (println ";; Starting Temperature Measurement Unit:" native?)
    (let [measure-script (if native?
                           "dht-22-sample.py"
                           "dht-22-sample-mock.py")]
      (-> (assoc component :measure-script measure-script)
          )))
  (stop [component]
    (println ";; Stopping Temperature Measurement Unit")
    ))

(defn create-temp-measurement
  [n]
  (map->TempMeasure {:native? n}))


(defn get-Measurement
  [self] :- Measurement
  (let [
        value (:measure-script self)
        result (sh/sh "python" value)
        out (:out result)
        parsed (json/read-str out :key-fn keyword)]
    parsed
    ))


