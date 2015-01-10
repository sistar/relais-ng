(ns relais-ng.activation-manager-test
  (:require [clojure.test :refer :all]
            [relais-ng.activation-manager :as am]
            [relais-ng.utils :as u]
            [relais-ng.temperature-measurement :as tm]))

(deftest activation-manager
  (testing "should set rule in ref"
    (with-redefs [u/persist-states! (fn [self f] println "would save.." f)]
      (let [self {:activation-rules (ref {})}
            r {:rule '(fn [m] (if (> (:temperature m) 15) :low :high))}
            _ (am/set-rule! self r)
            state-entry (first (vals @(:activation-rules self)))]
        (is (= (:position state-entry) 0))
        (is (= (:time state-entry) {:from "00:00:00" :to "23:59:59"}))
        (is (= (count (:id state-entry)) 36))
        (is (= (:id state-entry)  (first (keys @(:activation-rules self))))))))
  (testing "should use rule for calc"
    (with-redefs [u/persist-states! (fn [self f] println "would save.." f)
                  tm/get-Measurement (fn [self] {:temperature 16})]
      (let [self {:activation-rules (ref {})}
            r {:rule '(fn [m] (if (> (:temperature m) 15) :low :high))}
            _ (am/set-rule! self r)
            rule-id (first (keys @(:activation-rules self)))
            result (am/calc-rule self rule-id )]
        (is (= result :low))))))
