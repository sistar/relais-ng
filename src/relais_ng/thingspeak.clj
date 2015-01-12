(ns relais-ng.thingspeak
  (:require [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [relais-ng.settings :as settings]
            [relais-ng.utils :as u]))

(defrecord ThingSpeak [settings]
  component/Lifecycle

  (start [component]
    (log/info ";; Starting Thing Speak Unit")
    (let [write-api-key (settings/get-setting settings :write-api-key)
          ]
      (assoc component :write-api-key write-api-key)))

  (stop [component]
    (println ";; Stopping Thing Speak Unit")))

(defn dumb-translate
  "dumbly translate measurement fot thingspeak"
  [self measurement]
  {"field1" (:temperature measurement) "field2" (:humidity measurement)})

(defn write
  "write to thingspeak"
  [self measurement]
  (let [with-api-key (assoc (dumb-translate self measurement) :api_key (:write-api-key self))]
    (client/post "https://api.thingspeak.com/update"
                 {:form-params with-api-key})))

(defn create
  []
  (map->ThingSpeak {}))