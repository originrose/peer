# think.peer - A Clojurescript library for web communications

## Development

You can verify basic connectivity with the test server and page:

```
$ lein run test
$ lein figwheel test
```

Now visit http://localhost:4242/test and you should see data that
has arrived from the server using a websocket RPC call.
