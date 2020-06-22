(ns eway.riak-test
  (:require [clojure.test :refer [deftest is]]
            [eway.riak :as r]))

(deftest lock
  ;; working cases
  (is (= "42"
         (r/with-lock
           #{:dev}
           (str 42)))
      "with-lock should call the inner form and return its value")

  (is (thrown? clojure.lang.ExceptionInfo
               (r/with-lock #{:dev}
                 (r/with-lock #{:dev} (str 42))))
      "an exception should be thrown if the lock cannot be acquired"))
