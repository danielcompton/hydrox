(ns hydrox.doc.render
  (:require [hydrox.common.util :as util]
            [hydrox.doc.render
             [article :as article]
             [navigation :as navigation]
             [toc :as toc]]
            [hiccup.compiler :as compiler]
            [clojure.string :as string]))

(defn render-article [{:keys [elements]} folio]
  (->> elements
       (map #(article/render % folio))
       (#'compiler/compile-seq)
       (string/join)))

(defn render-navigation [{:keys [elements]} folio]
  (let [chapters (filter (fn [e] (#{:chapter :appendix} (:type e)))
                         elements)]
    (->> chapters
         (map #(navigation/render % folio))
         (#'compiler/compile-seq)
         (string/join))))

(defn replace-template [template includes opts project]
  (reduce-kv (fn [html k v]
               (.replaceAll html (str "<@=" (name k) ">")
                            (cond (string? v)
                                  v

                                  (vector? v)
                                  (cond (= (first v) :file)
                                        (slurp (util/full-path (second v) (-> opts :template :path) project))))))
             template
             includes))
