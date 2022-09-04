(ns cqrs-web-services.core.endpoints
  (:require [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.sse :as sse]
            [compojure.core :refer [defroutes ANY POST GET]]
            [cqrs-web-services.core.log-connector :refer [add-to-pending! <accepted]]
            [clojure.core.async :as a]
            [clojure.core.async :refer [chan go]]))



(def test-async-handler
  (sse/event-channel-handler
   (fn [request response raise event-ch]
     (a/pipe <accepted event-ch)
     )
   {:on-client-disconnect (fn [] (println "client-disconnect"))}
   ))


(defroutes app
  (ANY "/" [] (fn [request respond raise]
                (respond ((resource :available-media-types ["text/html"]
                                     :handle-ok "<html>Hello world</html>")
                          request))))
  (ANY "/commands" []
       (fn [request respond raise]
         (respond
          ((resource
            :allowed-methods [:post]
            :available-media-types ["application/edn"]
            :handle-ok (fn [ctx] (format (pr-str {:response "Command sent"})))
            :post! (fn [ctx] ;; TODO: move out of handler body
                     (let [command
                           (slurp (get-in ctx [:request :body]))]
                       ;; TODO: validate with schema
                       (dosync
                         (println command)
                         (add-to-pending! (pr-str {:data command})))
                       )))
           request))))
  (GET "/accepted" [] test-async-handler)
)

(def handler
  (-> app
      wrap-params ;; wraps the app with request params
      ))
