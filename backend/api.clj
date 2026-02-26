(ns backend.api
  (:require 
   [backend.replicant-hack :as replicant]
   [backend.sse :as sse]
   [clojure.edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [kanban.ui :as ui]
   [kanban.task :as task]
   [kanban.actions]
   [kanban.sample-data :as sample-data]
   [kanban.ui.elements]
   [nexus.registry :as nxr]
   [org.httpkit.server :as hk]
   [replicant.alias]
   [ring.middleware.resource :refer [resource-request]]))


(def store
  (atom {:tasks 
         (->> sample-data/tasks
              (map (juxt :task/id identity))
              (into {}))
         :system/started-at (java.util.Date.)
         :columns sample-data/columns
         :tags sample-data/tags}))


(defn get-aliases []
  (-> (replicant.alias/get-registered-aliases) 
      ;; REVIEW I had an issue with missing namespaces, but maybe it is not necessary anymore
      (merge (update-keys (replicant.alias/get-registered-aliases) 
                          (fn [k] (keyword "kanban.ui.elements" (name k)))) )))


(defn main-view [data]
  [:div#app (ui/render-app data)])

(defn asset-path [path]
  ;; Add CACHE buster so we don't have stale files accidentally
  (str path "?" (.getTime (java.util.Date.))))

(defn javascript-include-tag [path]
  [:script {:src (asset-path path) :type "application/javascript"}])


(defn scittle-include-tag [path]
  [:script {:type "application/x-scittle" :src (asset-path path)}])


(defn scittle-libs []
  (for [lib ["scittle"
             "scittle.pprint"
             #_"scittle.dataspex"]] 
    (javascript-include-tag (str "https://cdn.jsdelivr.net/npm/scittle@0.8.31/dist/" lib ".js"))))


(defn scittle-block [s]
  [:script {:type "application/x-scittle" 
            :innerHTML s}])


(defn scittle-inline [resource]
  (scittle-block (slurp (io/resource resource))))


(defn layout [& hiccup]
  [:html
   [:head
    (scittle-libs)
   
    ;; From libs
    (scittle-include-tag "reagami/core.cljc")

    (scittle-include-tag "nexus/core.cljc")
    (scittle-include-tag "nexus/registry.cljc")
    (scittle-include-tag "nexus/strategies.cljc")

    ;; Original kanban namespaces
    (scittle-include-tag "kanban/forms.cljs")
    (scittle-include-tag "kanban/command.cljc")

    ;; Subset of kanban.actions
    (scittle-include-tag "frontend/kanban/actions.cljc")

    ;; Bridge between frontend and backend Nexus
    (scittle-include-tag "frontend/replicend.cljs")

    (scittle-block '(replicend/start-stream))

    ;; Styles artifact from replicant-kanban
    [:link {:rel "stylesheet" :href "frontend/styles.css"}]]
   [:body hiccup]])


(defn render-hiccup [hiccup]
  (replicant/render hiccup  {:aliases (get-aliases)}))


(defn render-html [hiccup]
  (replicant.string/render hiccup {:aliases (get-aliases)}))


(defn render-main-view-hiccup [data]
  (render-hiccup (main-view data)))


(defn render-main-page-html [data]
  (render-html (layout (main-view data))))


(defn handle-command [req]
  (if-let [command-data (try
                          (clojure.edn/read-string (slurp (:body req)))
                          (catch Exception e
                            (println "Failed to parse command body")
                            (prn e)))]
    (let [dispatch-data {:type :dispatch-data}]
      (nxr/dispatch store dispatch-data (mapv (comp :action/data first) command-data)))
    {:error "Unparsable command"}))


(defn pp-str [x]
  (with-out-str (clojure.pprint/pprint x)))


;; On every change send an update to the clients
(add-watch store ::sse 
           (fn [_ _ old-data updated-data]  
             (when-not (= old-data updated-data)
               #_(let [[old new] (take 2 (clojure.data/diff old-data updated-data))] 
                 (println "Diff:\n"
                          " old:\n" 
                          (pp-str old) "\n"

                          "  new:\n" 
                          (pp-str new) "\n"))
               (sse/broadcast-message-to-connected-clients!
                (render-main-view-hiccup updated-data)))))


(defn app [request]
  #_(println "Request" (pr-str request))
  (if (= (:request-method request) :post)

    (case (:uri request)
      "/command" 
      {:status 200 
       ;; For debugging: Use text/edn so we can read the response in the console
       :headers {"content-type" "text/edn"}
       :body (pp-str (handle-command request))})
    
    (or (resource-request request ".")
        (let [accept-header (get-in request [:headers "accept"])]
          (or 
           (when accept-header
             (cond
               (str/includes? accept-header "text/html")
               {:status 200
                :body 
                (render-html (layout [:div#app "Loading"])) #_
                (render-main-page-html @store)}
               
               (str/includes? accept-header "text/event-stream")
               (sse/handler-sse request (render-main-view-hiccup @store))))

           {:status 422})))))


