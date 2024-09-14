(ns donut.endpoint.middleware.instrument
  (:require
   [steffan-westcott.clj-otel.api.trace.span :as span]
   [taoensso.telemere :as telemere]
   [taoensso.telemere.impl :as telemere-impl]))

(def otel-enabled? telemere-impl/enabled:otel-tracing?)

(defn req->outer-ctx
  [req]
  {:trace-id                   (when otel-enabled? (.getTraceId (span/get-span-context)))
   :application.request/uri    (:uri req)
   :application.request/method (:request-method req)})

(defn outer-response-data
  [{:keys [status headers] :as _response}]
  {:application.response/status status
   :application.response/headers headers})

(defn wrap-outer-context
  "Adds :uri and :method request data to logging context"
  ([handler]
   (wrap-outer-context handler nil))
  ([handler {:keys [ctx req->ctx response-data]
             :or   {req->ctx      req->outer-ctx
                    response-data outer-response-data}}]
   (fn wrap-outer-context-handler [req]
     (telemere/with-ctx+
       (merge ctx (req->ctx req))
       (do
         (telemere/event! :application.request/received)
         (let [response (handler req)]
           (telemere/event! :application.request/response
                            {:data (response-data response)})
           response))))))

;; TODO query params
(defn req->app-handler-ctx
  [req]
  {:request/username (-> req :session :identity)})

(defn wrap-log-app-handler
  ([handler]
   (wrap-log-app-handler handler nil))
  ([handler {:keys [req->ctx]
             :or   {req->ctx req->app-handler-ctx}}]
   (fn wrap-log-app-handler-handler [req]
     (let [ctx (req->ctx req)]
       (span/add-span-data! {:attributes ctx})
       (telemere/with-ctx+ (req->ctx ctx)
         (let [response (handler req)]
           (telemere/event! :application.request/handled)
           (telemere/event! :application.request/handler-ring-response
                            {:data response})
           response))))))
