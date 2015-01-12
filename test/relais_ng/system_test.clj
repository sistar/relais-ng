(ns relais-ng.system-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as c]
            [relais-ng.components :as components]
            [relais-ng.settings :as settings]
            [relais-ng.handler :as handler]
            [relais-ng.pin :as pin]
            [relais-ng.main :as main]
            [relais-ng.utils :as u]
            [relais-ng.raspi-io :as rio]
            [relais-ng.temperature-measurement :as tm]
            [relais-ng.activation-manager :as am]
            [clojure.tools.logging :as log])
  (:import (clojure.lang PersistentList)))

(defn test-system [& args]
  (c/system-map
    :settings (relais-ng.settings/new-settings (atom {:apply-rules false :state-store "/tmp/t-store.clj" :measure-script (or (first args) "/foo") :rule-store "/tmp/r-store.clj"}))
    :tm (c/using (tm/create-temp-measurement false) [:settings])
    :rio (c/using (rio/create-rio-mock) [:settings])
    :am (c/using (am/activation-manager-component [:rio :tm :settings]) [:rio :tm :settings])))

(deftest test-rpio
  (testing "a rio component stores pin state"
    (with-redefs [u/persist-states! (fn [self f] (println "mocked persist-state!" f))]
      (let [s (c/start (test-system))
            r (rio/set-relais-state! (:rio s) {:pinName "09" :pinState "LOW"})
            ]
        (is (= r {:pinName "09" :pinState "LOW"})))))
  (testing "unknown pin is returned as nil"
    (let [s (c/start (test-system))
          r (rio/single-relais-info (:rio s) "baz")
          ]
      (is (nil? r)))))
(deftest test-calc-activation-manager
  (let [sample-rule-persist (read-string "{:time {:from \"00:00\" :to \"23:59\"} :rule (fn [m] (if (< (:temperature m) 20) :high :low)) :id 0 :position 0}")
        sample-rule-web (read-string "{:time {:from \"00:00\" :to \"23:59\"} :rule \"(fn [m] (if (< (:temperature m) 20) :high :low))\"  :id 0 :position 0 }")
        sample-rule-web-2 (read-string "{:time {:from \"21:00\" :to \"06:00\"} :rule \"(fn [m] (if (< (:temperature m) 18) :high :low))\"   }")]

    (testing "should calculate activation :rule set as PersistentList..."
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 21 :humidity 89})]
        (let [s (c/start (test-system))
              rule (am/set-rule! (:am s) sample-rule-persist)
              result (am/calc-rule (:am s) (:id rule))]
          (is (= (:id rule) "0"))
          (is (= (type (:rule sample-rule-persist)) PersistentList))
          (is (= result :low)))))

    (testing "should calculate activation :rule set as PersistentList..."
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 21 :humidity 89})]
        (let [s (c/start (test-system))
              rule (am/set-rule! (:am s) sample-rule-persist)
              result (am/calc-rule (:am s) (:id rule))]
          (is (= (type (:rule sample-rule-persist)) PersistentList))
          (is (= result :low)))))

    ))

(deftest test-id-activation-manager
  (let [sample-rule-persist (read-string "{:time {:from \"00:00\" :to \"23:59\"} :rule (fn [m] (if (< (:temperature m) 20) :high :low)) :id 0 :position 0}")
        sample-rule-web (read-string "{:time {:from \"00:00\" :to \"23:59\"} :rule \"(fn [m] (if (< (:temperature m) 20) :high :low))\"  :id 0 :position 0 }")
        sample-rule-web-2 (read-string "{:time {:from \"21:00\" :to \"06:00\"} :rule \"(fn [m] (if (< (:temperature m) 18) :high :low))\"   }")]



    (testing "a activation-manager component should add position and id"
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 21 :humidity 89})]
        (let [s (c/start (test-system))
              rule (am/set-rule! (:am s) sample-rule-persist)
              rule2 (am/set-rule! (:am s) sample-rule-web-2)
              state (am/get-activation-rules (:am s))
              _ (println @(:activation-rules (:am s)))
              ]
          (is (= (count state) 2))
          (is (= (:id (first state)) "0"))
          (is (= (count (:id (second state))) 36)))))))


