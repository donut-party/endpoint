(ns donut.endpoint.routes
  "Sugar for reitit routes. Lets you:

  1. Specify a map of options that apply to a group of routes
  2. Transform names (usually namespace names) into reitit
  routes that include both:
     - a collection routes, e.g. `/users`
     - a unary route, e.g. `/user/{id}`


  ## Basic expansion

  A sugared route definition might be:

  [[:my-app.endpoint.user]]

  This would expand to:

  [[\"/user\" {:name   :users
               ::ns    :my-app.endpoint.user
               ::type  :collection
               :id-key :id}]
   [\"/user/{id}\" {:name   :user
                    ::ns    :my-app.endpoint.user
                    ::type  :member
                    :id-key :id}]]

  ## Common option map

  Here's how you'd apply a map of options to many routes:

  [{:ctx {:foo :bar}}
   [:my-app.endpoint.user]
   [:my-app.endpoint.post]

   {} ;; resets \"shared\" options to an empty ma
   [:my-app.endpoint.vote]]

  This would expand to:

  [[\"/user\" {:name   :users
               ::ns    :my-app.endpoint.user
               ::type  :collection
               :ctx    {:foo :bar}
               :id-key :id}]
   [\"/user/{id}\" {:name   :user
                    ::ns    :my-app.endpoint.user
                    ::type  :member
                    :ctx    {:foo :bar}
                    :id-key :id}]
   [\"/post\" {:name   :posts
               ::ns    :my-app.endpoint.post
               ::type  :collection
               :ctx    {:foo :bar}
               :id-key :id}]
   [\"/post/{id}\" {:name   :post
                    ::ns    :my-app.endpoint.post
                    ::type  :member
                    :ctx    {:foo :bar}
                    :id-key :id}]

   ;; vote routes do not include the :ctx key
   [\"/vote\" {:name   :votes
               ::ns    :my-app.endpoint.vote
               ::type  :collection
               :id-key :id}]
   [\"/vote/{id}\" {:name   :vote
                    ::ns    :my-app.endpoint.vote
                    ::type  :member
                    :id-key :id}]]"
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [donut.sugar.utils :as u]
            [malli.core :as m]
            [malli.error :as me]
            [meta-merge.core :as mm]
            #?@(:cljs [[goog.string.format]])))

;;------
;; specs
;;------

;; paths
(def PathFragment [:and string? [:fn not-empty]])
(def FullPath PathFragment)
(def Path PathFragment)
(def PathPrefix PathFragment)
(def PathSuffix PathFragment)
(def PathOpts [:map
               [::full-path FullPath]
               [::path Path]
               [::path-prefix PathPrefix]
               [::path-suffix PathSuffix]])

;; expanders
(def ExpanderName keyword?)
(def ExpanderOpts map?)
(def ExpanderWithOpts [:catn
                       [:expander-name ExpanderName]
                       [:expander-opts [:? ExpanderOpts]]])
(def PathExpander [:catn
                   [:path PathFragment]
                   [:expander-opts [:? ExpanderOpts]]])
(def Expander [:orn
               [:expander-name ExpanderName]
               [:expander-with-opts ExpanderWithOpts]
               [:path-expander PathExpander]])

(def ExpandWith [:sequential Expander])

;; namespace-route
(def RouteName keyword?)
(def GenerateRoute map?)
(def NameRoute [:catn
                [:route-name RouteName]
                [:generate-route [:? GenerateRoute]]])

;; plain ol' path route
(def Handler any?)
(def PathRouteOpts [:map [:handler Handler]])
(def PathRoute [:catn
                [:path Path]
                [:path-route-opts PathRouteOpts]])

(def SugaredRoutes [:sequential
                    [:orn
                     [:expander-opts ExpanderOpts]
                     [:name-route NameRoute]
                     [:path-route PathRoute]]])

;;------
;; utils
;;------

