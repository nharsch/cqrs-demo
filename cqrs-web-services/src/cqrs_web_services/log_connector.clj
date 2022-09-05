(ns cqrs-web-services.core.log-connector
  (:require
   [ketu.async.source :as source]
   [ketu.async.sink :as sink]
   [clojure.core.async :refer [chan close! <! <!! >! >!! put! take! go]]))

(defonce <accepted (chan 10))
(defonce accepted (source/source <accepted {:name "pending-consumer"
                                        :brokers "localhost:9093"
                                        :topic "pending" ;; TODO: config param
                                        :group-id "pending-consumers"
                                        :value-type :string
                                        :shape :value}))

(defonce >pending (chan 10))
(defonce pending (sink/sink >pending {:name "pending-producer"
                                      :brokers "localhost:9092" ;; TODO: make a config param
                                      :topic "accepted" ;; TODO: config param
                                      :value-type :string
                                      :shape :value}))

(defn add-to-pending! [v]
  (go
    (>! >pending v)))
