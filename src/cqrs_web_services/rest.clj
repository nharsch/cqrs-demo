(ns cqrs-web-services.rest
  (:require [clojure.core.async :as a]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.auth.accessrules :refer (success error wrap-access-rules)]
            [compojure.core :refer [defroutes ANY POST GET]]
            [ketu.async.source :as source]
            [ketu.async.sink :as sink]
            [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.sse :as sse]))


;; TODO: should we open these channels per request?
(defonce <accepted (a/chan 10))
(defonce accepted (source/source <accepted {:name "accepted-consumer"
                                            :brokers "localhost:29092,localhost:29093"
                                            :topic "accepted" ;; TODO: config param
                                            :group-id "accepted-consumers"
                                            :auto-offset-reset "earliest"
                                            :value-type :string
                                            :shape :value}))

(defonce >pending (a/chan 10))
(defonce pending (sink/sink >pending {:name "pending-producer"
                                      :brokers "localhost:29092,localhost:29093" ;; TODO: make a config param
                                      :topic "pending" ;; TODO: config param
                                      :value-type :string
                                      :shape :value}))
(def <failure (a/chan 10))
(def failure-src (source/source <failure {:name "failure-producer"
                                          :brokers "localhost:29092,localhost:29093" ;; TODO: make a config param
                                          :group-id "failure-consumers"
                                          :topic "failure"       ;; TODO: config param
                                          :value-type :string
                                          :shape :value}))

(def backend (backends/session))


(defroutes app
  (ANY "/" []
       ;; TODO: render FE app
       (fn [request respond raise]
         (respond {:status 200
                   :headers {}
                   :available-media-types ["text/html"]
                   :body "<html>Hello world...</html>"})))

  (ANY "/commands" []
       ;; TODO: make sync version
     (fn [request respond raise]
       ;; TODO: return 202
       (respond
        ((resource
          :allowed-methods [:post]
          :available-media-types ["application/edn"]
          :handle-created (fn [ctx] (format (:command ctx)))
          :post! (fn [ctx] ;; TODO: move out of handler body
                   (dosync
                    ;; TODO: make function
                    (let [command (-> (slurp (get-in ctx [:request :body]))
                                      read-string
                                      (assoc :id (str (java.util.UUID/randomUUID))) ;; just add an ID
                                      pr-str)]
                      ;; TODO: validate with schema
                      (a/go (a/>! >pending command))
                      (println "sending back command: " command)
                      {:command command}
                      ))))
         request))))

  (GET "/accepted" []
       (sse/event-channel-handler
        (fn [request respond raise event-ch]
          (a/go-loop []
            (let [acc (a/<! <accepted)
                  ess-msg {:data acc}] ;; TODO: get any more info from kafka chan, like timestamp?
              (a/>! event-ch ess-msg)
              (a/<! (a/timeout 300)))
            (recur)))
        {:on-client-disconnect #(println "client-disconnect" %)}))

  (GET "/errors" []
       (sse/event-channel-handler
        (fn [request respond raise event-ch]
          (a/go-loop []
            (let [err (a/<! <failure)
                  ess-msg {:data err}] ;; TODO: get any more info from kafka chan, like timestamp?
              (a/>! event-ch ess-msg)
              (a/<! (a/timeout 300)))
            (recur)))
        {:on-client-disconnect #(println "client-disconnect" %)})))

(comment
  (-> req-command
      read-string
      (assoc :id (str (java.util.UUID/randomUUID)))
      pr-str))

(defn authenticated-access
  [request]
  (if (:identity request)
    true
    (error "Only authenticated users allowed")))

(def rules [{:pattern #"^/.+"
             :handler authenticated-access}])

(defn on-error
  [request value]
  {:status 403
   :headers {}
   :body "Not authorized"})

;; (def handler app)
(def handler
  (-> app
      wrap-params ;; wraps the app with request params
      (wrap-authentication backend)
      (wrap-authorization backend)
      (wrap-access-rules {:rules rules :on-error on-error})
      ))
