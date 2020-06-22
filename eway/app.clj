(ns eway.app
  (:require [taoensso.timbre :as t]
            [eway.log :as log]
            [eway.config :as config]))

(defn main*
  [ns f]
  (let [code (try
               (t/info :start {:ns ns})
               (let [config (config/read-config* ns)]
                 ;; TODO: redact secrets if any
                 (t/info :load-config config)
                 (log/init* config ns)
                 (f config))
               0
               (catch Exception x
                 (t/error :catch x)
                 ;; Sleep a bit in case logger needs to send email.
                 (Thread/sleep 5000)
                 1))]
    (t/info :exit {:ns ns :code code})
    (System/exit code)))

(defmacro main
  "Helper to call main* with current namespace."
  [f]
  `(main* ~*ns* ~f))
