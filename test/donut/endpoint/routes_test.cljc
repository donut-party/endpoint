(ns donut.endpoint.routes-test
  (:require [donut.endpoint.routes :as sut]
            #?@(:clj [[clojure.test :refer :all]
                      [donut.endpoint.routes-load-handlers]]
                :cljs [[cljs.test :refer :all :include-macros true]])))

(deftest makes-routes
  (is (= [["/user"      {:name      :users
                         :ent-type  :user
                         ::sut/ns   :ex.endpoint.user
                         ::sut/type :collection}]
          ["/user/{id}" {:name      :user
                         :ent-type  :user
                         ::sut/ns   :ex.endpoint.user
                         ::sut/type :member
                         :id-key    :id}]]
         (sut/expand-route [:ex.endpoint.user]))))

(deftest nested-route
  (is (= [["/admin/user"      {:name      :admin.users
                               :ent-type  :user
                               ::sut/ns   :ex.endpoint.admin.user
                               ::sut/type :collection}]
          ["/admin/user/{id}" {:name      :admin.user
                               :ent-type  :user
                               ::sut/ns   :ex.endpoint.admin.user
                               ::sut/type :member
                               :id-key    :id}]]
         (sut/expand-route [:ex.endpoint.admin.user]))))

(deftest exclude-route
  (testing "if you specify route types only those are included"
    (is (= [["/user" {:name      :users
                      :ent-type  :user
                      ::sut/ns   :ex.endpoint.user
                      ::sut/type :collection}]]
           (sut/expand-route [:ex.endpoint.user {::sut/expand-with [:collection]}])))

    (is (= [["/user/{id}" {:name      :user
                           :ent-type  :user
                           ::sut/ns   :ex.endpoint.user
                           ::sut/type :member
                           :id-key    :id}]]
           (sut/expand-route [:ex.endpoint.user {::sut/expand-with [:member]}])))))

(deftest common-opts
  (testing "you can specify common opts and override them"
    (is (= [["/user" {:name      :users
                      :ent-type  :user
                      ::sut/ns   :ex.endpoint.user
                      ::sut/type :collection
                      :id-key    :weird/id}]
            ["/user/{weird/id}" {:name      :user
                                 :ent-type  :user
                                 ::sut/ns   :ex.endpoint.user
                                 ::sut/type :member
                                 :id-key    :weird/id}]
            ["/topic" {:name      :topics
                       :ent-type  :topic
                       ::sut/ns   :ex.endpoint.topic
                       ::sut/type :collection
                       :id-key    :db/id}]
            ["/topic/{db/id}" {:name      :topic
                               :ent-type  :topic
                               ::sut/ns   :ex.endpoint.topic
                               ::sut/type :member
                               :id-key    :db/id}]]
           (sut/expand-routes [{:id-key :db/id}
                               [:ex.endpoint.user {:id-key :weird/id}]
                               [:ex.endpoint.topic]])))))

(deftest shared-opts
  (testing "opts are shared across unary and coll"
    (is (= [["/user" {:name      :users
                      :ent-type  :user
                      ::sut/ns   :ex.endpoint.user
                      ::sut/type :collection
                      :a         :b}]
            ["/user/{id}" {:name      :user
                           :ent-type  :user
                           ::sut/ns   :ex.endpoint.user
                           ::sut/type :member
                           :a         :b
                           :id-key    :id}]]
           (sut/expand-routes [[:ex.endpoint.user {:a :b}]])))))

(deftest paths
  (testing "custom path construction"
    (is (= [["/boop" {:name      :users
                      :ent-type  :user
                      ::sut/ns   :ex.endpoint.user
                      ::sut/type :collection}]
            ["/boop" {:name      :user
                      :ent-type  :user
                      ::sut/ns   :ex.endpoint.user
                      ::sut/type :member
                      :id-key    :id}]]
           (sut/expand-routes [[:ex.endpoint.user {::sut/path "/boop"}]])))

    (is (= [["/user/x" {:name      :users
                        :ent-type  :user
                        ::sut/ns   :ex.endpoint.user
                        ::sut/type :collection}]
            ["/user/{id}/x" {:name      :user
                             :ent-type  :user
                             ::sut/ns   :ex.endpoint.user
                             ::sut/type :member
                             :id-key    :id}]]
           (sut/expand-routes [[:ex.endpoint.user {::sut/path-suffix "/x"}]])))))