(defn- path
  [{:keys [::full-path ::path ::path-prefix ::path-suffix] :as opts}]
  (or full-path
      (->> [path-prefix path path-suffix]
           (map (fn [s] (if (fn? s) (s opts) s)))
           (remove empty?)
           (str/join ""))))

(defn- dissoc-opts
  "the final routes don't need to be cluttered with options specific to route expansion"
  [opts]
  (dissoc opts
          ::base-name
          ::full-path
          ::path
          ::path-prefix
          ::path-suffix
          ::expand-with
          ::expander-opts))

(defn- generate-route
  "generates a route for an expander"
  [nsk expander defaults opts]
  (let [route-opts (merge {::ns   nsk
                           ::type expander}
                          defaults
                          opts
                          (::expander-opts opts))]
    [(path route-opts) (dissoc-opts route-opts)]))

;;------
;; expansion
;;------

(defmulti expand-with
  (fn [_nsk expander _opts]
    (if (string? expander)
      ::path
      (let [ns (keyword (namespace expander))
            n  (keyword (name expander))]
        (cond (and (= ns :collection) (some? n)) ::collection-child
              (and (= ns :member) (some? n))     ::member-child
              :else                              expander)))))

;; handles expanders like ["/some/path" {:name :xyz}]
(defmethod expand-with
  ::path
  [nsk path {:keys           [::base-name]
             {:keys [:name]} ::expander-opts
             :as             opts}]
  (when-not (keyword? name)
    (throw (ex-info "You must supply a :name for paths in :expand-with e.g. [\"/foo\" {:name :foo}]"
                    {:path            path
                     :route-namespace nsk})))
  (generate-route nsk
                  name
                  {:name  name
                   ::type name
                   ::path (u/fmt "/%s%s" (u/slash base-name) path)}
                  opts))

;; keys like :member/some-key are treated like
;; ["/ent-type/{id}/some-key" {:name :member/some-key}]
(defmethod expand-with
  ::member-child
  [nsk expander {:keys [::base-name] :as opts}]
  (generate-route nsk
                  expander
                  {:name   (keyword base-name (name expander))
                   ::path  (fn [{:keys [id-key] :as o}]
                             (u/fmt "/%s/{%s}/%s"
                                    (u/slash (::base-name o))
                                    (u/full-name id-key)
                                    (name expander)))}
                  opts))

(defmethod expand-with
  :collection
  [nsk expander {:keys [::base-name] :as opts}]
  (generate-route nsk
                  expander
                  {:name  (keyword (str base-name "s"))
                   ::path (str "/" (u/slash base-name))}
                  opts))

(defmethod expand-with
  :member
  [nsk expander {:keys [::base-name] :as opts}]
  (generate-route nsk
                  expander
                  {:name   (keyword base-name)
                   ::path  (fn [{:keys [id-key] :as o}]
                             (u/fmt "/%s/{%s}"
                                    (u/slash base-name)
                                    (u/full-name id-key)))}
                  opts))

;; singletons use the :collection path and the :member name
(defmethod expand-with
  :singleton
  [nsk expander {:keys [::base-name] :as opts}]
  (generate-route nsk
                  expander
                  {:name  (keyword base-name)
                   ::path (str "/" (u/slash base-name))}
                  opts))

(defn ent-type-id-key
  [ent-type route-ent-types expand-route-opts]
  (or (:id-key expand-route-opts)
      (ent-type route-ent-types)
      (:default-id-key route-ent-types)
      :id))

