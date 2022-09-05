(require '[clj-http.client :as client])

(:body (client/get "http://localhost:3000"))

(:body (client/post "http://localhost:3000/commands"
                    {:content-type :edn
                     :accept :edn
                     :body (pr-str {:data {:command "hello lasdkjf world"}})}))
