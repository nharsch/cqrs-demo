(ns cqrs-web-services.commander
  (:require
   [ketu.async.source :as source]
   [ketu.async.sink :as sink]
   [clojure.core.async :as a]))

(defonce <pending (a/chan 10))
(defonce pending (source/source <pending {:name "pending-consumer"
                                          :brokers "localhost:9093"
                                          :topic "pending" ;; TODO: config param
                                          :group-id "pending-consumers"
                                          :value-type :string
                                          :shape :value}))

(defonce >accepted (a/chan 2))
(defonce accepted (sink/sink >accepted {:name "accepted-producer"
                                        :brokers "localhost:9093" ;; TODO: make a config param
                                        :topic "accepted" ;; TODO: config param
                                        :value-type :string
                                        :shape :value}))

(a/pipeline 1 ; threads
            >accepted  ; to ch
            (map (fn [v]
                   (let [msg {:event-id (str (java.util.UUID/randomUUID))
                              :parent v}]
                     (println msg)
                     msg)))
            <pending ; from ch
            false ; close when 'from' runs out?
            (fn [err] (println (.getMessage err))))
