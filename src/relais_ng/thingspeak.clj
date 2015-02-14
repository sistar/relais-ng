(ns relais-ng.thingspeak
  (:require [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [relais-ng.settings :as settings]
            [relais-ng.raspi-io :as rio]))

(defrecord ThingSpeak [settings]
  component/Lifecycle

  (start [component]
    (log/info ";; Starting Thing Speak Unit")
    (assoc component :write-api-keys (settings/get-setting settings :write-api-keys)))

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
  [self measurement room]
  (let [fields-from-measurement (dumb-translate self measurement)
        fields-from-relais (from-relais-info self 3)
        merged (merge fields-from-measurement fields-from-relais)
        api-key (get (:write-api-keys self) room)
        with-api-key (assoc merged :api_key api-key)
        _ (log/debug "send to thingspeak:" with-api-key)]
    (client/post "https://api.thingspeak.com/update"
                 {:form-params with-api-key})))

(defn create
  []
  (map->ThingSpeak {}))