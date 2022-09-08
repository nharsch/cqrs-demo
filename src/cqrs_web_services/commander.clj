(ns cqrs-web-services.commander
  (:gen-class)
  (:require
   [ketu.async.source :as source]
   [ketu.async.sink :as sink]
   [datahike.api :as d]
   [clojure.core.async :as a]))

;; TODO: is there any way to better manage this?
(def cfg {:store {:backend :file :path "./db-data"}}) ;; TODO: env var
;; (d/create-database cfg)
(defonce conn (d/connect cfg))
  ;; set up schema
(d/transact conn [{:db/ident :team/name
                   :db/valueType :db.type/string
                   :db/cardinality :db.cardinality/one }
                  {:db/ident :user/name
                   :db/valueType :db.type/string
                   :db/cardinality :db.cardinality/one }])

;; domain commands
;; TODO: create command dispatcher
(defn create-user! [username]
  (let [tx {:tx-data [{:user/name username}]}]
       (d/transact conn tx)))
;; (create-user! "test")

(defn create-team! [teamname]
  (let [tx {:tx-data [{:team/name teamname}]}]
    (d/transact conn tx)))

;; (:tx-data (create-team! "test"))

;; (d/q '[:find ?e
;;        :where [?e :user/name "test"]]
;;      @conn)
;; (d/q '[:find ?e
;;        :where [?e :team/name "test"]]
;;      @conn)

(defn handle-command! [cmd]
  (condp = (:command  cmd)
    "create-user" (create-user! (get-in cmd [:data :username]))
    "create-team" (create-team! (get-in cmd [:data :team-name]))
    (println (str "unexpected command, " cmd))))

;; (handle-command! {:command "create-user" :data {:username "test"}})

(defn process-command-value! [v]
  (println (str "COMMANDER: processing value: " v))
  (let [cmd (read-string v)
        tx-resp (handle-command! cmd)]
    (pr-str {:event-id (str (java.util.UUID/randomUUID)) ;; TODO: why the event ID here?
             :tx-data (:tx-data tx-resp)
             :parent (:id cmd)})))

;; (process-command-value! (pr-str {:id 98089 :command "create-user" :data {:username "test"}}))

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
    ;; TODO: how to route errors
    (a/pipeline 1                      ; threads
                >accepted              ; to ch
                (map process-command-value!)
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
