(ns donut.endpoint.middleware.exception
  (:require
   [taoensso.telemere :as telemere]))

(def ExData
  "Good keys to include in ex-data"
  [:map
   ;; unique identifier for this failure, preferably namespaced keyword
   [:id keyword?]
   ;; categorize this failure. dispatch on this for logging, rendering
   [:type any?]
   ;; detailed explanation of why the failure occurred
   ;; e.g. "start-date should be in YYYY-MM-DD format"
   [:explain {:optional true} any?]
   ;; instructions for the user to address the problem
   [:remediation {:optional true} any?]])

(def ex-data-keys
  #{:id :type :explain :remediation})

(defn logging-safe-request
  "Remove keys that are either sensitive or noisy"
  [req]
  (-> req
      (dissoc :reitit.core/match :reitit.core/router :session/key
              :muuntaja/response :muuntaja/request
              :cookies :deps :body)
      (update :headers dissoc "cookie")))

;;---
;; middleware
;;---


(defn wrap-catch-exception
  "Catch exceptions closer to where they're thrown so we can capture the request
  as close as possible to its final state"
  [handler]
  (fn wrap-catch-exception-handler
    [req]
    (try
      (handler req)
      (catch Throwable t
        (let [error-id (random-uuid)]
          (telemere/error! {:id   ::wrap-catch-exception-handler
                            :data {:request  (logging-safe-request req)
                                   :error-id error-id}}
                           t))
        (throw t)))))
