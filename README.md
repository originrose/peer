# think.peer

This is a networking library for Clojure and Clojurescript providing events, RPC
calls, and subscriptions to remote channels.  From Clojure you can point to a
namespace of functions to expose them as both a web API using typical HTTP
requests or as a websocket interface.

## Usage

Add the library to your dependencies like so:

[thinktopic/think.peer "0.2.0-SNAPSHOT"]

In Clojure you point to a namespace to expose its functions as
event/rpc/subscription handlers.

(ns example.app.handler
  (:require [think.peer.net :as net]
            [think.peer.api :as peer-api]))

...

(defroutes routes
  (GET "/" [] (loading-page))
  (GET "/api/v0/docs" [] (docs-page))
  (GET "/api/v0/*" req
    (html5 (peer-api/api-handler (peer-api/ns-api 'example.app.api) req)))
  (GET "/connect" req
    (net/connect-client (peer-api/ns-api 'example.app.api) req))
  (resources "/")
  (not-found "Not Found"))


Then in Clojurescript you can connect to the websocket using the
connect-to-server function, and you can make an RPC call using the request
function.  It returns a channel onto which the result of the request will be
placed.

(ns example.app.core
  (:require [think.peer.net :as net]
            [cljs.core.async :as async :refer [<! >! put!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def dbs* (atom nil))

(net/send-event 'hello-event)
(let [cnt-chan (net/subscribe-to 'second-counter)]
  (go
    (<! (net/connect-to-server "ws://localhost:1212/connect"))
    (reset! dbs* (<! (net/request 'dbs-get)))))

## Development

You can verify basic connectivity with the test server and page:
  $ lein run test
  $ lein figwheel test

Now visit http://localhost:4242/test and you should see data that
has arrived from the server using a websocket RPC call.

