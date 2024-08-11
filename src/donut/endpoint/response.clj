(ns donut.endpoint.response
  (:require [donut.sugar.utils :as dsu]))

(defn errors-response
  [malli-schema params]
  (when-let [feedback (dsu/feedback malli-schema params)]
    {:status   422
     :feedback feedback
     :body     [[:errors {:attrs feedback}]]}))
