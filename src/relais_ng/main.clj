(ns relais-ng.main
  (:require
    [clojure.tools.logging :as log]
    [com.stuartsierra.component :as component]
    [relais-ng.settings :refer [new-settings]]
    [relais-ng.http-server :refer [create-http-server]]
    [relais-ng.raspi_io :refer [create-rio create-rio-mock]]
    [relais-ng.utils :refer [on-shutdown]])
  (:gen-class :main true))

(defn base-system []
  (->
    (component/system-map
      :settings new-settings
      :rio (component/using
             (create-rio) [:settings])
      :http-server (component/using
                     (create-http-server [:rio] 3000) [:rio]))))
(defn base-system-mock []
  (->
    (component/system-map
      :settings new-settings
      :rio (component/using
             (create-rio-mock) [:settings])
      :http-server (component/using
                     (create-http-server [:rio] 3000) [:rio]))))


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