(ns relais-ng.thingspeak
  (:require [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [relais-ng.settings :as settings]
            [relais-ng.utils :as u]
            [relais-ng.raspi-io :as rio]))

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
  "dumbly translate measurement for thingspeak"
  [self measurement]
  {"field1" (:temperature measurement) "field2" (:humidity measurement)})

(defn from-relais-info
  "translate relais state to thing speak fields - use index of sorted relais names - u have to enter them in channel settings"
  [self field-offset]

  (let [info (rio/relais-info (:rio self))
        relais-indices (into {} (map-indexed (fn [idx item] {item (+ idx field-offset)}) (sort (map :pinName info))))
        _ (log/info "relais field mappings for thingspeak" relais-indices)
        ]
    (into {} (map #(vector
                    (str "field" (get relais-indices (:pinName %)))
                    (if (.equalsIgnoreCase "HIGH" (:pinState %)) 1 0)
                    ) info))))

(defn write
  "write to thingspeak"
  [self measurement]
  (let [fields-from-measurement (dumb-translate self measurement)
        fields-from-relais (from-relais-info self 3)
        merged (merge fields-from-measurement fields-from-relais)
        with-api-key (assoc merged :api_key (:write-api-key self))
        _ (log/debug "send to thingspeak:" with-api-key)]
    (client/post "https://api.thingspeak.com/update"
                 {:form-params with-api-key})))

(defn create
  []
  (map->ThingSpeak {}))