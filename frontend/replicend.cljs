(ns replicend
  (:require
   [reagami.core :as reagami]
   [nexus.registry :as nxr]
   [nexus.strategies :as strategies]
   [clojure.string :as str]))


(nxr/register-action! :actions/command 
  (fn [_ wrapped-command]
    [[:effects/command wrapped-command]]))


(nxr/register-effect! :effects/command
                      ^:nexus/batch
                      (fn [ctx store commands]
                        (js/console.log "Posting" (pr-str commands))
                        (-> (js/fetch "/command"
                                      #js {:method "POST"
                                           :body (pr-str commands)})
                           #_(.then #(.text %))
                           #_(.then clojure.edn/read-string)
                           #_(.then (fn [response]
                                    ;; REVIEW Should we do something with the response
                                    #_(js/console.log "handle response" (pr-str response))                                    
                                    ))
                           )))


(nxr/register-system->state! deref)


#_(nxr/register-interceptor! :before-action
   (fn [{:keys [action] :as ctx}]
     (println ":before action:\n" (pr-str action))
     ctx))

#_(nxr/register-interceptor! :before-dispatch
   (fn [{:keys [actions] :as ctx}]
     (println ":before-dispatch actions:\n" (pr-str actions))
     ctx))
     
#_(nxr/register-interceptor! :before-effect
   (fn [{:keys [effect] :as ctx}]
     (println ":before-effect:\n" (pr-str effect))
     ctx))


(nxr/register-interceptor! strategies/fail-fast)


;; We don't store anything locally (yet), but nxr/dispatch expects an atom
(def store (atom {}))


(defn dispatch-actions [dispatch-data actions]
  (let [registry (nxr/get-registry)
        local-actions (into #{} (concat (keys (:nexus/actions registry))
                                        (keys (:nexus/effects registry))))
        ;; All remote actions are wrapped in :effects/command
        actions0
        (map (fn [action]
               (if (contains? local-actions (first action))
                 action
                 [:actions/command {:action/data action}])
               )
             actions)] 
    (js/console.log "Dispatch actions"  (pr-str actions0))
    (let [{:keys [results errors]} (nxr/dispatch store dispatch-data actions0)]
      (when-let [error (->> errors (filter :err) first)]
          (throw error)))))


(def *event-handler-cache (atom {}))


(defn get-event-handler [actions]
  (let [actions (if (keyword? (first actions)) [actions] actions)
        ret (swap! *event-handler-cache 
                   (fn [cache]
                     (if (get cache actions)
                       cache
                       (assoc cache actions 
                              (fn [event]
                                (dispatch-actions {:replicant/dom-event event}
                                                  actions))))))]
    (get ret actions)))


(defn add-event-handler [el data-on]
  (doseq [[event-type actions] data-on]
    (println "Adding event handler" (pr-str event-type) (pr-str actions))
    (.addEventListener el 
                       (str/replace (name event-type) #"-" "")
                       (get-event-handler actions))))


(defn on-render [node lifecycle {:keys [unmount updates] :as data}]
  (js/console.log "node rendered" node))

(defn add-event-handlers [data]
  (clojure.walk/postwalk (fn [x]
                           (if-let [on (:data-on x)]
                             (do
                               (reduce (fn [acc [event-type actions]]
                                            (assoc acc
                                                   (keyword (str "on-" (name event-type)))
                                                   (get-event-handler actions)))
                                       x
                                       (read-string on)))                             
                             x))
                         data))


(defn- render [s]
  (let [data (read-string s)
        enriched-data (add-event-handlers data)]
    #_(prn "Reagami: render" enriched-data)
    (reagami/render (.getElementById js/document "app") enriched-data)))


(defn start-stream []
  (let [host-alias (str "http://"  (js/Date.now) "-" js/document.location.host)
        
        event-stream (fn event-stream []
                       #_(js/console.log "request at " (js/Date.now))                     
                       (-> (js/fetch
                            "/" 
                            (clj->js {:headers {"Accept" "text/event-stream"
                                                "x-forwarded-for" js/document.location.host}}))
                           (.then (fn [response]
                                    #_(js/console.log "response at " (js/Date.now) (pr-str (js->clj response)))
                                    (let [stream (.-body response)
                                          reader (.getReader stream)
                                          readChunk (fn readChunk []
                                                      (-> (.read reader)
                                                          (.then (fn [x]
                                                                   (let [{:keys [value done] :as y} (js->clj x {:keywordize-keys true})]
                                                        
                                                                     (if done 
                                                                       (js/console.log "Stream finished")
                                                                       (do
                                                                         (when value 
                                                                           (let [chunkString (.decode (js/TextDecoder.) value)
                                                                                 body-data-str (subs chunkString 6)]
                                                                             (render body-data-str)))
                                                            
                                                                         (readChunk))))))
                                                          (.catch (fn [error]
                                                                    (js/console.log "Error in Read chunk... " (js/Date.now))
                                                                    (js/console.error error)

                                                                    ;; When connection drops entirely, e.g. on server reset
                                                                    (js/console.log "Trying to reconnect" (js/Date.now))
                                                                    (js/setTimeout event-stream 50)
                                                                    ))
                                                          ))]
                                      (readChunk))))
                           (.catch (fn [error]
                                     (js/console.log "Error in stream ... ")
                                     (js/console.error error)
                                     
                                     ;; Try to reconnect
                                     (js/console.log "Trying to reconnect" (js/Date.now))
                                     (js/setTimeout event-stream 50)))))
]
    (event-stream)))


(js/console.log "%cReplicend%c loaded", "font-weight: bold", "font-weight: normal")

