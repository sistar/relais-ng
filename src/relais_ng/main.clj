(ns relais-ng.main
  (:require
    [clojure.tools.logging :as log]
    [com.stuartsierra.component :as component]
    [relais-ng.settings :refer [new-settings]]
    [relais-ng.thingspeak :as ts]
    [relais-ng.temperature-measurement :as tm]
    [relais-ng.activation-manager :as am]
    [relais-ng.http-server :refer [create-http-server]]
    [relais-ng.raspi-io :refer [create-rio create-rio-mock]]
    [relais-ng.utils :refer [on-shutdown]])
  (:gen-class :main true))

(defn base-system [rio-constructor]
  (->
    (component/system-map
      :settings (new-settings)
      :rio (component/using
             (rio-constructor) [:settings])
      :thing-speak (component/using
                     (ts/create) [:settings :rio])
      :tm (component/using
            (tm/create-temp-measurement [:settings]) [:settings :thing-speak])
      :am (component/using
            (am/activation-manager-component [:rio :tm :settings]) [:rio :tm :settings])
      :http-server (component/using
                     (create-http-server [:rio :tm :am] 3000) [:rio :tm :am]))))

;; entry point

(defn -main
  [& args]
  (let [raspi? (= (System/getProperty "os.arch") "arm")
        b-s (base-system (if raspi? create-rio create-rio-mock))
        system (component/start b-s)]
    (on-shutdown
      (log/info "interrupted! shutting down")
      (component/stop system))
    (.join (Thread/currentThread))))