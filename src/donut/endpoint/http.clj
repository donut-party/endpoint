(ns donut.endpoint.http
  "Component group that can run an http server"
  (:require
   [donut.endpoint.handler :as deh]
   [donut.endpoint.middleware :as dem]
   [donut.endpoint.server :as des]
   [donut.system :as ds]))

(def HTTPComponentGroup
  {::ds/doc    "HTTP framework defaults for full-stack donut apps"
   :server     des/ServerComponent
   :middleware dem/AppMiddlewareComponent
   :handler    deh/HandlerComponent})

(def http-plugin
  {:donut.system.plugin/name
   ::http-plugin

   :donut.system/doc
   "HTTP framework defaults for full-stack donut apps"

   :donut.system.plugin/system-defaults
   {::ds/defs {:http HTTPComponentGroup}}})
