(ns eway.riak
  (:require [com.stuartsierra.component :as component]
            [eway.component :as ec]
            [eway.net :as net]
            [eway.process :as process]
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

(defn delete-val
  "Deletes the value under key in bucket."
  [conn bucket key]
  (kv/delete conn bucket key))

(defrecord Riak [config]
  component/Lifecycle
  (start [this]
    (let [{:keys [profile
                  ns
                  endpoints]} config
          bucket              (case profile
                                :prod ns
                                (str ns "-test"))
          conn                (wc/connect-to-cluster-via-pb endpoints)]
      (wb/create conn bucket)
      (info :start (assoc config :bucket bucket))
      (assoc this
             :conn conn
             :bucket bucket
             :get-fn (partial fetch-val conn bucket)
             :set-fn (partial store-val conn bucket)
             :delete-fn (partial delete-val conn bucket))))

  (stop [{:keys [conn] :as this}]
    (info :stop)
    (when conn
      (wc/shutdown conn))
    (assoc this :conn nil)))

(defn component
  [config]
  (component/using
   (->Riak config)
   [:log]))

(defmacro with-riak
  [[sym config] & body]
  `(ec/with-component [~sym (~component ~config)] ~@body))

#_ (macroexpand-1 '(with-riak [riak config] 42))

(def lock-key
  "Key to use when locking a bucket."
  "lock")

(defn lock-val
  "Returns the value to store under lock-key. Should represent the
  running process."
  []
  {:host (net/hostname)
   :pid (process/pid)})

(defn lock-status
  "Returns the value stored in the lock."
  [{:keys [get-fn]}]
  (get-fn lock-key))

(defn acquire-lock
  "Acquires a singleton lock. Throws an exception if the lock cannot
  be acquired. Returns true."
  [{:keys [get-fn set-fn]}]
  (let [k lock-key]
    (when-let [v (get-fn k)]
      (throw (ex-info "Failed to acquire lock" v)))
    (let [v (lock-val)]
      (set-fn k v)
      (when (not= v (get-fn k))
        (throw (ex-info "Failed to acquire lock" v)))
      (info :lock-aquired v)))
  true)

(defn release-lock
  "Releases the lock acquired by the current process (if any). Throws
  an exception if the lock is held by another process. Returns true."
  [{:keys [get-fn delete-fn]}]
  (let [k          lock-key
        v-expected (lock-val)
        v-actual   (get-fn k)]
    (when v-actual
      ;; Make sure lock is held by this process.
      (when (not= v-expected v-actual)
        (throw (ex-info "Failed to release lock. Lock is held by other process." {:expected v-expected :actual v-actual})))
      ;; release lock
      (delete-fn k)
      (info :lock-released)))
  true)

(defn clear-lock
  "Clears the lock. Useful to clean up after a process that exited
  without releasing the lock. Returns true if a lock was cleared and
  false if no lock was found."
  [{:keys [get-fn delete-fn]}]
  (let [k lock-key
        v (get-fn k)]
    (if v
      (do
        ;; release lock
        (delete-fn k)
        (info :lock-cleared v)
        true)
      (do
        (info "no lock was present")
        false))))

(defmacro with-lock
  "Acquires a singleton lock, calls body, release the lock when body
  exits and returns the value returned by body."
  [riak & body]
  `(try
     (~acquire-lock ~riak)
     ~@body
     (finally
       (~release-lock ~riak))))
