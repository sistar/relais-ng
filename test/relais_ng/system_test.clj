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
            [clojure.tools.logging :as log]))

(defn test-system [& args]
  (c/system-map
    :settings (relais-ng.settings/new-settings (atom {:apply-rules false :state-store "/tmp/t-store.clj" :measure-script ( or (first args) "/foo") :rule-store "/tmp/r-store.clj"}))
    :tm (c/using (tm/create-temp-measurement false) [:settings])
    :rio (c/using (rio/create-rio-mock) [:settings])
    :am (c/using (am/activation-manager-component [:rio :tm :settings]) [:rio :tm :settings])))

(deftest test-rpio
  (testing "a rio component stores pin state"
    (with-redefs [rio/persist-states! (fn [self]
                                        (println "mocked persist-state!")
                                        )]
      (let [s (c/start (test-system))
            r (rio/set-relais-state! (:rio s) {:pinName "09" :pinState "LOW"})
            ]
        (is (= r {:pinName "09" :pinState "LOW"})))))
  (testing "unknown pin is returned as nil"
    (let [s (c/start (test-system))
          r (rio/single-relais-info (:rio s) "baz")
          ]
      (is (nil? r)))))

(deftest test-am
  (let [sample-rule (read-string "{:time {:from \"00:00\" :to \"23:59\"} :rule (fn [m] (if (< (:temperature m) 20) :high :low))}")]
    (testing "a activation-manager component should calculate activation..."
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 21 :humidity 89})]
        (let [s (c/start (test-system))
              rule (am/set-rule! (:am s) sample-rule)
              result (am/calc-rule (:am s))]

          (is (= result :low)))))

    (testing "a activation-manager component should do nothing without pins..."
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 21 :humidity 89})
                    rio/persist-states! (fn [self]
                                          (println "mocked persist-state!")
                                          )]
        (let [s (c/start (test-system))
              rule (am/set-rule! (:am s) sample-rule)
              result (am/apply-rule! (:am s))]
          (is (= 1 1)))))

    (testing "a activation-manager component should do settings on pins..."
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 21 :humidity 89})
                    rio/persist-states! (fn [self]
                                          (println "mocked persist-state!")
                                          )]
        (let [s (c/start (test-system))
              r (rio/set-relais-state! (:rio s) {:pinName "09" :pinState "HIGH"})
              rule (am/set-rule! (:am s) sample-rule)

              _ (am/apply-rule! (:am s))
              result (rio/single-relais-info (:rio s) "09")]
          (is (= (:pinState result) "LOW")))))

    (testing "a activation-manager component should do settings on pins..."
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 18 :humidity 89})
                    rio/persist-states! (fn [self]
                                          (println "mocked persist-state!")
                                          )]
        (let [s (c/start (test-system))
              r (rio/set-relais-state! (:rio s) {:pinName "09" :pinState "HIGH"})
              rule (am/set-rule! (:am s) sample-rule)
              _ (am/apply-rule! (:am s))
              result (rio/single-relais-info (:rio s) "09")]
          (is (= (:pinState result) "HIGH")))))

    (testing "a activation-manager component should apply rules by schedule"

      (with-redefs [tm/get-Measurement (fn [self] {:temperature 18 :humidity 89})
                    rio/persist-states! (fn [self]
                                          (println "mocked persist-state!")

                                          )
                    am/apply-rule! (fn [self] (println "foo"))]
        (let [s (c/start (test-system))
              r (rio/set-relais-state! (:rio s) {:pinName "09" :pinState "HIGH"})
              rule (am/set-rule! (:am s) sample-rule)
              _ (am/apply-rule! (:am s))

              result (rio/single-relais-info (:rio s) "09")]
          (is (= (:pinState result) "HIGH")))))

    (testing "a activation-manager component should be able to return its active rule"

      (with-redefs [tm/get-Measurement (fn [self] {:temperature 18 :humidity 89})
                    rio/persist-states! (fn [self]
                                          (println "mocked persist-state!")

                                          )
                    am/apply-rule! (fn [self] (println "foo"))]
        (let [s (c/start (test-system))
              r (rio/set-relais-state! (:rio s) {:pinName "09" :pinState "HIGH"})
              rule (am/set-rule! (:am s) sample-rule)
              result (str(:rule(am/get-rule (:am s))))]

          (is (= result "(fn [m] (if (< (:temperature m) 20) :high :low))")))))))



(deftest test-pin
  (testing "unknown pin -> nil")
  (let [s (c/start (test-system))
        r (pin/get-pin (:rio s) "baz")]
    (is (nil? r))))

(deftest test-python-dht22
  (testing "call adafruit lib"
    (let [python-script-path (u/to-known-path "dht-22-sample-mock.py")
          _ (log/info "---> " python-script-path)
          s (c/start (test-system python-script-path ))
          result (tm/get-Measurement (:tm s))]
      (is (= (:temperature result) 12.12))
      (is (= (:humidity result) 7.07)))))
