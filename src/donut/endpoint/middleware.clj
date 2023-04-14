(ns donut.endpoint.middleware
  (:require
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.gzip :as ring-gzip]
   [ring.util.response :as resp]))

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
  "Frontend routing recognizes URLs that the backend might not recognize. This
  will load an index.html file in those cases, thus loading the frontend app,
  which can then frontend route the URL"
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

(defn wrap-not-found
  "Middleware that returns a 404 'Not Found' response from an error handler if
  the base handler returns nil.

  Used to provide the index.html file by default for frontend routes"
  ([handler]
   (wrap-not-found handler identity))
  ([handler error-handler]
   (fn
     ([request]
      (or (handler request) (error-handler request)))
     ([request respond raise]
      (handler request #(respond (or % (error-handler request))) raise)))))

(def ring-defaults-config
  "Default configuration for a donut API endpoint handler"
  {:params    {:urlencoded true
               :multipart  true
               :nested     true
               :keywordize true}
   :cookies   true
   :session   {:flash        false
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
  {:gzip          true
   :latency       false
   :default-index true})

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
  #:donut.system{:doc   "Middleware stack optimized for donut framework apps. ::ds/config map give
some control over individual middleware inclusion and configuration"
                 :start (fn [{:keys [:donut.system/config]}]
                          (fn [handler] (app-middleware handler config)))
                 :config  app-middleware-config})
