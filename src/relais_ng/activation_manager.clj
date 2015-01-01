(ns relais-ng.activation-manager
  (:import
    (java.io File PushbackReader FileReader))
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [overtone.at-at :as at]
            [relais-ng.settings :as settings]
            [relais-ng.utils :as u]
            [relais-ng.raspi-io :as rio]
            [relais-ng.temperature-measurement :as tm]))

(def hour (* 60 60 1000))
(def minute (* 60 1000))

(defn get-rule
  [self]
  @(:rule-str self))

(defn set-rule!
  [self rule-str]
  (try
    (let [rule (read-string rule-str)
          _ (dosync (ref-set (:rule-str self) rule-str)
                    (ref-set (:rule self) rule))]
      @(:rule self)
      ) (catch Exception e)))

(defn calc-rule
  [self]
  (let [measurement (tm/get-Measurement (:tm :self))
        e-f (eval @(:rule self))]
    (if (some? measurement)
      (e-f measurement)
      (do (log/error "no measurement result")
          "LOW"))))

(defn apply-rule!
  [self]
  (let [rio (:rio self)
        names (rio/pin-names rio)
        result (calc-rule self)
        _ (log/info "applying rule: " (:rule self) " result: " result " to: " names)]
    (doseq [name names]
      (if (or (= result "LOW") (= result "HIGH"))
        (rio/set-relais-state! rio {:pinName name :pinState result})))))


(defrecord ActivationManager
  [rio tm settings]
  component/Lifecycle
  (start [component]
    (println ";; Starting Activation Manager")
    (let [rule-store (settings/get-setting settings :rule-store)
          executor (at/mk-pool)
          store (File. rule-store)
          _ (log/debug "configured rule-store is :" rule-store " isFile: " (.isFile store))
          default-fn (fn [measurement] "NOOP")
          rule (if (.isFile store)
                 (u/frm-load store default-fn)
                 (do (log/error "could not read from rule-store")
                     default-fn))
          do-apply-rules true
          new-self (assoc component
                          :rule (ref rule)
                          :rule-str (ref (str rule))
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