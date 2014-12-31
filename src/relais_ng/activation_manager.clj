(ns relais-ng.activation-manager
  (:import
    (java.io File PushbackReader FileReader))
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [relais-ng.settings :as settings]
            [relais-ng.raspi-io :as rio]
            [relais-ng.temperature-measurement :as tm]))

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

(defrecord ActivationManager
  [rio tm settings]
  component/Lifecycle
  (start [component]
    (println ";; Starting Activation Manager")
    (let [rule-store (settings/get-setting settings :rule-store)
          store (File. rule-store)
          _ (log/debug "configured rule-store is :" rule-store " isFile: " (.isFile store))
          rule (if (.isFile store)
                 (frm-load store)
                 (fn [measurement] (rio/str-to-pin-state "LOW")))]
      (-> (assoc component :rule (ref rule))
          (assoc :rule-str (ref ""))
          (assoc :store store)
          )))
  (stop [component]
    (println ";; Stopping Activation Manager")
    (assoc component :am nil)))

(defn activation-manager-component
  []
  (map->ActivationManager {}))

(defn set-rule
  [self rule-str]
  (let [rule (read-string rule-str)
        _ (dosync (ref-set (:rule self) rule))]))

(defn calc-rule
  [self]
  (let [measurement (tm/get-Measurement (:tm :self))
        e-f (eval @(:rule self))]
    (e-f measurement)
    )
  )

(defn apply-rule [self]
  (let [rio (:rio self)
        names (rio/pin-names rio)
        result (calc-rule self)]
    (doseq [name names]
      (rio/set-relais-state! rio {:pinName name :pinState result}))))