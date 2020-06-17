(ns eway.site.11214.subscriber-response-hook-test
  (:require [clojure.test :refer [deftest is]]
            [fipp.edn :refer [pprint]]
            [eway.site.11214.subscriber-response-hook :as hook]
            [clj-http.fake :refer [with-fake-routes]]
            [ring.util.codec :refer [form-decode]]
            [clojure.walk :refer [keywordize-keys]]))

(deftest build-params
  (is (= {:literal "foo"
          :kw "BAR"
          :fn 42}
         (hook/build-params
          {:params {:literal "foo"
                    :kw :bar
                    :fn [[:baz] #(inc %)]}}
          {:bar "BAR"
           :baz 41}))))

(deftest call-hook
  (let [{:keys [params] :as ep} hook/endpoint
        data {:status "345"
              :email "foo@bar.com"
              :text18 "012"
              :rti "678"}]
    (with-fake-routes
      {#"https.+" identity}
      (is (= (assoc params
                    :se_ac "345"
                    :se_la "foo@bar.com"
                    :se_pr "subid-012|rti-678")
             (-> (hook/call-hook ep data)
                 :query-string
                 form-decode
                 keywordize-keys))))))

(deftest init-riak
  (let [[get-fn set-fn] (hook/init-riak (update hook/riak-conf
                                                :bucket
                                                #(str % ".test")))
        id 1000M]
    (set-fn id)
    (is (= id (get-fn)))))
