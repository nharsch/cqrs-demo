(defproject cwars-web-services "0.1.0-SNAPSHOT"
  :description "web service layer for a CQRS app"
  :plugins [[lein-ring "0.12.6"]
            [reifyhealth/lein-git-down "0.4.1"]]
  :ring {:handler cqrs-web-services.rest/handler
         :async? true}
  :source-paths ["src"]
  :middleware [lein-git-down.plugin/inject-properties]
  :repositories [["public-github" {:url "git://github.com"}]]
  :git-down {ring-sse {:coordinates bobby/ring-sse}}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "1.5.648"]
                 [liberator "0.15.1"]
                 [buddy/buddy-auth "3.0.1"]
                 [io.replikativ/datahike "0.5.1511"]
                 [compojure "1.6.0"]
                 [com.appsflyer/ketu "0.6.0"]
                 [ring-sse/ring-sse "master"]
                 [ring/ring-core "1.9.3"]]
  :javac-options     ["-target" "1.8" "-source" "1.8"]
  :profiles {:runtime       {:aot          :all
                             :omit-source  true}})
