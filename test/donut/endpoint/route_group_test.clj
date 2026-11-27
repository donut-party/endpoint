(ns donut.endpoint.route-group-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [donut.endpoint.route-group :as derg]
   [muuntaja.core :as m]
   [reitit.coercion.malli :as rcm]
   [reitit.ring :as rr]
   [ring.mock.request :as mock]))

(def handler
  (rr/ring-handler
   (rr/router
    ["/things"
     {:post {:parameters {:body [:map
                                 [:title [:string {:min 1}]]
                                 [:size :int]]}
             :handler    (fn [_] {:status 200 :body [[:ok {}]]})}}]
    {:data {:coercion   rcm/coercion
            :muuntaja   m/instance
            :middleware derg/route-middleware}})))

(defn post-things
  [body]
  (let [res (handler (-> (mock/request :post "/things")
                         (mock/json-body body)
                         (mock/header "accept" "application/edn")))]
    (update res :body #(m/decode m/instance "application/edn" %))))

(deftest request-coercion-errors-test
  (testing "coercion failures become 422 error segments keyed by attr path"
    (let [{:keys [status body]} (post-things {:title ""})]
      (is (= 422 status))
      (is (= [[:errors {:form  [derg/request-coercion-error-message]
                        :attrs {[:title] ["should be at least 1 character"]
                                [:size]  ["required"]}}]]
             body))))

  (testing "valid requests reach the handler"
    (let [{:keys [status body]} (post-things {:title "t" :size 1})]
      (is (= 200 status))
      (is (= [[:ok {}]] body)))))

(deftest other-exceptions-pass-through-test
  (let [h (derg/wrap-request-coercion-errors
           (fn [_] (throw (ex-info "boom" {:type ::other}))))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"boom" (h {})))))
