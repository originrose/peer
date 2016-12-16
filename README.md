# think.peer - A Clojure/script library for RPC and Pub/Sub

This library provides a simple interface for your clojurescript client to interact with a namespace using either an RPC style of interaction or subscribing to events.

## Development

You can verify basic connectivity with the (simple) example:

```
$ cd example/simple
$ lein run
$ lein figwheel
```

Now visit http://localhost:4242/ and you should see data that
has arrived from the server using a websocket RPC call.
