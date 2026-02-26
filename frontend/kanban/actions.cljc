(ns kanban.actions
  (:require [kanban.command :as command]
            #?(:cljs [kanban.forms :as forms])
            [nexus.registry :as nxr]))


(nxr/register-placeholder! :event.target/value
  (fn [{:replicant/keys [dom-event]}]
    (some-> dom-event .-target .-value)))


(nxr/register-placeholder! :clock/now
   (fn [& _]
     (js/Date.)))


(nxr/register-placeholder! :event/form-data
                           (fn [{:replicant/keys [dom-event]}]
                             (some-> dom-event .-target forms/gather-form-data)))


(defn tick
  ([f]
   #?(:cljs (js/requestAnimationFrame f)
      :clj (f)))
  ([#?(:clj _ :cljs ms) f]
   #?(:cljs (js/setTimeout f ms)
      :clj (f))))


(nxr/register-effect! :actions/prevent-default
  (fn [{:keys [dispatch-data]}]
    (some-> dispatch-data :replicant/dom-event .preventDefault)))

(nxr/register-effect! :actions/start-drag-move
  (fn [{:keys [dispatch-data] :as args}]
    (when-let [event (:replicant/dom-event dispatch-data)]
      (tick #(-> event .-target .-classList (.add "invisible")))
      (set! (.-effectAllowed (.-dataTransfer event)) "move"))))


(nxr/register-effect! :actions/end-drag-move
  (fn [{:keys [dispatch-data]}]
    (some-> dispatch-data :replicant/dom-event
            .-target .-classList (.remove "invisible"))))

(nxr/register-action! :actions/flash
  (fn [_ ms path v]
    [[:actions/command {:action/data [:actions/assoc-in path v]}]
     [:actions/delay ms
      [[:actions/command {:action/data [:actions/dissoc-in path]}]]]]))


(nxr/register-effect! :actions/delay
  (fn [{:keys [dispatch]} _ ms actions]
    (tick ms #(dispatch actions))))


(nxr/register-effect! :effects/drop (fn [e [effect]]
                                     ;(js/console.log "moving" (pr-str e) (pr-str effect))
                                     (set! (.-dropEffect (.-dataTransfer e)) (name effect))))


(nxr/register-placeholder! :random/uuid (fn [_] (random-uuid)))
