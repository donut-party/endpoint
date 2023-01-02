(ns donut.endpoint.handler)

(def HandlerComponent
  #:donut.system{:start  (fn [{:keys [:donut.system/config]}]
                           (let [{:keys [route-ring-handler middleware]} config]
                             (middleware route-ring-handler)))
                 :config {:route-ring-handler [:donut.system/ref [:routing :ring-handler]]
                          :middleware         [:donut.system/local-ref [:middleware]]}})
