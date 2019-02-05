# Peer

This is a networking library for Clojure and ClojureScript providing events, RPC
calls, and subscriptions to remote channels.  From Clojure you can point to a
namespace of functions to expose them as both a web API using typical HTTP
requests or as a websocket interface.

## Usage

Add the library to your dependencies like so:

    [peer "0.4.0"]

In Clojure you point to a namespace to expose its functions as
event/rpc/subscription handlers.

    (ns example.app.handler
      (:require [peer.net :as net]
                [peer.api :as peer-api]))

    ...

    (def listener (net/peer-listener {:api (peer-api/ns-api 'example.app.api)})

    (defroutes routes
      (GET "/" [] (loading-page))
      (GET "/api/v0/docs" [] (docs-page))
      (GET "/api/v0/*" req
        (html5 (peer-api/api-handler (peer-api/ns-api 'example.app.api) req)))
      (GET "/connect" req
        (net/connect-listener listener req))
      (resources "/")
      (not-found "Not Found"))


Then in ClojureScript you can connect to the websocket using the
connect-to-server function, and you can make an RPC call using the request
function.  It returns a channel onto which the result of the request will be
placed.

    (ns example.app.core
      (:require [peer.net :as net]
                [cljs.core.async :as async :refer [<! >! put!]])
      (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

    (def dbs* (atom nil))

    (go
     (let [conn (<! (net/connect "ws://localhost:1212/connect"))
           (net/event conn 'hello-event)
           (reset! dbs* (<! (net/request 'dbs-get)))]
         (go-loop [counter (net/subscribe conn 'second-counter)]
           (println (<! counter))
           (recur counter))))


## HTTP Requests

The same functions exposed over a websocket for :rpc and :event handlers can
also be served as regular HTTP endpoints using the peer.api/api-handler
function.  This function is a typical ring handler that takes the API map and
an HTTP request, and it returns an HTTP response.

If you use peer.net/listen it will automatically setup the api handler.
A full example can be seen in the http-api-test unit test.

    (net/listen {:port 4242 :api-ns 'test-api})

Now you can access a test-handler function like this:

    (let [msg (util/edn->transit {:id (uuid) :args [80 20 100]})
                res (http/put "http://localhost:4242/api/v0/rpc/test-handler"
                               {:body msg})
                response (transit->edn (:body @res))]
            (is (= 200 (:result response))))

## Documentation

If you use the peer.net/listen function to setup your API then html
documentation will automatically be generated and served at /docs.

    (def s (net/listen {:port 4242 :api-ns 'test-api}))

Browse to "http://localhost:4242/docs" to see the documentation.

## Development

You can verify basic connectivity with the (simple) example:

    $ cd example/simple
    $ lein run
    $ lein figwheel

Now visit http://localhost:4242/ and you should see data that
has arrived from the server using a websocket RPC call.
