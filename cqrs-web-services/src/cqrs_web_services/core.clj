(ns cqrs-web-services.core
  (:require [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [defroutes ANY]]))


(defroutes app
  (ANY "/" [] (resource :available-media-types ["text/html"]
                        :handle-ok "<html>Hello world</html>")))

(def handler
  (-> app
      wrap-params ;; wraps the app with request params
      ))
