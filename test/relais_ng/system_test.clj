(ns relais-ng.system-test
  (:require [clojure.test :refer :all]
            [relais-ng.components :as components]
            [relais-ng.handler :as handler]
            [relais-ng.pin :as pin]
            [relais-ng.main :as main]
            [relais-ng.raspi-io :as rio]
            [relais-ng.temperature-measurement :as tm]
            [com.stuartsierra.component :as c]
            ))

(defn test-system []
  (->
    (c/system-map
      :tm (tm/create-temp-measurement false)
      :rio (rio/create-rio-mock))))

(deftest test-rpio
  (testing "a rio componets stores pin state"
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
