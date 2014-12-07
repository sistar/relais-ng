(ns relais-ng.pin
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [relais-ng.raspi_io :refer :all]
            [clojure.tools.logging :as log]))
(s/defschema Pin {:pinName String :pinState String})

(defn get-pins [rio] :- [Pin]
  (relais-info rio ))

(defn get-pin [rio id] :- Pin
  (single-relais-info rio id ))

(defn set-pin [rio pin] :- Pin
  (set-relais-state rio pin ))

