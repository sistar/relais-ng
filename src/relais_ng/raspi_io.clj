(ns relais-ng.raspi-io
  (:import (com.pi4j.io.gpio GpioController GpioFactory PinState RaspiPin)
           (java.io File))
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]))

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
                 })

(defn init-dig-io-pin [self pin-name pin-state]
  (let [gpIoController (:gpIoController self)
        raspi-pin (get raspi-pins pin-name)
        gpio-pin-digital-output (if (some? raspi-pin)
                                  (.provisionDigitalOutputPin gpIoController raspi-pin pin-state)
                                  (log/error "no raspi pin " pin-name))]
    (if (some? gpio-pin-digital-output)
      (dosync (alter (:gpio-pin-digital-outputs self) assoc pin-name gpio-pin-digital-output))

      ) (log/debug @(:gpio-pin-digital-outputs self))))

(defn init-state [self pin-name pin-state-str]
  (init-dig-io-pin self pin-name (str-to-pin-state pin-state-str)))

(defn alter-state [self pin-name pin-state-str]
  (let [new-state (str-to-pin-state pin-state-str)
        gpio-pin-digital-output (get @(:gpio-pin-digital-outputs self) pin-name)]
    (if (some? gpio-pin-digital-output)
      (.setState gpio-pin-digital-output new-state)
      (log/error "no digital output" pin-name " not setting pin state" pin-state-str))))

(defn init-or-alter-state [self pin-name pin-state-str]
  (let [gpio-pin-digital-output (get @(:gpio-pin-digital-outputs self) pin-name)]
    (if (some? gpio-pin-digital-output)
      (alter-state self pin-name pin-state-str)
      (init-state self pin-name pin-state-str))))

(defn frm-save
  "Save a clojure form to file."
  [#^java.io.File file form]
  (with-open [w (java.io.FileWriter. file)]
    (binding [*out* w *print-dup* true] (prn form))))

(defn frm-load
  "Load a clojure form from file."
  [#^java.io.File file]
  (with-open [r (java.io.PushbackReader.
                  (java.io.FileReader. file))]
    (let [rec (read r)]
      rec)))

(defn init-from-persisted [c pin-states]
  (doseq [kv pin-states]
    (init-or-alter-state c (:pinName kv) (:pinState kv)))
  c)

(defrecord RaspIo [native gpIoController]
  component/Lifecycle
  (start [component]
    (println ";; Starting Raspberry Pi IO native:" native)
    (let [store (File. "heat-state.clj")
          pin-states (if (.isFile store)
                       (frm-load store)
                       {})]
      (-> (assoc component :gpio-pin-digital-outputs (ref {}))
          (init-from-persisted pin-states))))
  (stop [component]
    (println ";; Stopping Raspberry Pi IO")
    (assoc component :rio nil)))

(defn create-rio
  []
  (map->RaspIo {:native?        true
                :gpIoController (GpioFactory/getInstance)})
  )

(defn createPinProxy [name]
  (let [state (atom {:pin-state com.pi4j.io.gpio.PinState/HIGH})]
    (proxy [com.pi4j.io.gpio.GpioPinDigitalOutput] []
      (getState [] (:pin-state @state))
      (setState [pinState] (swap! state assoc :pin-state pinState))
      (getName [] name))))

(defn create-rio-mock
  []
  (map->RaspIo {:native?        false
                :gpIoController (proxy [com.pi4j.io.gpio.GpioController] []
                                  (provisionDigitalOutputPin
                                    ([] (createPinProxy "?"))
                                    ([provider pin] (createPinProxy "??"))
                                    ([pin pinName pinState] (println pin pinName pinState) (createPinProxy pinName))
                                    ([provider pin pinName pinState] (println provider pin pinName pinState) (createPinProxy pinName))))}))

(defn get-state
  "nil for non existing pin"
  [self pin-name]
  (let [gpio-pin-digital-output (get @(:gpio-pin-digital-outputs self) pin-name)]
    (if (some? gpio-pin-digital-output)
      (.name (.getState gpio-pin-digital-output))
      nil)))

(defn relais-info
  [self]
  (let [pin-names (keys @(:gpio-pin-digital-outputs self))
        states (map #(get-state self %) pin-names)
        vectors (map vector pin-names states)]
    (map #(zipmap [:pinName :pinState] %) vectors)))

(defn persist-states!
  [self]
  (frm-save (File. "heat-state.clj") (relais-info self)))

(defn single-relais-info
  [self pin-name]
  (let [state (get-state self pin-name)]
    (if (some? state)
      (zipmap [:pinName :pinState] [pin-name state])
      nil)))

(defn set-relais-state
  [self pin]
  (init-or-alter-state self (:pinName pin) (:pinState pin))
  (persist-states! self)
  (single-relais-info self (:pinName pin)))
