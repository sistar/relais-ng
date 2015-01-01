(ns relais-ng.raspi-io-test
  (:require
    [clojure.test :refer :all]
    [relais-ng.raspi-io :as rio]
    [com.stuartsierra.component :as c]))

(defn test-system []
  (->
    (c/system-map
      :settings { :state (atom{ :state-store "/tmp/heat-state.clj"})}
      :rio (c/using
             (rio/create-rio-mock) [:settings]))))

(deftest test-raspi-io
  (testing "initializing pin state"
    (let [s (c/start-system (test-system))
          r (rio/set-relais-state! (:rio s) {:pinName "00" :pinState "low"})
          result-state-zero (rio/get-state (:rio s) "00")]
      (is (= result-state-zero "LOW"))))

  (testing "getting unkown pin returns nil"
    (let [s (c/start-system (test-system))
          result-state-zero (rio/get-state (:rio s) "07A")]
      (is (= result-state-zero nil)))))

