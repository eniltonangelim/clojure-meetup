(ns clojure-sse-meetup.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.sse :as sse]
            [io.pedestal.http.jetty.websockets :as ws]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [io.pedestal.log :as log]
            [clojure.core.async :as async]
            [clojure.java.jmx :as jmx]))

;; Armazena a sessao do cliente usando Atom
(def ws-clients (atom {}))
(def ws-clients-messages (atom {:qtd 0}))

(defn send-and-close!
  [message]
    (let [[ws-session send-ch] (first @ws-clients)]
      (async/put! send-ch message)
      (async/close! send-ch)
      (swap! ws-clients dissoc ws-session)
      (log/info :msg 
        (str "Active Connections: " (count @ws-clients)))))

(defn send-message-to-all!
  [message]
    (doseq 
      [[^org.eclipse.jetty.websocket.api.Session session channel] @ws-clients]
        (when (.isOpen session)
          (async/put! channel message))))

(defn meetup-ws-client
  "Mantem todas as sessoes dos clientes"
  [ws-session send-ch]
    (async/put! send-ch "Bem vindo!")
    (swap! ws-clients assoc ws-session send-ch))

(def meetup-ws-paths
  {"/chat" {
    :on-connect (ws/start-ws-connection meetup-ws-client)
    :on-text (fn [msg] 
               (log/info :msg (str "Client: " msg))
               (swap! ws-clients-messages update :qtd inc)
               (send-message-to-all! msg))
    :on-error (fn [t] 
                (log/error :msg "WS Error happened" :exception t))
    :on-close (fn [num-code reason-text] 
                (log/info :msg "WS Closed:" :reason reason-text))
  }})

(defn meetup-sse
  [event-ch context]
    (loop []
      (async/put!
        event-ch {:name "Status chat"
                  :data 
                    {:message (str "Quantidade de mensagens "
                               (get @ws-clients-messages :qtd))
                     :client (str "Quantidade de clientes " 
                               (count @ws-clients))
                     :server-memory (jmx/read "java.lang:type=Memory" 
                                      [:HeapMemoryUsage])}})
      (Thread/sleep 5000)
      (recur)))

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body])

;; Tabular routes
(def routes #{["/events" :get [(sse/start-event-stream meetup-sse)]]})

;; Map-based routes
;(def routes `{"/" {:interceptors [(body-params/body-params) http/html-body]
;                   :get home-page
;                   "/about" {:get about-page}}})

;; Terse/Vector-based routes
;(def routes
;  `[[["/" {:get home-page}
;      ^:interceptors [(body-params/body-params) http/html-body]
;      ["/about" {:get about-page}]]]])


;; Consumed by clojure-sse-meetup.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Tune the Secure Headers
              ;; and specifically the Content Security Policy appropriate to your service/application
              ;; For more information, see: https://content-security-policy.com/
              ;;   See also: https://github.com/pedestal/pedestal/issues/499
              ;;::http/secure-headers {:content-security-policy-settings {:object-src "'none'"
              ;;                                                          :script-src "'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:"
              ;;                                                          :frame-ancestors "'none'"}}

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ;;  This can also be your own chain provider/server-fn -- http://pedestal.io/reference/architecture-overview#_chain_provider
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 8080
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false
                                        :context-configurator 
                                          #(ws/add-ws-endpoints % meetup-ws-paths)}})

