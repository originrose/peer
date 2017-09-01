(ns think.peer.net
  (:require
    [taoensso.timbre :as log]
    [chord.http-kit :refer [with-channel]]
    [chord.format :as cf]
    [hiccup.core :refer [html]]
    [hiccup.page :refer [include-css include-js]]
    [org.httpkit.server :as http-kit]
    [ring.middleware.resource :refer [wrap-resource]]
    [ring.middleware.file :refer [wrap-file]]
    [bidi.ring :refer [make-handler]]
    [clojure.core.async :refer [<! >! go go-loop] :as async]
    [think.peer.websocket-client :as ws-client]
    [think.peer.api :as api])
  (:import [org.fressian.handlers WriteHandler ReadHandler]
           [org.fressian.impl ByteBufferInputStream BytesOutputStream]))

(def DEFAULT-PEER-PORT 4242)

(defn mapply
  "Apply a map as keyword args to a function.
   e.g.
      (mapply foo {:a 2}) => (foo :a 2)
  "
  [f & args]
  (apply f (apply concat (butlast args) (last args))))

(defn run-handler
  [handler req]
  (let [msg-args (:args req)
        {:keys [arglists partial-args]} (meta handler)
        n (+ (count msg-args) (count partial-args))
        arg-counts (map count arglists)
        ok? (some #(= n %) arg-counts)
        optional? (first (filter #(= '& (first %)) arglists))]
    (cond
      ok? (apply handler msg-args)
      optional? (handler)
      ;; TODO: This is not surfacing from a System using think.peer...
      :default (throw (ex-info (str "Incorrect number of arguments passed to function: "
                                    n " for function " handler " with arglists " arglists)
                               {})))))

(defn event-handler
  [api req]
  (if-let [handler (get-in api [:event (:event req)])]
    (run-handler handler req)
    (log/info "Unhandled event: " req)))

(defn rpc-event-handler
  [api {:keys [chan id] :as req}]
  (go
    (if-let [handler (get-in api [:rpc (:fn req)])]
      (let [v (run-handler handler req)
            res (if (satisfies? clojure.core.async.impl.protocols/ReadPort v)
                  (<! v)
                  v)]
        (>! chan {:event :rpc-response :id id :result res}))
      (log/error "Unhandled rpc-request: " req))))

(defn subscription-event-handler
  [peers* peer-id api {:keys [chan id] :as req}]
  (go
    (if-let [handler (get-in api [:subscription (:fn req)])]
      (let [res (apply handler (:args req))
            res (if (map? res) res {:chan res})
            pub-chan (:chan res)]
        (if (satisfies? clojure.core.async.impl.protocols/ReadPort pub-chan)
          (let [event-wrapper (async/chan 1 (map (fn [v] {:event :publication :id id :value v})))]
            (async/pipe pub-chan event-wrapper)
            (async/pipe event-wrapper chan)
            (swap! peers* assoc-in [peer-id :subscriptions id] res))
          (throw (ex-info (str "Subscription function didn't return a publication channel:" req)
                          {}))))
      (throw (ex-info (str "Unhandled subscription request: " req)
                      {})))))

(defn unsubscription-event-handler
  [peers* peer-id {:keys [id] :as req}]
  (let [{:keys [chan stop]} (get-in @peers* [peer-id :subscriptions id])]
    (swap! peers* update-in [peer-id :subscriptions] dissoc id)
    (when (fn? stop)
      (stop))
    (async/close! chan)))

(defn disconnect-peer
  [peers* peer-id]
  (let [peer (get @peers* peer-id)]
    (swap! peers* dissoc peer-id)
    (if-let [peer-chan (:chan peer)]
      (async/close! peer-chan))
    (doseq [[sub-id {:keys [chan stop]}] (:subscriptions peer)]
      (when (fn? stop)
        (stop))
      (async/close! chan))))

(defn disconnect-all-peers
  [peers*]
  (doseq [peer-id (keys @peers*)]
    (disconnect-peer peers* peer-id)))

(defn- api-router
  "Setup a router go-loop to handle received messages from a peer."
  [{:keys [api* peers*] :as listener} peer-id]
  (let [peer-chan (get-in @peers* [peer-id :chan])]
    (go-loop []
      (let [{:keys [message error] :as packet} (<! peer-chan)]
        (if (or (nil? packet) error)
          (do
            (log/info "peer disconnect")
            (disconnect-peer peers* peer-id))
          (do
            (when message
              (let [message (assoc message
                                   :peer-id peer-id
                                   :chan peer-chan)
                    event-type (:event message)]
                (cond
                  (= event-type :rpc) (rpc-event-handler @api* message)
                  (= event-type :subscription) (subscription-event-handler peers* peer-id @api* message)
                  (= event-type :unsubscription) (unsubscription-event-handler peers* peer-id message)
                  :default (event-handler @api* message)))
              (recur))))))))

(defn connect-listener
  "Connect an API listener to a websocket.  Takes an http-kit request
  from a websocket endpoint.

  (connect-listener listener request)
  "
  [{:keys [peers* api*] :as listener} req]
  (with-channel req ws-ch {:format :transit-json}
    (go
      (let [{:keys [message error]} (<! ws-ch)]
        (if error
          (log/warn "Peer connect error:" error)
          (let [peer-id (:peer-id message)]
            (swap! peers* assoc peer-id {:chan ws-ch :subscriptions {}})
            (api-router listener peer-id)
            (>! ws-ch {:type :connect-reply :success true})))))))


(defn- page-template
  [body]
  {:status 200
   :headers {}
   :body (html
          [:html
           [:head
            [:meta {:charset "utf-8"}]
            [:meta {:name "viewport"
                    :content "width=device-width, initial-scale=1"}]
            (include-css "css/style.css")]
           body])})

(defn- app-page
  [js css request]
  (page-template
    (apply conj
      [:body
       [:div#app
        [:h3 "Clojurescript has not been compiled..."]]]
      [:div (concat (map include-js js)
                    (map include-css css))])))

(defn- atom?
  [x]
  (instance? clojure.lang.Atom x))

(defn peer-listener
  "A peer listener holds the state need to serve an API to a set of one or more peers.
  Requires an API config map.
  * :api is an API map as described below

  The API map contains three maps of handler functions:
  * :event handlers are triggered when a peer fires an event
  * :rpc the return value of RPC calls is sent back to the client
  * :subscription subscription handlers should return a map with two keys
    - :chan a channel that will be piped to the remote peer
    - :stop an optional function to call on unsubscribe

  (defn rand-eventer
    []
    (let [c (chan)]
      (go
        ; Using while to automatically exit the go form if the channel is closed
        (while (>! c (rand-int 1000))
          (<! (async/timeout 2000))

  (peer-listener
      {:event {'ping #'my.ns/ping}
            :rpc {'add-two #(+ %1 %2)}
            :subscription {'rand-val (fn [] {:chan (rand-eventer)})})
  "
  [api]
  {:peers* (atom {})
   :api* (if (atom? api) api (atom api))})


;; Note:
; If you have your own HTTP router (e.g. compojure or bidi) already setup to
; receive websocket requests you can connect an API to each new socket using
; the peer-listener and connect-listener function.
;
;  (connect-listener listener request)
;
; Or you can use serve-api to serve the API on a port (listening for websocket
; requests), optionally also serving files and a cljs app.

(defn listen
  "Serve an API on a specified port, listening for websocket connections.  The optional
  :app-path argument is the path to a compiled cljs app which will be mounted into an
  #app div when the root URL is requested.  The optional :ws-path determines the path used
  to connect to the websocket."
  [& {:keys [listener port ws-path js css] :as args}]
  (log/debug (format "(listen %s)" args))
  (let [port (or port DEFAULT-PEER-PORT)
        ws-path (or ws-path "connect")
        routes {ws-path (partial connect-listener listener)}
        routes (if (or js css)
                 (assoc routes "" (partial app-page js css) )
                 routes)
        app (-> (make-handler ["/" routes])
                (wrap-resource "public")
                (wrap-file "resources/public" {:allow-symlinks? true}))]
    (log/info "routes: " routes)
    (assoc listener
           :port port
           :server (http-kit/run-server app {:port port}))))

(defn close
  [{:keys [server peers*] :as s}]
  (disconnect-all-peers peers*)
  (when-let [server (:server s)]
    (server)
    (dissoc s :server)))
;

(def DEFAULT-HOST "localhost")
(def DEFAULT-PORT 4242)
(def DEFAULT-PATH "connect")

(def RPC-TIMEOUT 3000)
(def DISPATCH-BUFFER-SIZE 64)                               ; # of incoming msgs to buffer

(defn- dispatch-events
  [server-chan dispatch-chan]
  "Dispatch messages from server socket onto the event chan, where
  they can easily be subscribed to by :event type. "
  (go-loop []
    (let [packet (<! server-chan)
          {:keys [message error]} packet]
      (if error
        (log/error "Websocket Error on server channel: " error)
        (do
          (>! dispatch-chan message)
          (recur))))))

(defn- pub-chan
  "Takes a readable chan, returns a core.async/pub chan that can be
  subscribed to in order to receive server events of a specific type."
  [server-chan]
  (let [dispatch-chan (async/chan DISPATCH-BUFFER-SIZE)
        event-chan    (async/pub dispatch-chan :event)]
    (dispatch-events server-chan dispatch-chan)
    event-chan))

;; this is built in to cljs.core, but we need to write our own in clj
(defn random-uuid [] (str (java.util.UUID/randomUUID)))
(defn- websocket-chan
  [url peer-id error-fn]
  (go
    (let [conn (<! (ws-client/ws-ch url {:format :transit-json}))
          _ (println "conn:")
          _ (println conn)
          {:keys [ws-channel error]} conn
          _    (>! ws-channel {:type :connect :client-id peer-id})
          {:keys [message error]} (<! ws-channel)]
      (if error
        (if error-fn
          (error-fn {:error :connect
                     :host  url}))
        (do
          (log/info "Connected to host: " url)
          ws-channel)))))

(defn- event-type-chan
  "Returns a channel onto which all server events of a specific type will be placed."
  [event-chan event-type & [buf]]
  (let [c (if buf (async/chan buf) (async/chan))]
    (async/sub event-chan event-type c)
    c))

(defn- handle-rpc-events
  [event-chan rpc-map*]
  (let [rpc-events (event-type-chan event-chan :rpc-response)]
    (go-loop []
      (let [{id :id :as event} (<! rpc-events)]
        ;; FIXME: this is a hack, convert to strings as transit UUIDs aren't the same as core UUIDs
        (when-let [res-chan (get (into {} (map (fn [[k v]] [(str k) v]) @rpc-map*)) (str id))]
          ; (get @rpc-map* id)

          (>! res-chan event)
          (swap! rpc-map* dissoc id)))
      (recur))))

(defn request
  "Make an RPC request to the server. Returns a channel that will receive the result, or nil on error.
  (The error will be logged to the console.)"
  [{:keys [rpc-map* peer-chan timeout error-fn] :as conn} fun & [args]]
  (let [req-id (random-uuid)
        res-chan (async/chan)
        t-out (async/timeout (or timeout RPC-TIMEOUT))
        event {:event :rpc :id req-id :fn fun :args (or args [])}]
    (swap! rpc-map* assoc req-id res-chan)
    (go
      (>! peer-chan event)
      (let [[v port] (async/alts! [res-chan t-out])]
        (cond
          ; The request timed out
          (= port t-out) (do
                           (swap! rpc-map* dissoc req-id)
                           (if error-fn
                             (error-fn (assoc event :error :timeout))))

          ; Got response
          (and (= port res-chan)
               (:result v))      (:result v)

          ; Got error
          (and (= port res-chan)
               (:error v)) (if error-fn
                             (error-fn (merge event v))))))))

(defn connect
  "Returns a peer connection that can be used to send events, make rpc requests, and
  subscribe to peer event channels."
  [& {:keys [url api host port path error-fn]
      :or   {host     DEFAULT-HOST
             port     DEFAULT-PORT
             path     DEFAULT-PATH
             error-fn #(log/error "Error: " %)}
      :as   args}]
  (go
    (let [url       (or url (format "ws://%s:%s/%s" host port path))
          id        (random-uuid)
          _ (println "before peer-chan")
          peer-chan (<! (websocket-chan url id error-fn))
          _ (println "after peer-chan")
          ec        (pub-chan peer-chan)
          rpc-map*  (atom {})]
      (handle-rpc-events ec rpc-map*)
      {:peer-chan         peer-chan
       :event-chan        ec
       :error-fn          error-fn
       :rpc-map*          rpc-map*
       :subscription-map* (atom {})
       :api*              (atom api)})))

