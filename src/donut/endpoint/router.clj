(ns donut.endpoint.router
  (:require
   [muuntaja.core :as m]
   [reitit.coercion.malli :as rcm]
   [reitit.ring :as rr]))

(def router-opts
  {:data {:coercion rcm/coercion
          :muuntaja m/instance}})

(def RouterComponent
  #:donut.system{:start  (fn [{:keys [:donut.system/config]}]
                           (rr/router (:routes config)
                                      (:router-opts config)))
                 :config {:routes      [:donut.system/local-ref [:routes]]
                          :router-opts [:donut.system/local-ref [:router-opts]]}})