(deftest singleton
  (is (= [["/user" {:name      :user
                    :ent-type  :user
                    ::sut/ns   :ex.endpoint.user
                    ::sut/type :singleton}]]
         (sut/expand-routes [[:ex.endpoint.user {::sut/expand-with [:singleton]}]]))))

(deftest ent-children
  (testing "default handling of unknown route types"
    (is (= [["/user/{id}/boop" {:name      :user/boop
                                :ent-type  :user
                                ::sut/ns   :ex.endpoint.user
                                ::sut/type :member/boop
                                :id-key    :id}]
            ["/user/{id}/moop" {:name      :user/moop
                                :ent-type  :user
                                ::sut/ns   :ex.endpoint.user
                                ::sut/type :member/moop
                                :id-key    :id}]]
           (sut/expand-route [:ex.endpoint.user {::sut/expand-with [:member/boop :member/moop]}]))))

  (testing "respects id key for unknown route types"
    (is (= [["/user/{oop/id}/boop" {:name  :user/boop
                                    :ent-type  :user
                                    ::sut/ns   :ex.endpoint.user
                                    ::sut/type :member/boop
                                    :id-key    :oop/id}]
            ["/user/{oop/id}/moop" {:name  :user/moop
                                    :ent-type  :user
                                    ::sut/ns   :ex.endpoint.user
                                    ::sut/type :member/moop
                                    :id-key    :oop/id}]]
           (sut/expand-route [:ex.endpoint.user {::sut/expand-with [:member/boop :member/moop]
                                                 :id-key      :oop/id}])))))

(deftest custom-expand-with
  (testing "you can customize expanders in expand-with"
    (is (= [["/user/{oop/id}/boop" {:name      :user/boop
                                    :ent-type  :user
                                    ::sut/ns   :ex.endpoint.user
                                    ::sut/type :member/boop
                                    :id-key    :oop/id}]
            ["/user/{id}/moop" {:name      :user/moop
                                :ent-type  :user
                                ::sut/ns   :ex.endpoint.user
                                ::sut/type :member/moop
                                :id-key    :id}]
            ["/user/abc/bpp" {:name      :user/abc-bpp
                              :ent-type  :user
                              ::sut/ns   :ex.endpoint.user
                              ::sut/type :user/abc-bpp
                              :id-key    :oop/id}]]
           (sut/expand-route [:ex.endpoint.user {::sut/expand-with [:member/boop
                                                                    [:member/moop {:id-key :id}]
                                                                    ["/abc/bpp" {:name :user/abc-bpp}]]
                                                 :id-key           :oop/id}])))))

(deftest customize-builtin-expander-paths
  (testing "you can provide options to built-in expanders' paths"
    (is (= [["/custom-path" {:name      :users
                             :ent-type  :user
                             ::sut/ns   :ex.endpoint.user
                             ::sut/type :collection}]
            ["/api/v1/user/{id}" {:id-key    :id
                                  :name      :user
                                  :ent-type  :user
                                  ::sut/type :member
                                  ::sut/ns   :ex.endpoint.user}]]
           (sut/expand-route [:ex.endpoint.user {::sut/expand-with [[:collection {::sut/full-path "/custom-path"}]
                                                                    [:member     {::sut/path-prefix "/api/v1"}]]}])))))

(deftest merge-handlers-test
  (testing "merges the handlers in donut.endpoint.routes-load-handlers/handlers with route opts"
    (is (= [["/routes-load-handlers"
             {::sut/ns   :donut.endpoint.routes-load-handlers
              ::sut/type :collection
              :name      :routes-load-handlerss
              :ent-type  :routes-load-handlers
              :get       {:handler :foo}}]
            ["/routes-load-handlers/{id}"
             {::sut/ns   :donut.endpoint.routes-load-handlers
              ::sut/type :member
              :name      :routes-load-handlers
              :ent-type  :routes-load-handlers
              :id-key    :id
              :post      {:handler :bar}}]]
           (-> [[:donut.endpoint.routes-load-handlers]]
               (sut/expand-routes #"endpoint\.")
               (sut/merge-handlers))))))
