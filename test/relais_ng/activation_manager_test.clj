(ns relais-ng.activation-manager-test
  (:require [clojure.test :refer :all]
            [relais-ng.activation-manager :as am]
            [relais-ng.utils :as u]
            [relais-ng.raspi-io :as rio]
            [relais-ng.temperature-measurement :as tm]))

(deftest activation-manager
  (testing "should delete rule"
    (with-redefs []
      (let [self {:activation-rules (ref {"abc" {:rule '(fn [m] (if (> (:temperature m) 15) :high :high))}
                                          "def" {:rule '(fn [m] (if (> (:temperature m) 15) :high :high))}})}
            result (am/delete-rule! self "abc")]
        (is (= (count (keys @(:activation-rules self))) 1)))))
  (testing "should reset all rules"
    (with-redefs []
      (let [self {:activation-rules (ref {"xyz" {:rule '(fn [m] (if (> (:temperature m) 15) :high :high))}
                                          "zyx" {:rule '(fn [m] (if (> (:temperature m) 15) :high :high))}})}
            result (am/reset-rules! self)]
        (is (= (count (keys @(:activation-rules self))) 0)))))
  (testing "should set rule in ref"
    (with-redefs [u/persist-states! (fn [self f] println "would save.." f)]
      (let [self {:activation-rules (ref {})}
            r {:rule '(fn [m] (if (> (:temperature m) 15) :low :high))}
            _ (am/set-rule! self r)
            state-entry (first (vals @(:activation-rules self)))]
        (is (= (:position state-entry) 0))
        (is (= (:time state-entry) {:from "00:00:00" :to "23:59:59"}))
        (is (= (count (:id state-entry)) 36))
        (is (= (:id state-entry) (first (keys @(:activation-rules self))))))))
  (testing "should use rule for calc"
    (with-redefs [u/persist-states! (fn [self f] println "would save.." f)
                  tm/get-Measurement (fn [self] {:temperature 16})]
      (let [self {:activation-rules (ref {})}
            r {:rule '(fn [m] (if (> (:temperature m) 15) :low :high))}
            _ (am/set-rule! self r)
            rule-id (first (keys @(:activation-rules self)))
            result (am/calc-rule self rule-id)]
        (is (= result :low))))))
(deftest applying-rules
  (testing "should apply rules to pins"
    (let [mock-state (ref [])]
      (with-redefs [tm/get-Measurement (fn [self] {:temperature 16})
                    rio/pin-names (fn [self] ["00", "05"])
                    rio/set-relais-state! (fn [self pin] (dosync (ref-set mock-state (conj @mock-state pin))))]
        (let [self {:activation-rules (ref {"xyz" {:rule '(fn [m] (if (> (:temperature m) 14) :low :high))}
                                            "zyx" {:rule '(fn [m] (if (> (:temperature m) 17) :low :high))}})}
              result (am/apply-rules! self)]
          (is (= @mock-state [{:pinName "00", :pinState :low} {:pinName "05", :pinState :low}])))))))