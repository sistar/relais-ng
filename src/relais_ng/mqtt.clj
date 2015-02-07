(ns relais-ng.mqtt
  (:require [clojurewerkz.machine-head.client :as mh]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [relais-ng.utils :as u]
            [com.stuartsierra.component :as component]
            [overtone.at-at :as at]
            [relais-ng.settings :as settings])
  (:import (org.eclipse.paho.client.mqttv3 IMqttDeliveryToken)))

(def ^:const topic "/bedroom")

(defn start-consumer [self]
  (mh/subscribe @(:mqtt-con self)
                [topic]
                (fn [^String rcv-topic metadata ^bytes payload]
                  (let [s (String. payload "UTF-8")
                        json_t (json/read-str s :key-fn keyword)
                        rcvd-data {:temperature (/  (:temperature json_t) 10.0)
                                   :humidity    (/  (:humidity json_t) 10.0)
                                   :epoch       (:epoch json_t)}]
                    (log/info (format "topic:%s data:%s received-string: %s meta; %s" rcv-topic rcvd-data s metadata))))
                {:on-connection-lost   (fn [^Throwable reason]
                                         (.printStackTrace reason)
                                         (log/error "LOST" reason))
                 :on-delivery-complete (fn [^IMqttDeliveryToken token] (log/info "COMPLETE" token))}
                )
  (log/info "subscribed" (:mqtt-con self)))

(defn send-epoch [self]
  (if-not
    (mh/connected? @(:mqtt-con self))
    (let [new-id (mh/generate-id)
          _ (reset! (:mqtt-id self) new-id)
          new-con (mh/connect (:mqtt-connect-string self) @(:mqtt-id self)) ]
      (reset! (:mqtt-con self) new-con)))
  (mh/publish @(:mqtt-con self) "/epoch" (str (long (/ (System/currentTimeMillis) 1000)))))

(defrecord MqttComponent []

  component/Lifecycle
  (start [cm]
    (log/info "starting Mqtt")
    (let [id (mh/generate-id)
          connect-string (str "tcp://" (settings/get-setting (:settings cm) :mqtt-broker-host) ":" (settings/get-setting (:settings cm) :mqtt-broker-port))
          executor (at/mk-pool)
          conn (mh/connect connect-string id)
          new-self (assoc cm :mqtt-connect-string  connect-string
                             :mqtt-con (atom conn)
                             :mqtt-id (atom id))]
      (start-consumer new-self)
      (assoc new-self :schedule (at/every u/minute
                                          #(try

                                            (send-epoch new-self)
                                            (catch Throwable e (log/error e e)))
                                          executor))))

  (stop [cm]
    (log/info "stopping Mqtt")
    (mh/disconnect (:mqtt-con cm))))

(defn create-mqtt-component [deps]
  (map->MqttComponent {}))
