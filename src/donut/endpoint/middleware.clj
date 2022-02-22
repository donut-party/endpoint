(ns donut.endpoint.middleware
  "Defines a default middleware stack for donut apps."
  (:require [clojure.stacktrace :as cst]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as rrmm]
            [reitit.ring.middleware.parameters :as rrmp]
            [ring.middleware.defaults :as ring-defaults]
            [ring.middleware.gzip :as ring-gzip]
            [ring.util.response :as resp]
            #_[ring.middleware.stacktrace :as ring-stacktrace]))

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
  "TODO we want to muuntaja/encode because..."
  [handler]
  (fn [req]
    (let [res (handler req)]
      (assoc res :muuntaja/encode true))))

(defn wrap-format-exception
  "Catches exceptions and returns a formatted response.
  TODO update this to use segment response?"
  [handler {:keys [include-data]}]
  (fn [req]
    (try (handler req)
         (catch Throwable t
           {:status 500
            :body   [[:exception (if include-data
                                   {:message     (.getMessage t)
                                    :ex-data     (ex-data t)
                                    :stack-trace (with-out-str (cst/print-stack-trace t))}
                                   {})]]}))))

(defn wrap-latency
  "Introduce latency, useful for local dev when you want to simulate
  more realistic response times"
  [handler {:keys [sleep sleep-max]}]
  (fn [req]
    (Thread/sleep (if sleep-max
                    (rand (+ (- sleep-max sleep) sleep))
                    sleep))
    (handler req)))

(defn wrap-default-index
  [handler & [{:keys [root exclude status]
               :or   {root    "public"
                      exclude ["json"]
                      status  404}}]]
  (fn [req]
    (or (handler req)
        (let [content-type (str (get-in req [:headers "content-type"]))]
          (if (some #(re-find (re-pattern %) content-type) exclude)
            {:status status}
            (-> (resp/resource-response "index.html" {:root root})
                (resp/content-type "text/html")
                (resp/status 200)))))))

(defn not-found
  [resp]
  (update resp :status #(or % 404)))

(defn wrap-not-found
  "Middleware that returns a 404 'Not Found' response from an error handler if
  the base handler returns nil.

  Used to provide the index.html file by default for frontend routes"
  ([handler]
   (wrap-not-found handler identity))
  ([handler error-handler]
   (fn
     ([request]
      (or (handler request) (not-found (error-handler request))))
     ([request respond raise]
      (handler request #(respond (or % (not-found (error-handler request)))) raise)))))

(def ring-defaults-config
  "A default configuration for a browser-accessible website, based on current
  best practice."
  {:params    {:urlencoded true
               :multipart  true
               :nested     true
               :keywordize true}
   :cookies   true
   :session   {:flash        true
               :cookie-attrs {:http-only true
                              :same-site :strict}}
   :security  {:anti-forgery         false
               :xss-protection       {:enable? true
                                      :mode    :block}
               :frame-options        :sameorigin
               :content-type-options :nosniff}
   :static    {:resources "public"}
   :responses {:not-modified-responses true
               :absolute-redirects     true
               :content-types          true
               :default-charset        "utf-8"}})

(def endpoint-defaults-config
  {:gzip        true
   :latency     false
   :merge-parms true})

(def app-middleware-config
  (merge ring-defaults-config endpoint-defaults-config))

(defn- wrap [handler middleware options]
  (if (true? options)
    (middleware handler)
    (if options
      (middleware handler options)
      handler)))

(defn- wrap-defaults [handler config]
  (-> handler
      (wrap ring-gzip/wrap-gzip (get-in config [:gzip] true))
      (wrap wrap-latency (get-in config [:latency] false))
      (wrap wrap-default-index (get-in config [:default-index] true))
      (wrap wrap-not-found (get-in config [:not-found] true))))

(defn app-middleware
  [handler & [config]]
  (-> handler
      (ring-defaults/wrap-defaults (or config app-middleware-config))
      (wrap-defaults (or config app-middleware-config))))

(def AppMiddlewareComponent
  "A donut.system component that applies configured middleware to a handler"
  {:start (fn [conf _ _]
            (fn [handler] (app-middleware handler conf)))
   :conf  app-middleware-config})

(defn route-middleware
  []
  [rrmp/parameters-middleware
   rrmm/format-middleware
   rrc/coerce-request-middleware
   rrc/coerce-response-middleware
   wrap-merge-params
   wrap-muuntaja-encode])
