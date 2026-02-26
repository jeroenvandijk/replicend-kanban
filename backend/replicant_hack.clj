;; Replicant rendering of just hiccup data.

(ns backend.replicant-hack
  (:require [replicant.alias :as alias]
            [replicant.hiccup-headers :as hiccup]
            [replicant.hiccup :refer :all]
            [replicant.core :as r]
            [replicant.string :as rs]
            [clojure.string :as str]))


(defn render-event-handlers [v]
  [[:data-on (pr-str (into [] (sort v)))]])


(defn str-join [sep xs]
  (str/join sep xs))


(defn ^:no-doc render-attrs [attrs]
  (reduce-kv
   (fn [acc k v]
     (into acc (when (and (not (#{#_:on :innerHTML} k))
                          v
                          (nil? (namespace k)))
                 (case k
                   :on (render-event-handlers v)
                   
                   :classes
                   [[:class (str-join " " v)]]
                   
                   [[k v]]))))
   {}
   attrs))


(defn render-node [headers {:keys [depth indent aliases alias-data] :as opts}]
  (let [headers (rs/get-expanded-headers {:aliases aliases
                                          :alias-data alias-data} headers)]
    (if-let [text (hiccup/text headers)]
      text
      (let [tag-name (hiccup/tag-name headers)
            attrs (r/get-attrs headers)]
        
        (into [tag-name 
               (render-attrs attrs)

               ] 
              (keep (fn [child]
                     (when child
                       (render-node child opts))))
              (r/get-children headers (hiccup/html-ns headers)))))))


(defn render
  "Render `hiccup` to a string of HTML. `hiccup` can be either a single hiccup
  node or a list of multiple nodes."
  [hiccup & [{:keys [aliases alias-data indent]}]]
  (let [opt {:indent (or indent 0)
             :depth 0
             :aliases (or aliases (alias/get-registered-aliases))
             :alias-data alias-data}]
    (cond
      (hiccup? hiccup)
      (render-node (r/get-hiccup-headers nil hiccup) opt)

      (seq? hiccup)
      (map (fn [hiccup-node]
             (render-node (r/get-hiccup-headers nil hiccup-node) opt))
           hiccup)

      :else (str hiccup))))


(comment

  (defalias pill-bar [_ buttons]
            [:div.a buttons
             [:h1 {:innerHTML "BAR"} "fooo" ]])

  (defalias pill-bar [_ buttons]
            [:nav[:replicant.hiccup/pill-bar2
                  buttons]])

  (render [:replicant.hiccup/pill-bar "foo"])

  :-)
