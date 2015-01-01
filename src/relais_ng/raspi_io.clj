(ns relais-ng.raspi-io
  (:import (com.pi4j.io.gpio GpioController GpioFactory PinState RaspiPin)
           (java.io File))
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [relais-ng.settings :as settings]
            [relais-ng.utils :as u]))

(defn str-to-pin-state [s]
  (if (= (clojure.string/lower-case s) "high") PinState/HIGH PinState/LOW))

(def raspi-pins {"00" RaspiPin/GPIO_00
                 "01" RaspiPin/GPIO_01
                 "02" RaspiPin/GPIO_02
                 "03" RaspiPin/GPIO_03
                 "04" RaspiPin/GPIO_04
                 "05" RaspiPin/GPIO_05
                 "06" RaspiPin/GPIO_06
                 "07" RaspiPin/GPIO_07
                 "08" RaspiPin/GPIO_08
                 "09" RaspiPin/GPIO_09
                 "10" RaspiPin/GPIO_10
                 "11" RaspiPin/GPIO_11
                 "12" RaspiPin/GPIO_12
                 "13" RaspiPin/GPIO_13
                 "14" RaspiPin/GPIO_14
                 "15" RaspiPin/GPIO_15
                 "16" RaspiPin/GPIO_16
                 "17" RaspiPin/GPIO_17
                 "18" RaspiPin/GPIO_18
                 "19" RaspiPin/GPIO_19
                 "20" RaspiPin/GPIO_20
                 })

(defn init-dig-io-pin [self pin-name pin-state]
  (let [gpIoController (:gpIoController self)
        raspi-pin (get raspi-pins pin-name)
        gpio-pin-digital-output (if (some? raspi-pin)
                                  (.provisionDigitalOutputPin gpIoController raspi-pin pin-state)
                                  (log/error "no raspi pin for name" pin-name))]
    (if (some? gpio-pin-digital-output)
      (do
        (log/debug (.getName gpio-pin-digital-output) (.getState gpio-pin-digital-output))
        (dosync (alter (:gpio-pin-digital-outputs self) assoc raspi-pin gpio-pin-digital-output))))
    (log/debug @(:gpio-pin-digital-outputs self))))

(defn init-state! [self pin-name pin-state-str]
  (init-dig-io-pin self pin-name (str-to-pin-state pin-state-str)))

(defn alter-state! [self pin-name pin-state-str]
  (if (= (clojure.string/upper-case pin-state-str) "NOOP")
    (log/debug "not altering state for NOOP state-str")
    (let [new-state (str-to-pin-state pin-state-str)
          raspi-pin (get raspi-pins pin-name)
          gpio-pin-digital-output (get @(:gpio-pin-digital-outputs self) raspi-pin)]
      (if (some? gpio-pin-digital-output)
        (.setState gpio-pin-digital-output new-state)
        (log/error "no digital output" pin-name " not setting pin state" pin-state-str)))))

(defn init-or-alter-state! [self pin-name pin-state-str]
  (let [raspi-pin (get raspi-pins pin-name)
        gpio-pin-digital-output (get @(:gpio-pin-digital-outputs self) raspi-pin)]
    (if (some? gpio-pin-digital-output)
      (alter-state! self pin-name pin-state-str)
      (init-state! self pin-name pin-state-str))))

(defn init-from-persisted! [c pin-states]
  (doseq [kv pin-states]
    (init-or-alter-state! c (:pinName kv) (:pinState kv)))
  c)

(defrecord RaspIo [native gpIoController settings]
  component/Lifecycle
  (start [component]
    (println ";; Starting Raspberry Pi IO native:" native)
    (let [state-store (settings/get-setting settings :state-store)
          store (File. state-store)
          _ (log/debug "configured store is :" state-store " isFile: " (.isFile store))
          pin-states (if (.isFile store)
                       (u/frm-load store {})
                       {})]
      (-> (assoc component :gpio-pin-digital-outputs (ref {}))
          (assoc :store store)
          (init-from-persisted! pin-states))))
  (stop [component]
    (println ";; Stopping Raspberry Pi IO")
    (assoc component :rio nil)))

(defn create-rio
  []
  (map->RaspIo {:native?        true
                :gpIoController (GpioFactory/getInstance)}))

(defn createPinProxy [name state]
  (let [state (atom {:pin-state state})]
    (proxy [com.pi4j.io.gpio.GpioPinDigitalOutput] []
      (getState [] (:pin-state @state))
      (setState [pinState] (swap! state assoc :pin-state pinState))
      (getName [] name))))

(defn create-rio-mock
  []
  (map->RaspIo {:native?        false
                :gpIoController (proxy [com.pi4j.io.gpio.GpioController] []
                                  (provisionDigitalOutputPin
                                    ([] (createPinProxy "?" (str-to-pin-state "HIGH")))
                                    ([pin pinState] (createPinProxy (.getName pin) (str-to-pin-state pinState)))
                                    ([pin pinName pinState] (createPinProxy pinName pinState))
                                    ([provider pin pinName pinState] (createPinProxy pinName pinState))))}))

(defn get-state
  "nil for non existing pin"
  [self pin-name]
  (let [raspi-pin (get raspi-pins pin-name)
        gpio-pin-digital-output (get @(:gpio-pin-digital-outputs self) raspi-pin)]
    (if (some? gpio-pin-digital-output)
      (.name (.getState gpio-pin-digital-output))
      nil)))

(defn relais-info
  [self]
  (let [raspi-pins (keys @(:gpio-pin-digital-outputs self))
        states (map #(get-state self %) raspi-pins)
        pin-names (map #(format "%02d" (.getAddress %)) raspi-pins)
        vectors (map vector pin-names states)]
    (map #(zipmap [:pinName :pinState] %) vectors)))

(defn pin-names
  [self]
  (let [raspi-pins (keys @(:gpio-pin-digital-outputs self))
        names (map #(format "%02d" (.getAddress %)) raspi-pins)]
    names))

(defn persist-states!
  [self]
  (u/frm-save (:store self) (relais-info self)))

(defn single-relais-info
  [self pin-name]
  (let [state (get-state self pin-name)]
    (if (some? state)
      (zipmap [:pinName :pinState] [pin-name state])
      nil)))

(defn set-relais-state!
  [self pin]
  (init-or-alter-state! self (:pinName pin) (:pinState pin))
  (persist-states! self)
  (single-relais-info self (:pinName pin)))
