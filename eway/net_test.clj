(ns eway.net-test
  (:require [clojure.test :refer [is deftest]]
            [clojure.string :as str]
            [eway.net :as net]))

(deftest hostname
  (let [name (net/hostname)]
    (is (not (nil? name))
        "hostname should not return nil")

    (is (= (-> "/etc/hostname"
               slurp
               str/trim-newline)
           name)
        "hostname should match /etc/hostname")))
