(ns relais-ng.http-server
  (:require [org.httpkit.server :refer [run-server]]
            [com.stuartsierra.component :as component]
            [relais-ng.components :as c]
            [clojure.tools.logging :as log]))

(defrecord Http-server [opts deps rio]
  component/Lifecycle
  (start [component]
    (log/info "starting http-server")
    (assoc component :http-kit (run-server (c/make-handler component) opts)))
  (stop [{:keys [http-kit] :as component}]
    (when http-kit (http-kit))
    (assoc component :http-kit nil)))

(defn create-http-server
  "Creates Http-server which will use `make-handler` to create a ring handler
   which has components given in first parameter bound into request map."
  [deps port]
  (map->Http-server {:opts {:port port} :deps deps}))