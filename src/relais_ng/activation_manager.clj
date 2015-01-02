(ns relais-ng.activation-manager
  (:import
    (java.io File PushbackReader FileReader))
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [overtone.at-at :as at]
            [relais-ng.settings :as settings]
            [relais-ng.utils :as u]
            [relais-ng.raspi-io :as rio]
            [relais-ng.temperature-measurement :as tm]
            [schema.core :as s]))

(def hour (* 60 60 1000))
(def minute (* 60 1000))

(s/defschema ActivationRule {:time {:from String :to String} :rule String})

(defn get-rule
  [self]
  (let [value {:time {:from "00:00" :to "23:59"} :rule (str @(:rule-str self))}]
    (log/trace "---> " value)
    value))

(defn set-rule!
  [self rule-str]
  (u/loge
    (str "set rule: " rule-str)
    (let [s (str "'" (:rule rule-str)
          rule (read-string s))
          _ (dosync (ref-set (:rule-str self) rule)
                    (ref-set (:rule self) rule))]))
  (get-rule self))

(defn calc-rule
  [self]
  (let [
        measurement (tm/get-Measurement (:tm :self))
        e-f (eval @(:rule-str self))]
    (if (and (some? measurement) (some? e-f))
      (e-f measurement)
      (do (log/error "measurement result: " measurement "e-f" e-f)
          :low))))

(defn apply-rule!
  [self]
  (let [rio (:rio self)
        names (rio/pin-names rio)
        result (calc-rule self)
        _ (log/info "applying rule: " (:rule-str self) " result: " result " to: " names)]
    (doseq [name names]
      (if (or (= result :low) (= result :high))
        (rio/set-relais-state! rio {:pinName name :pinState result})))))


(defrecord ActivationManager
  [rio tm settings]
  component/Lifecycle
  (start [component]
    (println ";; Starting Activation Manager")
    (let [rule-store (settings/get-setting-nn settings :rule-store)
          executor (at/mk-pool)
          store (File. rule-store)
          _ (log/debug "configured rule-store is :" rule-store " isFile: " (.isFile store))
          default-fn '(fn [measurement] :no-op)
          rule (if (.isFile store)
                 (u/frm-load store default-fn)
                 (do (log/error "could not read from rule-store")
                     default-fn))
          do-apply-rules (settings/get-setting settings :apply-rules)
          new-self (assoc component
                     :rule (ref rule)
                     :rule-str (ref rule)
                     :store store
                     :executor executor)
          new-self-2 (assoc new-self :schedule (if do-apply-rules
                                                 (at/every minute
                                                           #(try (apply-rule! new-self)
                                                                 (catch Throwable e (log/error "fail" (.printStackTrace e))))
                                                           executor)
                                                 nil))
          ]
      new-self-2))

  (stop [component]
    (println ";; Stopping Activation Manager")
    (assoc component :am nil)))

(defn activation-manager-component
  [deps]
  (map->ActivationManager {}))