(ns eway.site.11214.subscriber-response-hook.cli
  "Queries new subscriber responses for site 11214 (Canvaspeople) and
  calls the Jondo webhook for each."
  (:require [fipp.edn :refer [pprint]]
            [taoensso.timbre :refer [info warn]]
            [eway.app :as app]
            [eway.client.jondo.hook.subscriber-response :as hook]))

;; Template: https://sp.canvaspeople.com/i?e=se&p=srv&tv=no-js&se_ca=lead&se_ac={{ action }}&se_la={{ email }}&se_pr=subid-{{ text18 }}|rti-{{ rti }}&aid=cp_lead_tracker
;; Example: https://sp.canvaspeople.com/i?e=se&p=srv&tv=no-js&se_ca=lead&se_ac=new&se_la=<redacted>@yahoo.com&se_pr=subid--1|rti-117410&aid=cp_lead_tracker
;; Note: For small batch testing with client, append _test to the :aid parameter.
(def endpoint
  {:url    "https://sp.canvaspeople.com/i"
   :params {:e     "se"
            :p     "srv"
            :tv    "no-js"
            :se_ca "lead"
            :se_ac :status
            :se_la :email
            :se_pr [[:text18 :respondingtoid] #(str "subid-" %1 "|rti-" %2)]
            :aid   "cp_lead_tracker"}})

(defn process
  [config]
  (hook/process-new-rows (assoc config
                                :site-id 11214
                                :endpoint endpoint)))

(defn -main
  [& _]
  (app/main process))

(comment
  (with-bindings {#'eway.config/*profile* :dev}
    #_ (-main)
    (process (eway.config/read-config))

    #_ (hook/current-id (eway.config/read-config))
    #_ (hook/max-subscriber-response-id)
    #_ (hook/set-id (eway.config/read-config) 42)
    #_ (hook/set-id-to-max (eway.config/read-config))
    )
)
