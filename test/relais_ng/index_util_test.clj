(ns relais-ng.index-util-test
  (:require
    [clojure.test :refer :all]
    [relais-ng.index-util :as iu]
    ))
(deftest index-utils
  (testing "should accept valid index"
    (is (= 2 (iu/valid-index :i {:i 2} (vals {1 {:id 1 :i 1}})))))
  (testing "should replace duplicate index"
    (is (= 2 (iu/valid-index :i {:i 1} (vals {1 {:id 5 :i 1}})))))
  (testing "should return new index when none given"
    (is (= 2 (iu/valid-index :i {} (vals {1 {:id 5 :i 1}})))))
  )



