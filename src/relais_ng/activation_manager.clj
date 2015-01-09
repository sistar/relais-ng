(ns relais-ng.activation-manager
  (:import
    (java.io File))
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

(s/defschema ActivationRule {:time {:from String :to String} :rule String :id String :position String})

(defn get-activation-rule
  "clojure-rule serialized to string"
  [self id]
  (let [ars @(:activation-rules self)
        ar (ars id)
        f (:from (:time ar))
        t (:to (:time ar))
        r (:rule ar)
        i (:id ar)
        p (:position ar)]
    {:time {:from f :to t} :rule (str r) :id i :position p}))

(defn get-activation-rules
  [self]
  (sort-by #(:id %) (map (partial get-activation-rule self) (keys @(:activation-rules self)))))

(defn eval-rule [ar]
  (let [f (:from (:time ar))
        t (:to (:time ar))
        r (:rule ar)
        i (:id ar)
        p (:position ar)
        er (if (= (type r) String) (read-string r) r)]
    {:time {:from f :to t} :rule er :id i :position p}))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn set-rule!
  [self activation-rule]
  (u/loge
    (str "set rule: " activation-rule)
    (let [i (:id activation-rule)
          i-valid (if (and (some? i) (not (some #{i} (map #(keys %) @(:activation-rules self))))) i (uuid))
          a-r-i-v (assoc activation-rule :id i-valid)
          p (:position activation-rule)
          existing-positions (map #((:position (vals %))) @(:activation-rules self))
          p-valid (if (and (some? p) (not (some #{p} existing-positions))) p (+ (max existing-positions) 1))
          a-r-p-v (assoc a-r-i-v :position p-valid)
          _ (log/info "set-rule! " a-r-p-v)
          ]
      (dosync (ref-set (:activation-rules self) (assoc @(:activation-rules self) (:id a-r-p-v) (eval-rule a-r-p-v))))
      (u/persist-states! self @(:activation-rules self))
      (get-activation-rule self (:id a-r-p-v))
      )
    ))

(defn delete-rule! [self id]
  (dosync (ref-set (:activation-rules self) (dissoc @(:activation-rules self) id))))

(defn calc-rule
  [self id]
  (if (contains? @(:activation-rules self) id)
    (let [measurement (tm/get-Measurement (:tm self))
          e-f (eval (:rule (@(:activation-rules self) id)))]
      (if (and (some? measurement) (some? e-f))
        (e-f measurement)
        (do (log/error "measurement result: " measurement "e-f" e-f)
            :low)))
    (log/error "NO")
    ))

(defn first-non-noop-wins [left right]
  (if (not (= :no-op left))
    left
    right
    ))

(defn apply-rules!
  [self]
  (let [rio (:rio self)
        names (rio/pin-names rio)
        _ (log/error (str "type results" @(:activation-rules self)))
        results (map (partial calc-rule self) (keys @(:activation-rules self)))
        result (reduce first-non-noop-wins results)
        _ (log/info "applying rules: " @(:activation-rules self) " result: " result " to: " names)]
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
          default-ar {:time {:from "00:00" :to "23:59"} :rule default-fn :id 0 :position 0}
          a-rs (if (.isFile store)
                 (u/frm-load store default-fn)
                 (do (log/error "could not read from rule-store")
                     {(:id default-ar) default-ar}))
          do-apply-rules (settings/get-setting settings :apply-rules)
          new-self (assoc component
                     :activation-rules (ref a-rs)
                     :store store
                     :executor executor)
          new-self-2 (assoc new-self :schedule (if do-apply-rules
                                                 (at/every minute
                                                           #(try (apply-rules! new-self)
                                                                 (catch Throwable e (log/error e e)))
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