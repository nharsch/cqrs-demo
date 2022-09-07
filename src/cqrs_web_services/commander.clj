(ns cqrs-web-services.commander
  (:gen-class)
  (:require
   [ketu.async.source :as source]
   [ketu.async.sink :as sink]
   [clojure.core.async :as a]))


(defn -main [& args]
  (let [kafka-servers (System/getenv "KAFKA_SERVERS")
        <pending (a/chan 10)
        pending (source/source <pending {:name "pending-consumer"
                                         :brokers kafka-servers
                                         :topic "pending" ;; TODO: config param
                                         :group-id "pending-consumers"
                                         :auto-offset-reset "earliest"
                                         :value-type :string
                                         :shape :value})
        >accepted (a/chan 10)
        accepted (sink/sink >accepted {:name "accepted-producer"
                                       :brokers kafka-servers ;; TODO: make a config param
                                       :topic "accepted" ;; TODO: config param
                                       :value-type :string
                                       :shape :value})]
    (println "COMMANDER: creating pending->accepted consumer pipeline")
    (a/pipeline 1                      ; threads
                >accepted              ; to ch
                (map (fn [v]
                       (let [msg (pr-str {:event-id (str (java.util.UUID/randomUUID))
                                          :parent (read-string v)})]  ;; TODO: user serialzier / spec
                         ;; (println msg)
                         msg)))
                <pending               ; from ch
                false                  ; close when 'from' runs out?
                (fn [err] (println (.getMessage err))))
    (loop [] (recur)) ; TODO: more graceful way to block?
    ))

(comment
  (dosync
   (a/>!! >accepted "comment")
   (source/stop! pending)
   (a/close! >accepted)
   (a/close! handle-pipe)
   ))
