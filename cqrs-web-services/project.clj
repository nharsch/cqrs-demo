(defproject cwars-web-services "0.1.0-SNAPSHOT"
  :description "web service layer for a CQRS app"
  :plugins [[lein-ring "0.12.6"]]
  :ring {:handler cqrs-web-services.core.endpoints/handler}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "1.5.648"]
                 [liberator "0.15.1"]
                 [compojure "1.6.0"]
                 [clj-http "3.12.3"]
                 ;; [com.fzakaria/slf4j-timbre "0.2"] ;; to avoid wierd ns errors: https://github.com/yogthos/migratus/issues/45
                 [com.appsflyer/ketu "0.6.0"]
                 [ring/ring-core "1.6.3"]])