(deftest test-activation-manager
  (let [sample-rule-persist (read-string "{:time {:from \"00:00\" :to \"23:59\"} :rule (fn [m] (if (< (:temperature m) 20) :high :low)) :id 0 :position 0}")
        sample-rule-web (read-string "{:time {:from \"00:00\" :to \"23:59\"} :rule \"(fn [m] (if (< (:temperature m) 20) :high :low))\"  :id 0 :position 0 }")
        sample-rule-web-2 (read-string "{:time {:from \"21:00\" :to \"06:00\"} :rule \"(fn [m] (if (< (:temperature m) 18) :high :low))\"   }")]






    (testing "a activation-manager component should calculate activation with :rule set as string..."
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 19 :humidity 89})]
        (let [s (c/start (test-system))
              rule (am/set-rule! (:am s) sample-rule-web)
              result (am/calc-rule (:am s) (:id rule))]

          (is (= (type (:rule sample-rule-web)) String))
          (is (= result :high)))))

    (testing "a activation-manager component should store 2 rules.."
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 19 :humidity 89})]
        (let [s (c/start (test-system))
              rule (am/set-rule! (:am s) sample-rule-web)
              result (am/calc-rule (:am s) (:id rule))]

          (is (= (type (:rule sample-rule-web)) String))
          (is (= result :high)))))


    (testing "a activation-manager component should do nothing without pins..."
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 21 :humidity 89})
                    u/persist-states! (fn [self f] (println "mocked persist-state!" f))]
        (let [s (c/start (test-system))
              rule (am/set-rule! (:am s) sample-rule-persist)
              result (am/apply-rules! (:am s))]
          (is (= 1 1)))))

    (testing "a activation-manager component should do settings on pins..."
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 21 :humidity 89})
                    u/persist-states! (fn [self f] (println "mocked persist-state!" f))]
        (let [s (c/start (test-system))
              r (rio/set-relais-state! (:rio s) {:pinName "09" :pinState "HIGH"})
              rule (am/set-rule! (:am s) sample-rule-persist)

              _ (am/apply-rules! (:am s))
              result (rio/single-relais-info (:rio s) "09")]
          (is (= (:pinState result) "LOW")))))

    (testing "a activation-manager component should do settings on pins..."
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 18 :humidity 89})
                    u/persist-states! (fn [self f] (println "mocked persist-state!" f))]
        (let [s (c/start (test-system))
              r (rio/set-relais-state! (:rio s) {:pinName "09" :pinState "HIGH"})
              rule (am/set-rule! (:am s) sample-rule-persist)
              _ (am/apply-rules! (:am s))
              result (rio/single-relais-info (:rio s) "09")]
          (is (= (:pinState result) "HIGH")))))

    (testing "a activation-manager component should apply rules by schedule"

      (with-redefs [tm/get-Measurement (fn [self] {:temperature 18 :humidity 89})
                    u/persist-states! (fn [self f] (println "mocked persist-state!" f))
                    am/apply-rules! (fn [self] (println "foo"))]
        (let [s (c/start (test-system))
              r (rio/set-relais-state! (:rio s) {:pinName "09" :pinState "HIGH"})
              rule (am/set-rule! (:am s) sample-rule-persist)
              _ (am/apply-rules! (:am s))

              result (rio/single-relais-info (:rio s) "09")]
          (is (= (:pinState result) "HIGH")))))

    (testing "a activation-manager component should be able to return its active rule"

      (with-redefs [tm/get-Measurement (fn [self] {:temperature 18 :humidity 89})
                    u/persist-states! (fn [self f] (println "mocked persist-state!" f))
                    am/apply-rules! (fn [self] (println "foo"))]
        (let [s (c/start (test-system))
              r (rio/set-relais-state! (:rio s) {:pinName "09" :pinState "HIGH"})
              rule (am/set-rule! (:am s) sample-rule-persist)
              result (str (:rule (first (am/get-activation-rules (:am s)))))]

          (is (= result "(fn [m] (if (< (:temperature m) 20) :high :low))"))))))

  )


(deftest test-contains-isolation
  (testing "should reduce multiple results"
    (let [default-fn '(fn [x] x)
          m {0 {:time {:from "00:00" :to "23:59"} :rule default-fn :id 0 :position 0}}
          id 0
          result (contains? m id)]
      (is (= result true))
      )))

(deftest test-pin
  (testing "unknown pin -> nil")
  (let [s (c/start (test-system))
        r (pin/get-pin (:rio s) "baz")]
    (is (nil? r))))

;(deftest test-python-dht22
;  (testing "call adafruit lib"
;    (let [python-script-path (u/to-known-path "dht-22-sample-mock.py")
;          _ (log/info "---> " python-script-path)
;          s (c/start (test-system python-script-path))
;          _ (Thread/sleep 2002)
;          result (tm/get-Measurement (:tm s))]
;      (is (= (:temperature result) 12.12))
;      (is (= (:humidity result) 7.07)))))
