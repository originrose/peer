(ns think.peer.websocket-client
  "Straight-up CLJ port of chord.client from CLJS using http-kit

https://github.com/jarohen/chord/blob/master/src/chord/client.cljs"
  (:require [clojure.core.async :as async :refer [go go-loop alt! chan <! >! put! close!]]
            [chord.channels :refer [read-from-ws! write-to-ws! bidi-ch]]
            [chord.format :refer [wrap-format]]
            [gniazdo.core :as ws])
  (:import (java.net URI)))

;; TODO: only need this fn because gniazdo is silly and has a non-configurable
;; and very small max test message size, which was causing some test query responses to die
(defn ws-client-with-larger-max-text-message-size
  [uri]
  (let [client (ws/client (URI. uri))]
    (try
      ;; well that was annoying:
      (.setMaxTextMessageSize (.getPolicy client) 1e9)
      (.setMaxTextMessageBufferSize (.getPolicy client) 1e9)
      (.start client)
      client

      (catch Throwable ex
        (.stop client)
        (throw ex)))))

(defn ws-ch
  "Creates websockets connection and returns a 2-sided channel when the websocket is opened.
   Arguments:
    ws-url           - (required) link to websocket service
    opts             - (optional) map to configure reading/writing channels
      :read-ch       - (optional) (possibly buffered) channel to use for reading the websocket
      :write-ch      - (optional) (possibly buffered) channel to use for writing to the websocket
      :format        - (optional) data format to use on the channel, (at the moment)
                                  either :edn (default), :json, :json-kw or :str.
      :ws-opts       - (optional) Other options to be passed to the websocket constructor (NodeJS only)
                                  see https://github.com/websockets/ws/blob/master/doc/ws.md#new-websocketaddress-protocols-options
   Usage:
    (:require [cljs.core.async :as a])
    (a/<! (ws-ch \"ws://127.0.0.1:6437\"))
    (a/<! (ws-ch \"ws://127.0.0.1:6437\" {:read-ch (a/chan (a/sliding-buffer 10))}))
    (a/<! (ws-ch \"ws://127.0.0.1:6437\" {:read-ch (a/chan (a/sliding-buffer 10))
                                          :write-ch (a/chan (a/dropping-buffer 10))}))"

  [ws-url & [{:keys [read-ch write-ch format ws-opts] :as opts}]]

  (let [open-ch    (async/chan)
        close-ch   (async/chan)
        {:keys [read-ch write-ch]} (-> {:read-ch  (or read-ch (chan))
                                        :write-ch (or write-ch (chan))}
                                       (wrap-format opts))
        ws-client      (ws-client-with-larger-max-text-message-size ws-url)
        web-socket (ws/connect ws-url
                               :client ws-client
                               ::ws/cleanup #(.stop ws-client)
                               :on-connect #(put! open-ch %)
                               :on-close #(put! close-ch %)
                               :on-receive #(put! read-ch {:message %})
                               :on-error #(put! read-ch {:error %}))]


    ;; TODO: find out if anyone at TT uses think.peer for binary data yet
    ;; this is commented out from the CLJS:
    ;; (set! (.-binaryType web-socket) "arraybuffer")

    (go-loop []
      (let [msg (<! write-ch)]
        (when msg
          (ws/send-msg web-socket msg)
          (recur))))

    (let [ws-chan (bidi-ch read-ch write-ch {:on-close #(ws/close web-socket)})
          initial-ch (async/chan)]

      (go-loop [opened? false]
        (alt!
          open-ch ([open-val]
                   (async/>! initial-ch {:ws-channel ws-chan})
                   (async/close! initial-ch)
                   (recur true))

          close-ch ([ev]
                     ;; TODO: this needs a little more looking into, because
                     ;; gniazdo doesn't support the concept of "clean", just status codes and reason strings
                     ;; (this is because the underlying jetty.websocket implementation doesn't either)
                     ;; btw the spec defines "clean" as when the TCP connection closes *after* the WS one
                     ;; https://tools.ietf.org/html/rfc6455#section-7.1.4
                    ;(let [maybe-error (close-event->maybe-error ev)]
                    ;  (when maybe-error
                    ;    (async/>! (if opened?
                    ;               read-ch
                    ;               initial-ch)
                    ;              {:error maybe-error}))

                    (async/close! ws-chan)
                    (async/close! initial-ch))))

      initial-ch)))

