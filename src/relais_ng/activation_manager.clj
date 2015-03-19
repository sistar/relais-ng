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
            [schema.core :as s]
            [relais-ng.index-util :as iu]
            [relais-ng.thingspeak :as ts]))


(s/defschema ActivationRule {(s/optional-key :time) {:from String :to String} :rule String (s/optional-key :id) String (s/optional-key :position) Long})

(defn ser-activation-rule
  "clojure-rule serialized to string"
  [self ar]
  (let [f (:from (:time ar))
        t (:to (:time ar))
        r (:rule ar)
        i (:id ar)
        p (:position ar)]
    {:time {:from f :to t} :rule (str r) :id (str i) :position p}))

(defn get-activation-rule
  "clojure-rule serialized to string"
  [self id]
  (let [ar (get @(:activation-rules self) id)]
    (if (some? ar) (ser-activation-rule self ar) nil)))

(defn get-activation-rules
  [self]
  (sort-by #(:id %) (map (partial get-activation-rule self) (keys @(:activation-rules self)))))

(defn eval-rule [ar]
  (let [f (if-some [x (:from (:time ar))] x "00:00:00")
        t (if-some [x (:to (:time ar))] x "23:59:59")
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
    (let [id-transmitted (some? (:id activation-rule))
          i (str (:id activation-rule))
          a-rs @(:activation-rules self)
          i-valid (if (and id-transmitted (not (contains? a-rs i))) i (uuid))
          a-r-i-v (assoc activation-rule :id i-valid)
          vals (vals a-rs)
          p-valid (iu/valid-index :position get-activation-rule vals)
          a-r-p-v (assoc a-r-i-v :position p-valid)
          _ (log/info "set-rule! " a-r-p-v)
          ]
      (dosync (ref-set (:activation-rules self) (assoc @(:activation-rules self) (:id a-r-p-v) (eval-rule a-r-p-v))))
      (u/frm-save (:store self) @(:activation-rules self))
      (get-activation-rule self (:id a-r-p-v))
      )
    ))

(defn delete-rule!
  [self id]
  (let
    [item (get @(:activation-rules self) id)]
    (log/info "delete with id" id "item" item "some?:" (some? item) " out of" (keys @(:activation-rules self)))
    (if (some? item)
      (do
        (dosync (ref-set (:activation-rules self) (dissoc @(:activation-rules self) id)))
        (u/frm-save (:store self) @(:activation-rules self))))
    item))

(defn reset-rules!
  [self]
  (let [ks (keys @(:activation-rules self))
        f (partial delete-rule! self)]
    (doall (map f ks))))


(defn calc-rule
  [self id]
  (if (contains? @(:activation-rules self) id)
    (let [measurement (tm/get-Measurement (:tm self))
          rule (:rule (@(:activation-rules self) id))
          e-f (eval rule)]
      (if (and (some? measurement) (some? e-f))
        (e-f measurement)
        (do (log/info "measurement result: " measurement "e-f" e-f) :low))
      )
    (log/error "NO RULE with ID" id)))

(defn first-non-noop-wins
  ([left right] (if (not (= :no-op left)) left right))
  ([] :no-op))

(defn apply-rules!
  [self]
  (if (> (count @(:activation-rules self)) 0)
    (let [rio (:rio self)
          names (rio/pin-names rio)
          results (doall (map (partial calc-rule self) (keys @(:activation-rules self))))
          result (reduce first-non-noop-wins results)
          _ (log/info "applying rules: " @(:activation-rules self) " result: " result " to: " names)]
      (doseq [name names]
        (if (or (= result :low) (= result :high))
          (rio/set-relais-state! rio {:pinName name :pinState result}))))))

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
          default-ar {:time {:from "00:00" :to "23:59"} :rule default-fn :id "0" :position 0}
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
                                                 (at/every u/minute
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