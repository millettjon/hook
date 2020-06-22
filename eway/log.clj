(ns eway.log
  (:require [fipp.edn :refer [pprint]]
            [taoensso.timbre :refer [info] :as t]
            [taoensso.timbre.appenders.3rd-party.rolling :refer [rolling-appender]]
            [taoensso.encore :as enc]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [eway.path :as path]
            [tick.alpha.api :as tick]
            [tick.format]
            [clansi]
            [com.stuartsierra.component :as component]
            [tarayo.core :as tarayo]))

(defn- colorize
  "Colors to use for log levels."
  [level s]
  (let [styles (case level
                 :trace  [:blue]
                 :debug  [:blue]
                 :info   [:white]
                 :warn   [:bright :yellow]
                 :error  [:bright :red]
                 :fatal  [:underline :bright :red]
                 :report [:underline :bright :red])]
    (apply clansi/style s styles)))

(defn- unwrap
  "Unwraps single element collections."
  [args]
  (if (= 1 (count args)) (first args) args))

(def last-minute
  (let [duration (tick/new-duration 1 :minutes)
        start (tick/- (tick/zoned-date-time) duration)]
    (atom {:instant start
           :duration duration
           :formatter (tick.format/formatter "yyyy-MM-dd HH:mm:ss")})))

(defn minute-header
  [inst]
  (let [new-instant (-> inst tick/zoned-date-time)
        {:keys [instant duration formatter]} @last-minute]
    (when (or (tick/< instant (tick/- new-instant duration))
              (not= (-> instant tick/minute)
                    (-> new-instant tick/minute)))
      (swap! last-minute assoc :instant new-instant)
      (let [s (tick/format formatter new-instant)]
        (clansi/style s
                      :bright :black :bg-white)))))

(defn colorize-event
 [{:keys [instant timestamp_ level vargs] :as data}]
  (when-let [s (minute-header instant)]
    (println s))
  (println #_(tiny-time instant)
           (colorize
            level
            (str/join " "
                      [(force timestamp_)
                       (unwrap vargs)]))))

(def base-config
  {:level :info
   :timestamp-opts t/default-timestamp-opts
   :output-fn      t/default-output-fn})

(def console-appender
  {:enabled?       true
   :async?         false
   :min-level      :info
   :timestamp-opts {:pattern  "ss.SSS"
                    :locale   :jvm-default
                    :timezone :jvm-default}
   :rate-limit     [[10 1000]] ; 10/1s
   :output-fn      colorize-event
   :fn             (fn [data]
                     (let [{:keys [output_]}    data
                           formatted-output-str (force output_)]
                       ;; TODO: What is this fn for?
                       ))})

#_ (def no-color-output
  (partial t/default-output-fn {:stacktrace-fonts {}}))

(def no-color-output
  (partial t/default-output-fn {:stacktrace-fonts {}}))

(defn build-subject
  [ns level]
  (str (str/upper-case (name level)) " "
       (-> ns
           (str/replace #"^eway\." "")
           (str/replace #"\.cli$" ""))))

;; TODO: Get rate limiting working.
;; TODO: Add rollup ability.
(defn email-appender
  "Returns an email appender."
  [ns {:keys [host port to]
       :or   {host "localhost"
              port 25}}]
  {:enabled?   true
   :async?     true  ; slow
   :min-level  :warn ; elevated
   :rate-limit [[5 (enc/ms :mins 2)]]
   :output-fn  no-color-output
   :fn         (fn [{:keys [msg_ hostname_ level ?ns-str ?line vargs]}]
                 (with-open [conn (tarayo/connect {:host host :port port})]
                   (let [hostname (force hostname_)]
                     (try
                       (tarayo/send! conn {:from    (str "alert-" hostname " <alert@ewayops.net>")
                                           :to      to
                                           :subject (build-subject ns level)
                                           :body    (str #_ (force msg_)
                                                         (unwrap vargs)
                                                         "\n\n" {:host           hostname
                                                                 :directory      (System/getProperty "user.dir")
                                                                 :main-namespace ns
                                                                 :file           ?ns-str
                                                                 :line           ?line})})
                       (catch Exception x
                         (prn "Failed to send email: " x))))))})

(defn log-file
   "Returns the path to log file given ns."
  [ns]
  (-> ns
      path/ns->path
      (path/sibling "log")))
#_ (log-file *ns*)

(defn file-appender
  [ns]
  (prn "ns" ns "log-file" (log-file ns))
  (merge (rolling-appender {:path (log-file ns)
                            :pattern :daily})
         #_{:output-fn no-color-output}))

(defrecord Log [config]
  component/Lifecycle
  (start [this]
    (let [{:keys [loggers main-ns]} config]

      (t/set-config! base-config)
      (prn "loggers" loggers)
      (when (:file loggers)
        (t/merge-config! {:appenders
                          {:file (file-appender main-ns)}}))
      (when (:email loggers)
        (t/merge-config! {:appenders
                          {:email (email-appender main-ns
                                                  {:to   (get-in config [:alert :mail])
                                                   :host (get-in config [:mail :relay])})}}))
      (when (or (:console loggers)
                (empty? loggers))
        (t/merge-config! {:appenders
                          {:console console-appender}})))
    this)

  (stop [this]
    (t/set-config! (merge base-config
                          console-appender))
    this))

(defmacro component
  "Creates a default logging component from the passed flags."
  [flags]
  `(->Log ~flags (str ~*ns*) {}))

(defn init*
  [config]
  (.start (->Log config)))
