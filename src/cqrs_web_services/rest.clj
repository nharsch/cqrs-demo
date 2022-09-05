(ns cqrs-web-services.rest
  (:require [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [ketu.async.source :as source]
            [ketu.async.sink :as sink]
            [ring.sse :as sse]
            [compojure.core :refer [defroutes ANY POST GET]]
            [clojure.core.async :as a]))

(defonce <accepted (a/chan 10))
(defonce accepted (source/source <accepted {:name "accepted-consumer"
                                        :brokers "localhost:29093"
                                        :topic "accepted" ;; TODO: config param
                                        :group-id "accepted-consumers"
                                        :auto-offset-reset "earliest"
                                        :value-type :string
                                        :shape :value}))

(defonce >pending (a/chan 10))
(defonce pending (sink/sink >pending {:name "pending-producer"
                                      :brokers "localhost:29092" ;; TODO: make a config param
                                      :topic "pending" ;; TODO: config param
                                      :value-type :string
                                      :shape :value}))


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
                       (a/go (a/>! >pending command))
                       )))
           request))))
  (GET "/accepted" []
    (sse/event-channel-handler
        (fn [request response raise event-ch]
          (a/go-loop []
              (let [acc (a/<! <accepted)
                    ess-msg {:data acc}]
                (a/>! event-ch ess-msg)
                (a/<! (a/timeout 300))
                )
            (recur)))
        {:on-client-disconnect #(println "client-disconnect" %)})))

(def handler
  (-> app
      wrap-params ;; wraps the app with request params
      ))
