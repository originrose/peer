(ns think.peer.webrtc
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :refer [chan >! <! put! timeout close!]]))

(defn select-element
  [id]
  (.getElementById js/document (name id)))

(def local-video  (select-element :#local-video))
(def remote-video (select-element :#remote-video))

(def start-button  (select-element :#start-button))
(def call-button   (select-element :#call-button))
(def hangup-button (select-element :#hangup-button))

(def local-stream (atom nil))

(defn event-chan
  [src event-name]
  (let [src (if (keyword? src)
              (select-element src)
              src)
        c (chan)]
    (aset src (name event-name) #(go (>! c %)))))

(defn log
  [& args]
  (if (> (count args) 1)
    (.log js/console (apply str args))
    (.log js/console (first args))))

(defn video-stream
  []
  (let [stream-chan (chan)]
    (.getUserMedia js/navigator (clj->js {"video" true})
                   (fn [stream] (put! stream-chan stream))
                   (fn [error]
                     (close! stream-chan)
                     (log "Error opening video stream: ")
                     (log error)))
    stream-chan))

(defn start
  []
  (go
    (let [stream (<! (video-stream))]
      (set! (.-src local-video) (.createObjectURL js/URL stream))
      (reset! local-stream stream)
      (set! (.-disabled call-button) false)
      (log "local stream connected..."))))


(defn connect-ice-candidate
  [src tgt]
  (set! (.-onicecandidate src)
        (fn [event]
          (when (.-candidate event)
            ;(.addIceCandidate tgt (js/RTCIceCandidate. (.-candidate event)))
            (.addIceCandidate tgt (.-candidate event))
            ))))

(defn call
  []
  (set! (.-disabled call-button) true)
  (set! (.-disabled hangup-button) false)

  (let [video-tracks (.getVideoTracks @local-stream)
        audio-tracks (.getAudioTracks @local-stream)
        local-con    (js/RTCPeerConnection. nil)
        remote-con   (js/RTCPeerConnection. nil)
        local-can-chan (chan)]
    (log "Video device: " (.-label (first video-tracks)))
    ;(log "Audio device: " (.-label (first audio-tracks)))))

    (connect-ice-candidate local-con remote-con)
    (.addStream local-con @local-stream)

    (connect-ice-candidate remote-con local-con)

    (set! (.-onaddstream remote-con)
          (fn [event] (set! (.-src remote-video) (.createObjectURL js/URL (.-stream event)))))

    (.createOffer local-con
      (fn [desc]
        (log "local description:")
        (log desc)
        (.setLocalDescription local-con desc)
        (.setRemoteDescription remote-con desc)
        (.createAnswer remote-con
        (fn [remote-desc]
          (log "remote description:")
          (log remote-desc)
          (.setLocalDescription remote-con remote-desc)
          (.setRemoteDescription local-con remote-desc)))))

    (set! (.-onclick hangup-button)
      (fn []
        (.close local-con)
        (.close remote-con)))))


(defn peer-connection
  []
  (let [servers (clj->js {"iceServers" [{"url" "stun:23.21.150.121"}]})
        config  (clj->js {"optional" [{"DtlsSrtpKeyAgreement" true}
                                      {"RtpDataChannels" true}]})
        servers    nil
        config     (clj->js {"optional" [{"RtpDataChannels" true}]})

        ; TODO: Try out a reliable channel too...
        local-con  (js/RTCPeerConnection. servers config)
        send-chan  (.createDataChannel peer-connection "send-channel" (clj->js {"reliable" false}))
        open-chan  (event-chan send-chan :onopen)
        close-chan (event-chan send-chan :onclose)
        msg-chan   (event-chan send-chan :onmessage)

        remote-con (js/RTCPeerConnection. servers config)
        new-data-chan (event-chan remote-con :ondatachannel)

        txt-received (select-element :#text-received)
        input-txt    (select-element :#text-to-send)
        send-btn-chan (event-chan :#send-txt-button :onclick)
        ]

    (connect-ice-candidate local-con remote-con)
    (connect-ice-candidate remote-con local-con)

    (go (while true
          (.log js/console (.-data (<! msg-chan)))))

    (go (while true
          (<! open-chan)
          (.log js/console "data send channel opened...")
          (<! close-chan)
          (.log js/console "data send channel closed...")))

    (go (while true
          (let [recv-data-chan (.-channel (<! new-data-chan))
                msg-chan (event-chan recv-data-chan :onmessage)]
            (go
              (while true
                (set! (.-value txt-received) (.-data (<! msg-chan))))))))

    (go (while true
          (<! send-btn-chan)
          (.send send-chan (.-value input-txt))))

    (.createOffer local-con
      (fn [desc]
        (.setLocalDescription local-con desc)
        (.setRemoteDescription remote-con desc)
        (.createAnswer remote-con
          (fn [remote-desc]
            (.setLocalDescription remote-con remote-desc)
            (.setRemoteDescription local-con remote-desc)))))
    ))

(defn setup
  []
  (set! (.-disabled start-button) false)
  (set! (.-onclick start-button) start)

  (set! (.-disabled call-button) true)
  (set! (.-onclick call-button) call)
  (set! (.-disabled hangup-button) true)
  ;(set! (.-disabled start-button) false)
  )

;(setup)
