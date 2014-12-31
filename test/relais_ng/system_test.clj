(ns relais-ng.system-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as c]
            [relais-ng.components :as components]
            [relais-ng.handler :as handler]
            [relais-ng.pin :as pin]
            [relais-ng.main :as main]
            [relais-ng.raspi-io :as rio]
            [relais-ng.temperature-measurement :as tm]
            [relais-ng.activation-manager :as am]))

(defn test-system []
  (c/system-map
    :settings (relais-ng.settings/new-settings)
    :tm (c/using (tm/create-temp-measurement false) [:settings])
    :rio (c/using (rio/create-rio-mock) [:settings])
    :am (c/using (am/activation-manager-component) [:rio :tm :settings])))

(deftest test-rpio
  (testing "a rio component stores pin state"
    (with-redefs [rio/persist-states! (fn [self]
                                        (println "mocked persist-state!")
                                        )]
      (let [s (c/start (test-system))
            r (rio/set-relais-state (:rio s) {:pinName "09" :pinState "LOW"})
            ]
        (is (= r {:pinName "09" :pinState "LOW"})))))
  (testing "unknown pin is returned as nil"
    (let [s (c/start (test-system))
          r (rio/single-relais-info (:rio s) "baz")
          ]
      (is (nil? r)))))

(deftest test-am
  (testing "a activation-manager component stores pin state"
    (let [s (c/start (test-system))
          r (rio/set-relais-state (:rio s) {:pinName "foo" :pinState "low"})
          ]
      (is (= r {:pinName "foo" :pinState "low"}))))
  (testing "unknown pin is returned as nil"
    (let [s (c/start (test-system))
          r (rio/single-relais-info (:rio s) "baz")
          ]
      (is (nil? r)))))



(deftest test-pin
  (testing "unknown pin -> nil")
  (let [s (c/start (test-system))
        r (pin/get-pin (:rio s) "baz")]
    (is (nil? r))))

(deftest test-python-dht22
  (testing "call adafruit lib"
    (let [s (c/start (test-system))
          result (tm/get-Measurement (:tm s))]
      (is (= (:temperature result) 12.12))
      (is (= (:humidity result) 7.07)))))
