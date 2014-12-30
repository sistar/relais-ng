(ns relais-ng.main
  (:require
    [clojure.tools.logging :as log]
    [com.stuartsierra.component :as component]
    [relais-ng.settings :refer [new-settings]]
    [relais-ng.temperature-measurement :as tm]
    [relais-ng.http-server :refer [create-http-server]]
    [relais-ng.raspi-io :refer [create-rio create-rio-mock]]
    [relais-ng.utils :refer [on-shutdown]])
  (:gen-class :main true))

(defn base-system []
  (->
    (component/system-map
      :settings (new-settings)
      :rio (component/using
             (create-rio) [:settings])
      :tm (component/using (tm/create-temp-measurement [:settings])[:settings])
      :http-server (component/using
                     (create-http-server [:rio :tm] 3000) [:rio :tm]))))
(defn base-system-mock []
  (->
    (component/system-map
      :settings (new-settings)
      :rio (component/using
             (create-rio-mock) [:settings])
      :tm (component/using
            (tm/create-temp-measurement [:settings])[:settings])
      :http-server (component/using
                     (create-http-server [:rio :tm] 3000) [:rio :tm]))))

(defrecord RelaisSystem []
  component/Lifecycle
  (start [this]
    (log/info "starting relais")
    (component/start-system this))
  (stop [this]
    (log/info "stopping relais")
    (component/stop-system this)))

;; entry point

(defn -main
  [& args]
  (let [
        raspi? (=(System/getProperty "os.arch")"arm")
        b-s (if raspi? (base-system ) (base-system-mock ))
        system (component/start b-s)]
    (on-shutdown
      (log/info "interrupted! shutting down")
      (component/stop system))
    (.join (Thread/currentThread))))