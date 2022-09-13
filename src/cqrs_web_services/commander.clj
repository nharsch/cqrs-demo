(ns cqrs-web-services.commander
  (:gen-class)
  (:require
   [ketu.async.source :as source]
   [ketu.async.sink :as sink]
   [datahike.api :as d]
   [clojure.core.async :as a]))

;; TODO: is there any way to better manage this?
(def cfg {:store {:backend :file :path "./db-data"}}) ;; TODO: env var
(d/create-database cfg)
;; (d/delete-database cfg)
(defonce conn (d/connect cfg))

  ;; set up schema
(d/transact conn [{:db/ident :team/name
                   :db/valueType :db.type/string
                   :db/unique :db.unique/identity
                   :db/cardinality :db.cardinality/one }
                  ;; users
                  {:db/ident :user/name
                   :db/valueType :db.type/string
                   :db/unique :db.unique/identity
                   :db/cardinality :db.cardinality/one }
                  {:db/ident :user/email
                   :db/valueType :db.type/string ;; is there an email type validation?
                   :db/unique :db.unique/value
                   :db/cardinality :db.cardinality/one }
                  ;; channels
                  {:db/ident :channel/name
                   :db/valueType :db.type/string
                   :db/unique :db.unique/identity
                   :db/cardinality :db.cardinality/one }
                  {:db/ident :user/subscribed-channels
                   :db/valueType :db.type/ref
                   :db/cardinality :db.cardinality/many }
                  ])


(defmulti handle-command!
  (fn [cmd] (:command cmd)))

(defmethod handle-command! :default [cmd]
  (println (str "unexpected command, " cmd)))
;; (handle-command! {:command "nope"})


(defmethod handle-command! "create-user" [cmd]
  (let [username (get-in cmd [:data :name])
        email (get-in cmd [:data :email])
        tx {:tx-data [{:user/name username :user/email email}]}]
    (cond (and username email)
          (d/transact conn tx)
          :else (throw (Throwable. "create-user requires :data/name and :data/email keys"))))) ;; TODO: use spec instead?
;; (handle-command! {:command "create-user" :data {}}) ;; error
(handle-command! {:command "create-user" :data {:name "test" :email "test@test.com"}}) ;; should be idempotent


;; (d/q '[:find ?email
;;        :where
;;        [_ :user/name "test"]
;;        [_ :user/email ?email]
;;        ]
;;      @conn)


(defmethod handle-command! "create-team" [cmd]
  (let [teamname (:name cmd)
        tx {:tx-data [{:name teamname}]}]
    (d/transact conn tx)))
;; (handle-command! {:command "create-team" :data {:name "test"}})

(defmethod handle-command! "create-channel" [cmd]
  (let [channel-name (get-in cmd [:data :name])
        tx {:tx-data [{:channel/name channel-name}]}]
    (cond (and channel-name)
          (d/transact conn tx)
          :else (throw (Throwable. "create-user requires :data/name key")))))
;; (handle-command! {:command "create-channel" :data {:name "default"}})
;; (handle-command! {:command "create-channel" :data {:name "cool stuff"}})
;; (d/q `[:find ?e :where [?e :channel/name "default"]] @conn)

(defmethod handle-command! "subscribe-to-channel" [cmd]
  (let [username (get-in cmd [:data :user/name])
        channel-name (get-in cmd [:data :channel/name])
        tx {:tx-data [{:db/id [:user/name username]
                       :user/subscribed-channels [:channel/name channel-name]}]}]
    (d/transact conn tx)))
;; (handle-command! {:command "subscribe-to-channel" :data {:user/name "test" :channel/name "default"}} )
;; (handle-command! {:command "subscribe-to-channel" :data {:user/name "test" :channel/name "cool stuff"}} )
;; (handle-command! {:command "subscribe-to-channel" :data {:user/name "test" :channel/name "nope"}} ) ;; error

;; (d/q `[:find ?channel-name
;;        :where
;;        [?u :user/name "test"]
;;        [?u :user/subscribed-channels ?c]
;;        [?c :channel/name ?channel-name]
;;        ] @conn)

(defmethod handle-command! "unsubscribe-to-channel" [cmd]
  (let [username (get-in cmd [:data :user/name])
        channel-name (get-in cmd [:data :channel/name])
        tx {:tx-data [[:db/retract [:user/name username]
                        :user/subscribed-channels [:channel/name channel-name]]]}]
    (d/transact conn tx)))
;; (handle-command! {:command "unsubscribe-to-channel" :data {:user/name "test" :channel/name "cool stuff"}} )
;; (d/q `[:find ?channel-name
;;        :where
;;        [?u :user/name "test"]
;;        [?u :user/subscribed-channels ?c]
;;        [?c :channel/name ?channel-name]
;;        ] @conn)


(defn process-command-value! [v]
  (println (str "COMMANDER: processing value: " v))
  (let [cmd (read-string v)
        tx-resp (handle-command! cmd)]
    ;; TODO: how to handle errors?
    (pr-str {:event-id (str (java.util.UUID/randomUUID)) ;; TODO: why the event ID here?
             :tx-data (:tx-data tx-resp)
             :parent (:id cmd)})))

;; (process-command-value! (pr-str {:id 98089 :command "create-user" :data {:name "test" :email "test@test.com"}}))
;; (process-command-value! (pr-str {:id 98089 :command "subscribe-to-channel" :data {:user/name "test" :channel/name "cool stuff"}}))

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
