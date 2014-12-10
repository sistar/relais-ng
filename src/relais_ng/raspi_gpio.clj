(ns relais-ng.raspi-gpio
  (:require [com.stuartsierra.component :as component]))







(defrecord GpioComponent [fields]
   component/Lifecycle
   (start [component]
     (println ";; Starting Gpio")
   ))
