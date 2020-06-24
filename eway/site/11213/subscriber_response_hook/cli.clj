(ns eway.site.11213.subscriber-response-hook.cli
  "Queries new subscriber responses for site 11213 (Canvaspeople) and
  calls the Jondo webhook for each."
  (:require [taoensso.timbre :refer [info]]
            [eway.log :as el]
            [eway.config :refer [read-config]]
            [cli-matic.core :as cli]
            [eway.client.jondo.hook.subscriber-response :as hook]))

;; CuteKid Template:
;; https://sp.canvaspeople.com/i?e=se&p=srv&tv=no-js&se_ca=lead&se_ac={{ action }}&se_la={{ email }}&se_pr=subid-{{ text17 }}|rti-{{ rti }}&aid=ck_lead_tracker_eway
;;
;; Similar to canvas people endpoint except:
;; - text17 instead of text18
;; - different "aid" value
;;
;; Note: For small batch testing with client, append _test to the :aid parameter.
(def endpoint
  {:url    "https://sp.canvaspeople.com/i"
   :params {:e     "se"
            :p     "srv"
            :tv    "no-js"
            :se_ca "lead"
            :se_ac :status
            :se_la :email
            :se_pr [[:text17 :respondingtoid] #(str "subid-" %1 "|rti-" %2)]
            :aid   "ck_lead_tracker_eway"}})

(defn -main
  [& args]
  (let [config (assoc (read-config)
                      :site-id 11213
                      :endpoint endpoint)
        cli-config (hook/build-cli config)]
    (info :load-config config)
    (el/init* config)
    (cli/run-cmd args cli-config)))

(comment
  (with-bindings {#'eway.config/*profile* :prod}
    (let [config (assoc (read-config)
                        :site-id 11213
                        :endpoint endpoint)]
      #_ (hook/set-id-to-max config)
      (hook/status config)
      #_ (hook/process-new-rows config)
      )
    #_ (hook/max-subscriber-response-id)
    #_ (hook/set-id (eway.config/read-config) 42)
    )
  )
