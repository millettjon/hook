(ns eway.client.jondo.hook.subscriber-response
  (:require [fipp.edn :refer [pprint]]
            [taoensso.timbre :refer [info warn]]
            [yesql.core :refer [defquery]]
            [clj-http.client :as client]
            [eway.component :as ec]
            [eway.riak :as r]
            [eway.path :as path]
            [diehard.core :as dh]))

;; ==================================================
;; HTTP CLIENT
;; ==================================================

(defn build-params
  "Builds an http parameter map from endpoint params and data."
  [{:keys [params]} data]
  (reduce-kv (fn [m key val]
               (assoc m key (cond
                              ;; if keyword, then lookup value from data
                              (keyword? val) (data val)
                              ;; if vector, then lookup values from data and call function
                              (vector? val) (let [[args f] val]
                                              (apply f (map #(data %) args)))
                              ;; else, use val unchanged
                              :else val)))
             {}
             params))

(def default-http-options
  "Default http request options. Timeouts are in ms."
  {:connection-timeout (* 10 1000)
   :socket-timeout     (* 10 1000)

   ;; They send back a cookie with an invalid expires. Just ignore it since
   ;; it generates a warning.
   :cookie-policy      :none})

(defn call-hook
  [{:keys [url] :as endpoint} data]
  (let [params (build-params endpoint data)]
    #_ (pprint params)
    (client/get url (merge default-http-options
                           {:query-params params}))))

#_ (let [data {:action "signup"
               :email "jon@example.com"
               :text18 "blue shoes"
               :rti "123"}]
     #_ (pprint (build-params endpoint data))
     (pprint (call-hook endpoint data)))

;; ==================================================
;; DATABASE
;; ==================================================

;; Note: These work w/ yesql.
;; Format: jdbc:oracle:thin:@hostname:portNumber:databaseName
;; yesql parameters must use _ instead of - for word separation
(def db-spec
  "JDBC Connection parameters"
  {:classname "oracle.jdbc.OracleDriver"
   :subprotocol "oracle:thin"
   :subname "@192.168.30.140:1521:ewayp1"
   :user     "ixsmember"
   :password "rtnbe6"})


(def sql-file
  (partial path/sibling (path/ns->path *ns*)))

(defquery get-sysdate (sql-file "sysdate.sql")
  {:connection db-spec})
#_ (get-sysdate)

(defquery max-subscriber-response-id (sql-file "max-subscriber-response-id.sql")
  {:connection db-spec})
#_ (max-subscriber-response-id)

(defquery get-subscriber-responses (sql-file "get-subscriber-response.sql")
  {:connection db-spec})
#_ (let [id   1297428031M
         rows (get-subscriber-response {:subscriber_response_id id})]
     (doseq [row rows]
       (println "--------------------------------------------------")
       (pprint row)))


;; ==================================================
;; RIAK
;; ==================================================

;; docs: http://clojureriak.info/articles/kv.html#about_this_guide
;; code: https://github.com/michaelklishin/welle

(def riak-key
  "subscriber_response_id")

(defn riak-fns
  [{:keys [get-fn set-fn]}]
  {:get-id (partial get-fn riak-key)
   :set-id (partial set-fn riak-key)})

;; ==================================================
;; HELPER FUNCTIONS
;; ==================================================

(defn current-id
  "Returns the current id."
  [{:keys [riak]}]
  (r/with-riak [riak riak]
    {:id (-> riak riak-fns :get-id .invoke)}))

(defn set-id
  [{:keys [riak]} id]
  (r/with-riak [riak riak]
    (-> riak riak-fns :set-id (apply [id]))))

(defn set-id-to-max
  [{:keys [riak]}]
  (let [id (-> (max-subscriber-response-id)
               ffirst
               second)]
    (r/with-riak [riak riak]
      (-> riak riak-fns :set-id (apply [id])))))

(defn lock-status
  [{:keys [riak]}]
  (r/with-riak [riak riak]
    (r/lock-status riak)))

;; ==================================================
;; MAIN PROCESSING LOOP
;; ==================================================

(defn process-conf
  [profile]
  (ec/assert-flag profile #{:dev :prod})
  {:hook-fn    (case profile
                   :dev #(pprint %2)
                   :prod call-hook)
   :batch-size (case profile
                   :dev 10
                   :prod nil)
   :sleep-ms   (case profile
                   :dev nil
                   :prod 500)})

(defn process-new-rows
  [{:keys [profile riak site-id endpoint]}]
  (ec/with-component [riak (r/component riak)]
    (r/with-lock riak
      (let [{:keys [hook-fn
                    batch-size
                    sleep-ms]} (process-conf profile)
            {:keys [get-id
                    set-id]}   (riak-fns riak)
            start-id           (get-id)
            rows               (get-subscriber-responses
                                {:site_id                site-id
                                 :subscriber_response_id start-id})
            rows               (if batch-size
                                 (take batch-size rows)
                                 rows)]
        (info {:start-id start-id
               :endpoint endpoint})
        (doseq [row rows]
            (hook-fn endpoint row)
            (info :row row)
            (set-id (row :subscriberresponseid))
            (when sleep-ms
              (Thread/sleep sleep-ms)))
        (info :stop {:final-id       (get-id)
                     :rows-processed (count rows)})))))

;; ==================================================
;; COMMAND LINE INTERFACE
;; ==================================================

(defn status
  [config]
  {:riak {:current-id (current-id config)
          :lock (lock-status config)}
   :db {:max-subscriber-response-id (-> (max-subscriber-response-id)
                                        ffirst
                                        second)}})

#_ (def clear-lock-cli status-cli)
#_ (def set-start-id-cli status-cli)
#_ (hook/set-id (eway.config/read-config) 42)
#_ (def call-webhook-cli status-cli)

(defn cli-fn
  [f]
  (fn [opts]
    (f opts)))

(defn build-cli
  [{:keys [site-id] :as config}]
  {:description (str "Send new subscriber responses for site " site-id " to Jondo using their webhook.")
   :subcommands [{:command "status"
                  :description "Show status of riak id, lock, and db id."
                  :runs (cli-fn (fn [_] (pprint (status config))))}
                 #_ {:command "set-start-id"
                  :description "Set starting subscriber response id for next query."
                  :runs (cli-fn (fn [{:keys [id]}]
                                  (set-id config) id))
                  :opts [{:option "id" :short 0 :as "subscriber response id" :type :int :default :present}]}
                 #_ {:command "clear-lock"
                  :description "Clear stuck lock. Useful after abnormal process termination."
                  :runs (cli-fn clear-lock-cli config)}
                 {:command "process"
                  :description "Query for new subscriber responses and send them to Jondo."
                  :runs (cli-fn (fn [_] (process-new-rows config)))}]})
