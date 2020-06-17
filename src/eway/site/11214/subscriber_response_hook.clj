(ns eway.site.11214.subscriber-response-hook
  (:require [fipp.edn :refer [pprint]]
            [taoensso.timbre :refer [info warn]]
            [yesql.core :refer [defquery]]
            [clj-http.client :as client]
            [eway.component :as ec]
            [eway.log :as el]
            [eway.riak :as r]
            [diehard.core :as dh]))

;; ==================================================
;; HTTP CLIENT
;; ==================================================

;; Template: https://sp.canvaspeople.com/i?e=se&p=srv&tv=no-js&se_ca=lead&se_ac={{ action }}&se_la={{ email }}&se_pr=subid-{{ text18 }}|rti-{{ rti }}&aid=cp_lead_tracker
;; Example: https://sp.canvaspeople.com/i?e=se&p=srv&tv=no-js&se_ca=lead&se_ac=new&se_la=<redacted>@yahoo.com&se_pr=subid--1|rti-117410&aid=cp_lead_tracker
(defn get-endpoint
  "Postback endpoint url and parameters."
  [flag]
  (ec/assert-flag flag #{:prod :dev})
  {:url    "https://sp.canvaspeople.com/i"
   :params {:e     "se"
            :p     "srv"
            :tv    "no-js"
            :se_ca "lead"
            :se_ac :status
            :se_la :email
            :se_pr [[:text18 :respondingtoid] #(str "subid-" %1 "|rti-" %2)]
            :aid   (case flag
                     :prod "cp_lead_tracker"
                     :dev  "cp_lead_tracker_test")}})

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
(def db-spec
  "JDBC Connection parameters"
  {:classname "oracle.jdbc.OracleDriver"
   :subprotocol "oracle:thin"
   :subname "@192.168.30.140:1521:ewayp1"
   :user     "ixsmember"
   :password "rtnbe6"})

(defquery get-sysdate "eway/site/11214/sysdate.sql"
  {:connection db-spec})
#_ (get-sysdate)

(defquery max-subscriber-response-id "eway/site/11214/max-subscriber-response-id.sql"
  {:connection db-spec})
#_ (max-subscriber-response-id)

(defquery get-subscriber-responses "eway/site/11214/get-subscriber-response.sql"
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

;; REPL HELPERS
;; TODO Make a CLI to call these.
(comment
  (ec/with-component [riak (r/component #{:prod})]
    ;; get current id
    (-> riak riak-fns :get-id .invoke)
    ;; set current id
    #_ (-> riak riak-fns :set-id (apply [42]))

    ;; set current id to max
    #_(let [id (-> (max-subscriber-response-id)
                 ffirst
                 second)]
      (-> riak riak-fns :set-id (apply [id]))))
  )

;; ==================================================
;; MAIN PROCESSING LOOP
;; ==================================================

(defn process-conf
  [profile]
  (ec/assert-flag profile #{:dev :prod})
  (prn "process-conf" profile)
  {:hook-fn    (case
                   :dev #(pprint %2)
                   :prod call-hook)
   :batch-size (case
                   :dev 10
                   :prod nil)
   :sleep-ms   (case
                   :dev nil
                   :prod 500)})

;; TODO: Save stacktraces in log file.
(comment
  (el/init! #{:console :file})
  (try
    (throw (RuntimeException. "fubar"))
    (throw (ex-info ":fubar" {:something :bad}))
    (catch Exception x
      (warn "boom" x)

      ))
  (fipp.edn/pprint (ex-info "boom" {:data :fubar}))
  )

(defn process-new-rows
  [profile]
  (ec/assert-flag profile #{:dev :prod})

  ;; Setup logging.
  (el/init! (case profile
              :dev  #{:console}
              :prod #{:console :file :email}))
  (info ::process :start {:profile profile})
  #_ (throw (ex-info ":fubar" {:something :bad}))

  (ec/with-component [riak (r/component #{profile})]
    (let [{:keys [hook-fn
                  batch-size
                  sleep-ms]} (process-conf profile)
          {:keys [get-id
                  set-id]}   (riak-fns riak)
          start-id           (get-id)
          rows               (get-subscriber-responses {:subscriber_response_id start-id})
          rows               (if batch-size
                               (take batch-size rows)
                               rows)
          endpoint           (get-endpoint profile)]
      (info {:endpoint endpoint})
      (info {:start-id start-id})
      (doseq [row rows]
        (hook-fn endpoint row)
        (info ::process :row row)
        (set-id (row :subscriberresponseid))
        (when sleep-ms
          (Thread/sleep sleep-ms)))
      (info ::process :stop {:final-id       (get-id)
                             :rows-processed (count rows)}))))
(comment
  (process-new-rows :dev)
  (process-new-rows :prod)
  )

(defn -main
  [& _]
  (info ::main :starting)
  (try
    (process-new-rows ec/profile)
    (catch Exception x
      (warn "exited with error" x)
      (Thread/sleep (* 1000 5)) ; sleep a bit to allow time for the email to go out
      ))
  (shutdown-agents)
  (info ::main :exiting))


;; TODO: add simple retry ability
;;   Ref: https://github.com/sunng87/diehard
;; TODO: load passwords from somewhere outside code
;; TODO: get rate throttling working for email logging
;; TODO: add unit tests
;;       - ? read only test user for database?
;; TODO: log (as data) elapsed time and total rows processed
;; TODO: Fix warning: update already refers to: #'clojure.core/update in namespace: clojurewerkz.welle.buckets, being replaced by: #'clojurewerkz.welle.buckets/update
;; TODO: handle exceptions (http, tcp conn/timeout, database, riak)
;; TODO: page if saved id is > N hours old (e.g., 2 hours)
;;       :sr_date field
;; TODO: get runing on batch1 and batch2
;; TODO: add nomad job
;; TODO: add unit tests
;; TODO: review riak persistence settings (client and server)
;; TODO: generate documentation and publish somewhere
;; TODO: switch to u log
;; TODO: add a double check helper to make sure it is running and check progress
;; TODO: make sure at most 1 copy can run at a time

;; QUERY BENCHMARK
;; - query took 3.5 minutes
;; 20-06-08 13:19:27 cloud1 INFO [eway.site.11214.subscriber-response-hook:155] - Starting
;; 20-06-08 13:23:01 cloud1 INFO [eway.site.11214.subscriber-response-hook:141] - Starting id: 1297446060
;; 20-06-08 13:23:12 cloud1 INFO [eway.site.11214.subscriber-response-hook:149] - Final id: 1297508866
;; 20-06-08 13:23:12 cloud1 INFO [eway.site.11214.subscriber-response-hook:150] - Processed 3076 rows.
;; 20-06-08 13:23:12 cloud1 INFO [eway.site.11214.subscriber-response-hook:157] - Exiting

;; RIAK BENCHMARK on cloud1 with default persistance settings:
;;   fetch: 2ms
;;   put: 3.5ms
;;   put + fetch: 5.3ms == 188.6/s
