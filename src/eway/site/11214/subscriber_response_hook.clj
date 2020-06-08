(ns eway.site.11214.subscriber-response-hook
  (:require [fipp.edn :refer [pprint]]
            [taoensso.timbre :refer [info]]
            [yesql.core :refer [defquery]]
            [clj-http.client :as client]
            [clojurewerkz.welle.core :as wc]
            [clojurewerkz.welle.buckets :as wb]
            [clojurewerkz.welle.kv :as kv]))

;; ==================================================
;; HTTP CLIENT
;; ==================================================

;; Template: https://sp.canvaspeople.com/i?e=se&p=srv&tv=no-js&se_ca=lead&se_ac={{ action }}&se_la={{ email }}&se_pr=subid-{{ text18 }}|rti-{{ rti }}&aid=cp_lead_tracker
;; Example: https://sp.canvaspeople.com/i?e=se&p=srv&tv=no-js&se_ca=lead&se_ac=new&se_la=<redacted>@yahoo.com&se_pr=subid--1|rti-117410&aid=cp_lead_tracker
(def endpoint
  "Postback endpoint url and parameters."
  {:url    "https://sp.canvaspeople.com/i"
   :params {:e     "se"
            :p     "srv"
            :tv    "no-js"
            :se_ca "lead"
            :se_ac :action
            :se_la :email
            :se_pr [[:text18 :rti] #(str "subid-" %1 "|rti-" %2)]
            :aid   "cp_lead_tracker"}})

(defn build-params
  "Builds an http parameter map from endpoint params and data."
  [{:keys [params] :as endpoint} data]
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
   :socket-timeout     (* 10 1000)})

(defn call-hook
  [{:keys [url] :as endpoint} data]
  (let [params (build-params endpoint data)]
    (client/get url (merge default-http-options
                           {:query-params params}))
    #_ (client/post url {:form-params params})
    #_ (client/put url {:form-params params})
    ))

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
(def riak-conf
  {:endpoints ["cloud1" "cloud2" "cloud3" "cloud4" "cloud5"]
   :bucket    "eway.site.11214"
   :key       "subscriber_response_id"})

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

(defn init-riak
  "Initializes riak client and returns a pair of functions to get and
  set the stored id."
  []
  (let [{:keys [endpoints
                bucket
                key]} riak-conf
        riak          (wc/connect-to-cluster-via-pb endpoints)
        _             (wb/create riak bucket)
        get-fn        (partial fetch-val riak bucket key)
        set-fn        (partial store-val riak bucket key)]
    [get-fn set-fn]))

;; ==================================================
;; MAIN PROCESSING LOOP
;; ==================================================

(defn -main
  [& _]
  (info "Starting")
  (let [[get-id set-id] (init-riak)
        id (get-id)
        rows (get-subscriber-responses {:subscriber_response_id id})]
    (info "Starting id:" id)
    (doseq [row rows]
      ;; process row
      #_ (call-hook endpoint row)
      ;; (println "--------------------------------------------------")
      ;; (pprint row)
      (set-id (row :subscriberresponseid))
      #_ (Thread/sleep 1000))
    (info "Final id:" (get-id))
    (info "Processed" (count rows) "rows.")
    (info "Exiting")
    (System/exit 0)))
#_ (-main)

;; TODO: select HTTP request method
;; TODO: alert on errors (postal?)
;; TODO: add unit tests
;; TODO: log (as data) elapsed time and total rows processed
;; TODO: Fix warning: update already refers to: #'clojure.core/update in namespace: clojurewerkz.welle.buckets, being replaced by: #'clojurewerkz.welle.buckets/update
;; TODO: handle exceptions (http, tcp conn/timeout, database, riak)
;; TODO: configure logging
;; TODO: configure http-client to discard cookies to silence bad cookie warning
;; TODO: handle exceptions and alert
;; TODO: page if saved id is > N hours old (e.g., 2 hours)
;;       :sr_date field
;; TODO: get runing on batch1 and batch2
;; TODO: add nomad job
;; TODO: add unit tests
;; TODO: merge in with eway-clj
;; TODO: review persistence settings (client and server)

;; Riak Benchmarks on cloud1 with default persistance settings:
;;   fetch: 2ms
;;   put: 3.5ms
;;   put + fetch: 5.3ms == 188.6/s
