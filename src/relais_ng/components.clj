(ns relais-ng.components
  (:require [com.stuartsierra.component :as component]
            [compojure.api.sweet :refer :all]))

(defmethod compojure.api.meta/restructure-param :components
  [_ components acc]
  (update-in acc [:letks] into [components `(::components ~'+compojure-api-request+)]))

(defn wrap-components [handler components]
  (fn [req]
    (handler (assoc req ::components components))))

(defn make-handler
  "Wrap a ring handler (e.g. routes from defapi) into middleware which will
   assoc components to each request.

   Handler is used through a var so that changes to routes will take effect
   without restarting the system (e.g. re-evaulating the defapi form)"
  [component]
  (require 'relais-ng.handler)

  (let [deps (select-keys component (:deps component))]
    (-> (resolve 'relais-ng.handler/app)
        (wrap-components deps))))