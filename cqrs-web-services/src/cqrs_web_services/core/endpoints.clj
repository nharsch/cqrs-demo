(ns cqrs-web-services.core.endpoints
  (:require [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [defroutes ANY POST GET]]
            [cqrs-web-services.core.log-connector :refer [add-to-pending!]]
            [clojure.core.async :refer [chan go]]))


(defroutes app
  (ANY "/" [] (resource :available-media-types ["text/html"]
                        :handle-ok "<html>Hello world</html>"))
  (ANY "/commands" []
        (resource
         :allowed-methods [:post]
         :available-media-types ["application/edn"]
         :handle-ok (fn [ctx] (format (pr-str {:response "Command sent"})))
         :post! (fn [ctx] ;; TODO: move out of handler body
                  (let [command
                        (slurp (get-in ctx [:request :body]))]
                    ;; TODO: validate with schema
                    (add-to-pending! command)
                    {:body (str "Command sent: " command)}
                    )
                  )
         )
        ))

;; (add-to-pending! "do")
(def handler
  (-> app
      wrap-params ;; wraps the app with request params
      ))
