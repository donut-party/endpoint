(ns donut.endpoint.middleware
  (:require
   [donut.endpoint.middleware.exception :as exception]
   [donut.endpoint.middleware.instrument :as instrument]
   [ring.middleware.x-headers :as x]
   [ring.middleware.flash :refer [wrap-flash]]
   [ring.middleware.session :refer [wrap-session]]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.nested-params :refer [wrap-nested-params]]
   [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.resource :refer [wrap-resource]]
   [ring.middleware.file :refer [wrap-file]]
   [ring.middleware.not-modified :refer [wrap-not-modified]]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.default-charset :refer [wrap-default-charset]]
   [ring.middleware.absolute-redirects :refer [wrap-absolute-redirects]]
   [ring.middleware.ssl :refer [wrap-ssl-redirect wrap-hsts wrap-forwarded-scheme]]
   [ring.middleware.proxy-headers :refer [wrap-forwarded-remote-addr]]
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

(defmacro middleware-component
  [f & [opts]]
  `(merge {:name ~(keyword (name f))
           :middleware ~f
           :donut.system/doc (:doc (meta (var ~f)))}
          ~opts))

(def donut-middleware-component-group-config
  [(middleware-component wrap-anti-forgery {:disable? true})
   (middleware-component wrap-flash {:disable? true})
   {:name                :wrap-session
    :donut.system/start  (fn [{:keys [:donut.system/config]}]
                           {:name             :wrap-session
                            :middleware       wrap-session
                            :donut.system/doc (:doc (meta (var wrap-session)))
                            :options          {:store (:session-store config)}})
    :donut.system/config {:session-store [:donut.system/local-ref [:session-store]]}}
   (middleware-component wrap-keyword-params)
   (middleware-component wrap-nested-params)
   (middleware-component wrap-multipart-params)
   (middleware-component wrap-params)
   (middleware-component wrap-cookies)
   (middleware-component wrap-absolute-redirects)
   (middleware-component wrap-resource {:options "public"})
   (middleware-component wrap-file {:disable? true})
   (middleware-component wrap-content-type)
   (middleware-component wrap-default-charset {:options "utf-8"})
   (middleware-component wrap-not-modified)
   (middleware-component x/wrap-xss-protection {:disable? true})
   (middleware-component x/wrap-frame-options {:options :sameorigin})
   (middleware-component x/wrap-content-type-options {:options :nosniff})
   (middleware-component wrap-hsts {:disable? true})
   (middleware-component wrap-ssl-redirect {:disable? true})
   (middleware-component wrap-forwarded-scheme {:disable? true})
   (middleware-component wrap-forwarded-remote-addr {:disable? true})
   (middleware-component ring-gzip/wrap-gzip)
   (middleware-component wrap-latency {:disable? true})
   (middleware-component wrap-default-index)
   (middleware-component wrap-not-found)
   (middleware-component exception/wrap-catch-exception)
   (middleware-component instrument/wrap-outer-context)])


(defn- valid-secret-key? [key]
  (and (= (type (byte-array 0)) (type key))
       (= (count key) 16)))

(def CookieSessionStoreComponent
  #:donut.system{:doc   "Ring cookie session store. Set cookie's session under [::ds/config :key].
Using a random key will invalidate cookies between server restarts."
                 :start (fn [{:keys [:donut.system/config]}]
                          (when-not (valid-secret-key? (:key config))
                            (throw (ex-info "Must supply byte array of exactly 16 bytes under [::ds/config :key] for this component
Use e.g. one of:
- (crypto.random/bytes 16)
- (byte-array [76 123 -88 -31 -122 -128 19 -14 -112 -108 125 0 19 -52 108 -35]]) "
                                            {:provided-config config})))
                          (cookie-store config))})

(def DonutMiddlewareComponent
  "A donut.system component that applies configured middleware to a handler"
  #:donut.system{:doc    "Middleware stack optimized for donut framework apps. ::ds/config map give
some control over individual middleware inclusion and configuration"
                 :start  (fn [{:keys [:donut.system/config]}]
                           (fn [handler]
                             (reduce (fn [handler {:keys [middleware options disable?]}]
                                       (cond disable? handler
                                             options  (middleware handler options)
                                             :else    (middleware handler)))
                                     handler
                                     (:middleware config))))
                 :config {:middleware (mapv (fn [m] [:donut.system/local-ref [(:name m)]])
                                            donut-middleware-component-group-config)}})

(def DonutMiddlewareComponentGroup
  (-> (reduce (fn [group component-config]
                (assoc group (:name component-config) component-config))
              {}
              donut-middleware-component-group-config)
      (assoc :session-store CookieSessionStoreComponent
             :donut-middleware DonutMiddlewareComponent)))
