(ns donut.endpoint.server
  (:require
   [donut.system :as ds]
   [ring.adapter.jetty :as rj]))

(def ServerComponent
  #::ds{:doc    "Starts a jetty server. Config options:

:handler is a ring handler
:options is a map of jetty options, including :port and :join?
         :join? false will run jetty on a separate thread
         :join? true will run jetty on calling thread, blocking it"
        :start  (fn [{:keys [::ds/config]}]
                  (rj/run-jetty (:handler config) (:options config)))
        :stop   (fn [{:keys [::ds/instance]}]
                  (.stop instance))
        :config {:handler (ds/local-ref [:handler])
                 :options {:port  (ds/ref [:env :http-port])
                           :join? false}}})
