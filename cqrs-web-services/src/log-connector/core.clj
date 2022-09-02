(ns cqrs-web-services.core
  (:require
   [clojure.core.async :refer [chan close! <!! >!!]]
   [ketu.async.source :as source]
   [ketu.async.sink :as sink]))

(let [sink-opts {:name "greeter-producer"
                 :brokers "localhost:29092" ;; TODO: make a config param
                 :topic "pending"  ;; TODO: config param
                 :value-type :string
                 :shape :value}
      >pending (chan 10)
      pending (sink/sink >pending sink-opts)
      source-opts {:name "greeter-consumer"
                   :brokers "broker1:9092"
                   :topic "accepted" ;; TODO: config param
                   :value-type :string
                   :shape :value}
      <accepted (chan 10)
      accepted (source/source <accepted source-opts)
      ]

  (>!! >pending "test")
  ;; (<!! >pending)
  )
