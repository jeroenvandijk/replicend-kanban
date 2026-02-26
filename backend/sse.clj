(ns backend.sse
  (:require [org.httpkit.server :as hk]))

;; Credits too https://github.com/http-kit/http-kit/issues/578
(defonce clients (atom #{}))

(defn format-event [body]
  (str "data: " body "\n\n"))

(defn send! [ch message]
  (hk/send! ch
    {:status 200
     :headers
     {"Content-Type"      "text/event-stream"
      "Cache-Control"     "no-cache, no-store"}
     :body   (format-event message)}
    false))

(defn handler-sse [req init]
  (hk/as-channel req
    {:on-open  (fn [ch]
                 (swap! clients conj ch)
                 (send! ch init))

     :on-close (fn [ch _]
                 (swap! clients disj ch))}))

(defn broadcast-message-to-connected-clients! [message]
  (run! (fn [ch] (send! ch message)) @clients))
;; / Credits
