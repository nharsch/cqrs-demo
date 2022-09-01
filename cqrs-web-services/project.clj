(defproject cwars-web-services "0.1.0-SNAPSHOT"
  :description "web service layer for a CQRS app"
  :plugins [[lein-ring "0.12.6"]]
  :ring {:handler cqrs-web-services.core/handler}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [liberator "0.15.1"]
                 [compojure "1.6.0"]
                 [ring/ring-core "1.6.3"]])
