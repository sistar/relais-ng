(ns relais-ng.main
  (:require
    [clojure.tools.logging :as log]
    [com.stuartsierra.component :as component]
    [relais-ng.settings :refer [new-settings]]
    [relais-ng.http-server :refer [create-http-server]]
    [relais-ng.raspi_io :refer [create-rio]]
    [relais-ng.utils :refer [on-shutdown]])
  (:gen-class :main true))

(defn base-system []
  (->
    (component/system-map
      :settings (new-settings)
      :rio (create-rio [:settings])
      :http-server (create-http-server [:rio] 3000))
    (component/system-using
      {:http-server [:rio]})))

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
  (let [system (component/start (base-system))]
    (on-shutdown
      (log/info "interrupted! shutting down")
      (component/stop system))
    (.join (Thread/currentThread))))