(ns relais-ng.settings-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as c]
            [relais-ng.components :as components]
            [relais-ng.handler :as handler]
            [relais-ng.pin :as pin]
            [relais-ng.main :as main]
            [relais-ng.settings :as s]
            [relais-ng.temperature-measurement :as tm]
            [relais-ng.activation-manager :as am]))

(defn test-system []
  (c/system-map
    :settings (s/new-settings (atom{:apply-rules false}))))

(deftest settings
  (testing "bla"
    (let [sys (c/start-system (test-system))
          r (s/get-setting (:settings sys) :apply-rules)]
      (is (= r false)))))
