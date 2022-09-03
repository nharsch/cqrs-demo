(ns cqrs-web-services.core.endpoints
  (:require [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [defroutes ANY POST GET]]
            ;; [cqrs-web-services.core.log-connector]
            ))


(defroutes app
  (ANY "/" [] (resource :available-media-types ["text/html"]
                        :handle-ok "<html>Hello world</html>"))
  (ANY "/commands" []
        (resource
         :allowed-methods [:post]
         :available-media-types ["application/edn" "text/html"]
         :handle-ok "get"
         :post! (fn [ctx]
                  (let [body (slurp (get-in ctx [:request :body]))]
                    (println body)
                    body)
                  )
         )))

(def handler
  (-> app
      wrap-params ;; wraps the app with request params
      ))
