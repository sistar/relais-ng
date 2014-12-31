(ns relais-ng.activation-manager
  (:import
           (java.io File PushbackReader FileReader))
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [relais-ng.settings :as settings]
            [relais-ng.raspi-io :as rio]))

(defn frm-save
  "Save a clojure form to file."
  [#^File file form]
  (with-open [w (java.io.FileWriter. file)]
    (binding [*out* w *print-dup* true] (prn form))))

(defn frm-load
  "Load a clojure form from file."
  [#^File file]
  (with-open [r (PushbackReader.
                  (FileReader. file))]
    (let [rec (read r)]
      rec)))

(defrecord ActivationManager [rio tm settings]
  component/Lifecycle
  (start [component]
    (println ";; Starting Activation Manager" )
    (let [rule-store (settings/get-setting settings :rule-store)
          store (File. rule-store)
          _ (log/debug "configured rule-store is :"rule-store " isFile: " (.isFile store))
          rule (if (.isFile store)
                       (frm-load store)
                       (fn [t-measured] true))]
      (-> (assoc component :rule (ref rule))
          (assoc :store store)
          )))
  (stop [component]
    (println ";; Stopping Activation Manager")
    (assoc component :am nil)))

(defn activation-manager-component []
  (map->ActivationManager {}))