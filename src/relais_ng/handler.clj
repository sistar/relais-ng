(ns relais-ng.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [relais-ng.pin :refer :all]
            [relais-ng.raspi_io :refer :all]
            [clojure.tools.logging :as log]))

(defapi app
        (swagger-ui)
        (swagger-docs
          :title "Relais-ng")
        (swaggered "api"
                   :description "relais pins and their state"
                   (GET* "/relais" []
                         :return [Pin]
                         :summary "return all pin states"
                         :components [rio]

                         (ok (get-pins rio)))
                   (GET* "/relais/:id" []
                         :return Pin
                         :path-params [id :- String]
                         :summary "return pin state"
                         :components [rio]
                         :responses {404 [String]}
                         (let [p (get-pin rio id)]
                           (if (some? p)
                             (ok p)
                             (not-found {:message "not-found"})
                             )
                           )
                         )
                   (PUT* "/relais" []
                         :return Pin
                         :body [body Pin]
                         :summary "changes pin state"
                         :components [rio]
                         (ok (set-pin rio body)))
                   (POST* "/relais" []
                          :return Pin
                          :body [body Pin]
                          :summary "changes pin state"
                          :components [rio]
                          (ok (set-pin rio body))))
        (compojure.route/files "" {:root "public"})
        (compojure.route/resources "/")
        )