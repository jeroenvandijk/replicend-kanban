#!/usr/bin/env bb

(ns replicend-kanban
  (:require 
   [babashka.process :refer [shell process exec]]
   [backend.api :as api :refer [app]]
   [clojure.string :as str]
   [org.httpkit.server :as hk]))

  
(defn server 
  {:org.babashka/cli {:coerce {:port :long}}}
  [{:keys [port] :or {port 8080}}]
  
  (future 
    (Thread/sleep 10) ;; Just a little bit of time
    (let [url (str "http://localhost:" port)]
      (println "Opened web page at " url)
      (shell "open" url)))
  
  (println "Starting server")
  (hk/run-server app {:port port}))
