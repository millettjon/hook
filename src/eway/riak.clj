(ns eway.riak
  (:require [com.stuartsierra.component :as component]
            [eway.component :as ec]
            [fipp.edn :refer [pprint]]
            [taoensso.timbre :refer [info]]
            [clojurewerkz.welle.core :as wc]
            [clojurewerkz.welle.buckets :as wb]
            [clojurewerkz.welle.kv :as kv]))

(defn fetch-val
  "Fetches and returns the value for key in bucket."
  [conn bucket key]
  (->> key
       (kv/fetch conn bucket)
       :result
       first
       :value))

(defn store-val
  "Stores value val under key in bucket."
  [conn bucket key val]
  (kv/store conn bucket key val {:content-type "application/clojure"}))


(def riak-flags
  ec/default-flags)

(defn riak-conf
  [flag ns]
  (ec/assert-flag flag riak-flags)
  {:endpoints ["cloud1" "cloud2" "cloud3" "cloud4" "cloud5"]
   :bucket    (case flag
                :prod ns
                :dev  (str ns "-test"))})
#_ (riak-conf :baz "foo.bar")

(defrecord Riak [flags ns config]
  component/Lifecycle
  (start [this]
    (info ::component :start {:flags flags :ns ns})
    (let [flags (ec/assert-1-flags
                 (if (empty? flags)
                   #{:dev}
                   flags)
                 riak-flags)
          conf  (riak-conf (first flags) ns)
          {:keys [endpoints bucket]} conf
          conn   (wc/connect-to-cluster-via-pb endpoints)]
      (wb/create conn bucket)
      (assoc this
             :flags flags
             :conn conn
             :get-fn (partial fetch-val conn bucket)
             :set-fn (partial store-val conn bucket))))

  (stop [{:keys [conn] :as this}]
    (info ::component :stop)
    (when conn
      (wc/shutdown conn))
    (assoc this :conn nil)))

(defn component*
  [flags ns]
  (component/using
   (->Riak flags (str ns) {})
   [:log]))

(defmacro component
  "Creates a default logging component from the passed flags."
  [flags]
  `(component* ~flags ~*ns*))

(comment
  (let [riak (component #{:dev})]
    (.start riak)
    (.stop riak))
  (ec/with-component [riak (component #{:dev})]
    (prn
     (let [{:keys [get-fn set-fn]} riak]
       (set-fn "buck" 2)
       (get-fn "buck")
       )))
)
