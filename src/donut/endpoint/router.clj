(ns donut.endpoint.router
  (:require
   [donut.endpoint.encoding :as denc]
   [reitit.coercion.malli :as rcm]
   [reitit.ring :as rr]))

(def transit-format "application/transit+json")

(def router-opts
  {:data {:coercion rcm/coercion
          :muuntaja denc/default-muuntaja-instance}})

(def RouterComponent
  #:donut.system{:start  (fn [{:keys [:donut.system/config]}]
                           (rr/router (:routes config)
                                      (:router-opts config)))
                 :config {:routes      [:donut.system/local-ref [:routes]]
                          :router-opts [:donut.system/local-ref [:router-opts]]}})

(def RingHandlerComponent
  #:donut.system{:start  (fn [{:keys [:donut.system/config]}]
                           (rr/ring-handler (:router config)))
                 :config {:router [:donut.system/local-ref [:router]]}})
