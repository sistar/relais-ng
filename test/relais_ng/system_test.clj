(ns relais-ng.system-test
  (:require [clojure.test :refer :all]
            [relais-ng.components :as components]
            [relais-ng.handler :as handler]
            [relais-ng.pin :as pin]
            [relais-ng.main :as main]
            [relais-ng.raspi_io :as rio]
            [com.stuartsierra.component :as c]
            [relais-ng.raspi_io :as rio]))

(defn test-system []
  (->
    (c/system-map
      :rio (rio/create-rio)
      )))

(deftest test-rpio
  (testing "a rio componets stores pin state"
    (let [s (c/start (test-system))
          r (rio/set-relais-state (:rio s) {:pinName "foo" :pinState "low"})
          ]
      (is (= r {:pinName "foo" :pinState "low"}))
      )
    )
  (testing "unknown pin is returned as nil"
    (let [s (c/start (test-system))
          r (rio/single-relais-info (:rio s) "baz")
          ]
      (is (nil? r))
      )
    )
  )
(deftest test-pin
  (testing "unknown pin -> nil")
  (let [s (c/start (test-system))
        r (pin/get-pin (:rio s) "baz")]
    (is (nil? r))
    ))