(defn expand-route
  "In a pair of [n m], if n is a keyword then the pair is treated as a
  name route and is expanded. Otherwise the pair is returned
  as-is (it's probably a regular reitit route).

  `delimiter` is a regex used to specify what part of the name to
  ignore. By convention Donut expects you to use names like
  `:my-app.backend.endpoint.user`, but you want to just use `user` to
  generate paths and route names - that's what the delimiter is for."
  ([pair] (expand-route pair nil #"endpoint\."))
  ([pair route-ent-types] (expand-route pair route-ent-types #"endpoint\."))
  ([[ns opts :as pair] route-ent-types delimiter]
   (if (m/validate NameRoute pair)
     (let [base-name (-> (str ns)
                         (str/split delimiter)
                         (second))
           ent-type  (-> base-name
                         (str/split #"\.")
                         last
                         keyword)
           expanders (::expand-with opts [:collection :member])
           opts      (assoc opts
                            ::base-name base-name
                            :ent-type ent-type
                            :id-key (ent-type-id-key ent-type route-ent-types opts))]
       (when-let [explanation (m/explain ExpandWith expanders)]
         (throw (ex-info (str "Invalid route expanders for " ns)
                         {:explain       explanation
                          :explain-human (me/humanize explanation)})))
       (reduce (fn [routes expander]
                 (let [expander-opts (if (sequential? expander) (second expander) {})
                       expander      (if (sequential? expander) (first expander) expander)]
                   (when-let [explanation (m/explain ExpanderOpts expander-opts)]
                     (throw (ex-info (str "Invalid expander opts for " ns)
                                     {:explain       explanation
                                      :explain-human (me/humanize explanation)})))
                   (conj routes (expand-with ns expander (assoc opts ::expander-opts expander-opts)))))
               []
               expanders))
     [pair])))

(defn expand-routes
  "Returns vector of reitit-compatible routes from compact route syntax

  `delimiter` is a regex used to split the namespace \"base\" from its
  domain component: `foo.endpoint.user` -> `user`"
  ([pairs]
   (expand-routes pairs nil #"endpoint\."))
  ([pairs route-ent-types]
   (expand-routes pairs route-ent-types #"endpoint\."))
  ([pairs route-ent-types delimiter]
   (loop [common                {}
          [current & remaining] pairs
          routes                []]
     (cond (not current)  routes
           (map? current) (recur current remaining routes)
           :else          (recur common
                                 remaining
                                 (into routes (expand-route (update current 1 #(mm/meta-merge common %))
                                                            route-ent-types
                                                            delimiter)))))))

(defn sanitize
  "Walks expanded routes, replacing a value `v` with `(tx v)` when `(pred v)` is
  true. Useful in replacing donut refs (or integrant refs) in cljs, because those refs
  probably refer to a backend component, not a frontend one."
  [expanded-routes pred tx]
  (walk/postwalk (fn [x] (if (pred x) (tx x) x))
                 expanded-routes))

;;---
;; assoc routes with endpoint defs
;;---

#?(:clj
   (do
     (defn resolve-handlers
       [ns-name handlers-sym]
       (if-let [handlers (ns-resolve (find-ns ns-name) handlers-sym)]
         @handlers
         (throw (ex-info (str "could not find " ns-name "/" handlers-sym)
                         {}))))

     (defn merge-handlers
       "For each donut endpoint route, looks up the var `handlers` in the corresponding
        namespace, then looks up `:collection`, `:member`, etc. The value returned
        gets merged directly into the route's options. It should be a valid map for a reitit route,
        e.g. `{:get {:handler ...}}`"
       ([routes]
        (merge-handlers routes 'handlers))
       ([routes handlers-sym]
        (mapv (fn [[_ opts :as r]]
                ;; TODO handle exception
                (if-let [ns-name (some-> (::ns opts) symbol)]
                  (do
                    (when-not (find-ns ns-name)
                      (throw (ex-info (str "endpoint ns " ns-name " referenced but not required") {})))
                    (update r 1 merge (get (resolve-handlers ns-name handlers-sym)
                                           ;; ::type is :collection, :member, etc
                                           (::type opts))))
                  r))
              routes)))))

;;---
;; helpers
;;---

(defn routes-by-name
  "produces a map with routes keyed by name"
  [routes & [filter*]]
  (cond->> routes
    (fn? filter*)                 (filter filter*)
    (= (type #"") (type filter*)) (filter (fn [[path]] (re-find filter* path)))
    true                          (u/key-by (comp :name second))))

(defn simple-routes
  "stripped down view of routes"
  [routes]
  (mapv (fn [route] (update route 1 :name))
        routes))
