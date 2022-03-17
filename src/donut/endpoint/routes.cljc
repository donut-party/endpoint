(ns donut.endpoint.routes
  (:require [donut.sugar.utils :as u]))

(defn merge-route-opts
  [routes]
  (map (fn [[path & opts]]
         [path (apply merge opts)])
       routes))

;;---
;; helpers
;;---

(defn routes-by-name
  "produces a map with routes keyed by name"
  [routes & [filter*]]
  (cond->> routes
    (fn? filter*)                 (filter filter*)
    (= (type #"") (type filter*)) (filter (fn [[path]] (re-find filter* path)))
    (= (type "") (type filter*))  (filter (fn [[path]] (re-find (re-pattern filter*) path)))
    true                          (u/key-by (comp :name second))))

(defn simple-routes
  "stripped down view of routes"
  [routes]
  (mapv (fn [route] (update route 1 :name))
        routes))

;; TODO what validation / debug / inspection helpers could I add?
