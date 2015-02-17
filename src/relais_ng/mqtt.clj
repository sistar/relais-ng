(ns relais-ng.mqtt
  (:require [clojurewerkz.machine-head.client :as mh]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [relais-ng.utils :as u]
            [com.stuartsierra.component :as component]
            [overtone.at-at :as at]
            [relais-ng.settings :as settings]
            [relais-ng.thingspeak :as ts])
  (:import (org.eclipse.paho.client.mqttv3 IMqttDeliveryToken)))


(defn validate-subscribed [self]
  (if @(:connection-lost self)
    (mh/subscribe @(:mqtt-con self) (:topics self)
                  (fn [^String rcv-topic metadata ^bytes payload]
                    (let [s (String. payload "UTF-8")
                          json_t (json/read-str s :key-fn keyword)
                          rcvd-data {:temperature (/ (:temperature json_t) 10.0)
                                     :humidity    (/ (:humidity json_t) 10.0)
                                     :epoch       (:epoch json_t)}]
                      (log/info (format "topic:%s data:%s received-string: %s meta; %s" rcv-topic rcvd-data s metadata))
                      ((ts/write (:thing-speak self) rcvd-data (clojure.string/replace rcv-topic #"^/" ""))))
                    {:on-connection-lost   (fn [^Throwable reason]
                                             (.printStackTrace reason)
                                             (log/error "LOST" reason)
                                             (reset! (:connection-lost self) true)
                                             )
                     :on-delivery-complete (fn [^IMqttDeliveryToken token] (log/trace "COMPLETE" token))}))
    (log/info "subscribed" (:mqtt-con self))))

(defn ensure-connected [self]
  (if-not (and
            (some? @(:mqtt-con self))
            (mh/connected? @(:mqtt-con self)))
    (do (reset! (:mqtt-id self) (mh/generate-id))
        (reset! (:mqtt-con self) (mh/connect (:mqtt-connect-string self) @(:mqtt-id self))))))

(defn publish-subscribe [self]
  (let [epoch (str (long (/ (System/currentTimeMillis) 1000)))]
    #(try
      (ensure-connected self)
      (mh/publish @(:mqtt-con self) "/epoch" epoch)
      (log/info "published /epoch " epoch)
      (validate-subscribed self)
      (catch Throwable e (log/error e e)))))

(defrecord MqttComponent []
  component/Lifecycle
  (start [cm]
    (log/info "starting Mqtt")
    (let [connect-string (str "tcp://" (settings/get-setting-nn (:settings cm) :mqtt-broker-host) ":" (settings/get-setting-nn (:settings cm) :mqtt-broker-port))
          topics (settings/get-setting-nn (:settings cm) :mqtt-subscription-topics)
          executor (at/mk-pool)
          new-self (assoc cm :topics topics
                             :mqtt-connect-string connect-string
                             :mqtt-con (atom nil)
                             :mqtt-id (atom nil)
                             :connection-lost (atom true))]
      (assoc new-self :schedule (at/every u/minute (publish-subscribe new-self) executor))))

  (stop [cm]
    (log/info "stopping Mqtt")
    (mh/disconnect (:mqtt-con cm))))

(defn create-mqtt-component []
  (map->MqttComponent {}))
