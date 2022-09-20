(ns cqrs-web-services.processor
  (:gen-class)
  (:require
   [ketu.async.source :as source]
   [ketu.async.sink :as sink]
   [datahike.api :as d]
   [clojure.core.async :as a]))

;; TODO: is there any way to better manage this?
(def cfg {:store {:backend :file :path "./db-data"}}) ;; TODO: env var

;; (d/create-database cfg) ;; TODO: how to handle already exists?
;; (d/delete-database cfg)

(defonce conn (d/connect cfg))

  ;; set up schema
  ;; TODO: handle somewhere else?
(d/transact conn [;; users
                  {:db/ident :user/name
                   :db/valueType :db.type/string
                   :db/unique :db.unique/value
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
  {:error "unexpected-command" :data cmd})

(handle-command! {:command "nope"})


(defn verify-new-email [email]
  (empty?
   (d/q '[:find ?id
          :in $ [?email]
          :where [?id :user/email ?email]]
        @conn
        [email])))
;; (verify-new-email "test@test.com")
;; (verify-new-email "test@talsdkfjlsdkfest.com")

(defn verify-new-username [username]
  (empty?
   (d/q '[:find ?id
          :in $ [?username]
          :where [?id :user/name ?username]]
        @conn
        [username])))
;; (verify-new-username "test")
;; (verify-new-username "bass")


(defmethod handle-command! "create-user" [cmd]
  (let [username (get-in cmd [:data :name])
        email (get-in cmd [:data :email])
        tx {:tx-data [{:user/name username :user/email email}]
            }]
    ;; TODO: there has to be a cleaner way to write these validation steps
    (cond (verify-new-username username)
          (cond (verify-new-email email)
                (try {:event "user-created" :commmand cmd :tx-data (d/transact conn tx)}
                     (catch Exception e {:error (.getMessage e) :data cmd}))
                :else {:error "email already exists" :data cmd})
          :else {:error "username already exists" :data cmd})))

;; (handle-command! {:command "create-user" :data {}}) ;; error
;; (handle-command! {:command "create-user" :data {:name "alsdfkjd" :email "test@test.com"}}) ;; should be idempotent
;; (handle-command! {:command "create-user" :data {:name "another-test" :email "test@another.com"}})
;; (handle-command! {:command "create-user" :data {:name "aasdfllllllll" :email "als@test.com"}}) ;; should be idempotent

;; (d/q '[:find ?id ?name ?email
;;        :where
;;        [?id :user/name ?name]
;;        [(re-find #"another" ?name)]
;;        [?id :user/email ?email]
;;        ]
;;      @conn)

(defn verify-new-channel-name [channel-name]
  (empty?
   (d/q '[:find ?id
          :in $ [?channel-name]
          :where [?id :channel/name ?channel-name]]
        @conn
        [channel-name])))

(defmethod handle-command! "create-channel" [cmd]
  (let [channel-name (get-in cmd [:data :name])
        tx {:tx-data [{:channel/name channel-name}]}]
    (cond (verify-new-channel-name channel-name)
          (try
            {:event "channel created" :command cmd :tx-data (d/transact conn tx)}
            (catch Exception e {:error (.getMessage e) :data cmd}))
          :else {:error "channel already exists" :data cmd})))
;; (handle-command! {:command "create-channel" :data {:name "default"}})
;; (handle-command! {:command "create-channel" :data {:name "default"}})
;; (handle-command! {:command "create-channel" :data {:name "cool stuff"}})
;; (d/q `[:find ?e :where [?e :channel/name "default"]] @conn)
;;

(defmethod handle-command! "subscribe-to-channel" [cmd]
  (let [username (get-in cmd [:data :user/name])
        channel-name (get-in cmd [:data :channel/name])
        tx {:tx-data [{:db/id [:user/name username]
                       :user/subscribed-channels [:channel/name channel-name]}]}]
    (cond (verify-new-channel-name channel-name)
          {:error "channel does not exist" :data cmd}
          :else (try (d/transact conn tx)
                     (catch Exception e {:error (.getMessage e) :data cmd})))))
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


(defn process-command-value! [v >events >errors]
  (println (str "PROCESSOR: processing value: " v))
  (let [cmd (read-string v)
        res (assoc (handle-command! cmd) :event-id (str (java.util.UUID/randomUUID)))]
    ;; TODO: how to handle errors?
    (cond (:event res)
          (do
            (println (str "emitting event" res))
            (a/go (a/>! >events res)))
          (:error res)
          (do
            (println (str "emitting error: " res))
            (a/go (a/>! >errors res)))
          :else (throw (Exception. "process-command-value! did not result in event or error")))))

;; (process-command-value! (pr-str {:id 98089 :command "create-user" :data {:name "test" :email "test@test.com"}}))
;; (process-command-value! (pr-str {:id 98089 :command "subscribe-to-channel" :data {:user/name "test" :channel/name "cool stuff"}}))

(defn -main [& args]
  (let [kafka-servers (System/getenv "KAFKA_SERVERS")
        >failure (a/chan 10)
        failure (sink/sink >failure {:name "failure-producer"
                                     :brokers kafka-servers ;; TODO: make a config param
                                     :topic "failure" ;; TODO: config param
                                     :value-type :string
                                     :shape :value})
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
    (println "PROCESSOR: creating pending->accepted consumer pipeline")
    (a/<!! (a/go-loop []
                  (let [cmd (a/<! <pending)]
                    (process-command-value! cmd >accepted >failure))
                  (recur)))))

(comment
  (a/>!! >failure "comment")
  (a/<!! <failure)
  (source/stop! pending)
  (a/close! >accepted)
  (a/close! handle-pipe)
)
