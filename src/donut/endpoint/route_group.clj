(ns donut.endpoint.route-group
  "Provides the happy path for including routes that serve as transit API
  endpoints"
  (:require
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.muuntaja :as rrmm]
   [reitit.ring.middleware.parameters :as rrmp]))

(defn wrap-merge-params
  "Merge all params maps, place under `:all-params`"
  [handler]
  (fn [req]
    (-> req
        (assoc :all-params (reduce (fn [p k] (merge p (get-in req k)))
                                   {}
                                   [[:params]
                                    [:body-params]
                                    [:path-params]
                                    [:query-params]
                                    [:path-params]
                                    [:form-params]
                                    [:multipart-params]
                                    [:parameters :body]
                                    [:parameters :path]
                                    [:parameters :query]
                                    [:parameters :form]
                                    [:parameters :multipart]]))
        handler)))

(defn wrap-muuntaja-encode
  "indicate we want to encode response with muuntaja. muuntaja handles the
  conversion between clojure data structures and wire formats"
  [handler]
  (fn [req]
    (let [res (handler req)]
      (assoc res :muuntaja/encode true))))

(def route-middleware
  "This is route middleware because it's applied after reitit matches a route; it
  relies on route info."
  [rrmp/parameters-middleware
   rrmm/format-middleware
   rrc/coerce-request-middleware
   rrc/coerce-response-middleware
   wrap-merge-params
   wrap-muuntaja-encode])

;;---
;; route group component
;;---

(def default-group-opts
  {:middleware route-middleware})

(defn route-group-component-start
  [{:keys [:donut.system/config]}]
  (let [{:keys [routes group-path group-opts]} config]
    [group-path
     (merge default-group-opts group-opts)
     routes]))

(def route-group-component-config
  {:routes     [:donut.system/local-ref [:routes]]
   :group-path [:donut.system/local-ref [:group-path]]
   :group-opts [:donut.system/local-ref [:group-opts]]})

(def RouteGroupComponent
  #:donut.system{:start  route-group-component-start
                 :config route-group-component-config})

(def RouteGroupComponentGroup
  {:route-group RouteGroupComponent
   :group-opts  {}})

(defn route-group
  [components]
  (merge RouteGroupComponentGroup components))
