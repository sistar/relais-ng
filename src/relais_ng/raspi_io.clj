(ns relais-ng.raspi_io
  (:import (com.pi4j.io.gpio GpioController GpioFactory PinState RaspiPin)
           (java.io File))
  (:require [com.stuartsierra.component :as component]))

(defn lookup-pin-state [s]
  (if (= (clojure.string/lower-case s) "high") PinState/HIGH PinState/LOW))

(defn set-pin-state [self id desiredState]
  (prn [id "to" desiredState] " really: " (lookup-pin-state desiredState) "toString" (.toString (lookup-pin-state desiredState)))
  (.setState ((:pins self) id) (lookup-pin-state desiredState)))

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
                                  (.provisionDigitalOutputPin gpIoController raspi-pin pin-state))]
    (if (some? gpio-pin-digital-output)
      (dosync (alter (:gpio-pin-digital-outputs self) assoc pin-name gpio-pin-digital-output))
      )))

(defn init-state [self pin-name pin-state-str]
  ((init-dig-io-pin self pin-name (lookup-pin-state pin-state-str))))

(defn alter-state [self pin-name pin-state-str]
  (let [new-state (lookup-pin-state pin-state-str)
        gpio-pin-digital-output (get @(:gpio-pin-digital-outputs self) pin-name)]
    (if (some? gpio-pin-digital-output)
      (.setState gpio-pin-digital-output new-state))))

(defn init-or-alter-state [self pin-name pin-state-str]
  (let [gpio-pin-digital-output (get @(:gpio-pin-digital-outputs self) pin-name)]
    (if (some? gpio-pin-digital-output)
      (alter-state self pin-name pin-state-str)
      (init-state self pin-name pin-state-str))))

(defn init-states
  [self]
  (let [pin-states @(:pin-states self)]
    (doseq [kv pin-states] (init-or-alter-state self (key kv) (val kv)))))

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

(defn persist-states
  [self]
  (let [pin-states @(:pin-states self)]
    (frm-save (File. "heat-state.clj") pin-states)
    )
  )

(defrecord RaspIo [gpIoController]
  component/Lifecycle
  (start [component]
    (println ";; Starting Raspberry Pi IO")
    (let [gpIoController (GpioFactory/getInstance)
          store (File. "heat-state.clj")]
      (assoc component :gpio-pin-digital-outputs (ref {}))
      (assoc component :gpIoController gpIoController)
      (if (.isFile store)
        (assoc component :pin-states (ref (frm-load store)))
        (assoc component :pin-states (ref {}))
        ))
    (init-states component)))

(defn createPinProxy [name]
  (proxy [com.pi4j.io.gpio.GpioPinDigitalOutput] []
    (getState [] com.pi4j.io.gpio.PinState/HIGH)
    (getName [] name)))

(defn createControllerProxy []
  (proxy [com.pi4j.io.gpio.GpioController] []
    (provisionDigitalOutputPin
      ([] (createPinProxy "?"))
      ([provider pin] (createPinProxy "??"))
      ([pin pinName pinState] (println pin pinName pinState) (createPinProxy pinName))
      ([provider pin pinName pinState] (println provider pin pinName pinState) (createPinProxy pinName)))))

(defrecord RaspIoMock [gpIoController]
  component/Lifecycle
  (start [component]
    (println ";; Starting Raspberry Pi IO Mock")
    (let [gpIoController (createControllerProxy)
          store (File. "heat-state.clj")]
      (assoc component :gpio-pin-digital-outputs (ref {}))
      (assoc component :gpIoController gpIoController)
      (if (.isFile store)
        (assoc component :pin-states (ref (frm-load store)))
        (assoc component :pin-states (ref {}))
        )))

  (stop [component]
    (println ";; Stopping Raspberry Pi IO Mock")
    (assoc component :rio nil)))

(defn create-rio
  [deps]
  (let [settings (:settings deps)
        raspi? (:raspi? (:state settings))]
    (if raspi?
      (map->RaspIo {:deps deps})
      (map->RaspIoMock {:deps deps}))))

(defn get-state
  "nil for non existing pin"
  [self id]
  (let [pin-states @(:pin-states self)
        pin-state (pin-states id)]
    pin-state)
  )

(defn relais-info
  [self]
  (let [psmr (:pin-states self)
        names (keys @psmr)
        states (map #(get-state self %) names)
        vectors (map vector names states)]
    (map #(zipmap [:pinName :pinState] %) vectors)))

(defn single-relais-info
  [self id]
  (let [state (get-state self id)]
    (if (some? state)
      (zipmap [:pinName :pinState] [id state])
      nil)))

(defn set-relais-state
  [self pin]

  (dosync (alter (:pin-states self) assoc (:pinName pin) (:pinState pin)))
  ;; only native (set-pin-state self (:pinName pin) (:pinState pin))
  (persist-states self)
  (single-relais-info self (:pinName pin)))
