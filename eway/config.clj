(ns eway.config
  (:require [fipp.edn :refer [pprint]]
            [taoensso.timbre :refer [info]]
            [aero.core :as aero]
            [eway.path :as path]
            [eway.core :refer [deep-merge]]))

(def ^:dynamic *profile*
  (let [profile (or (some-> (System/getenv "EWAY_PROFILE")
                            keyword)
                    :dev)]
    (info :using-profile profile)
    profile))

(defmethod aero/reader 'opt
  [opts tag value]
  (get opts value))

#_ (aero/read-config "/opt/eway/etc/config.edn" {:profile :prod
                                              :main-ns :foo.bar})

(defn read-config*
  "Returns a merged map of standard configurations.
  The following files are merged in order:
  (global)   config.edn in /opt/eway/etc
  (project)  config.edn project root (should be the current directory)
  (namespace config.edn in the namespace of the running script.)"
  ([ns]
   (read-config* *profile* ns))
  ([profile ns]
   (let [opts {:profile profile
               :main-ns (str ns)}
         conf (->> [;; global settigns
                    "/opt/eway/etc/config.edn"
                    ;; project settings
                    "config.edn"
                    ;; namespace settings
                    (-> ns
                        path/ns->path
                        (path/sibling "config.edn"))]
                   (filter path/exists?)
                   (map #(aero/read-config % opts))
                   (apply deep-merge))]
     (assoc conf
            :profile profile
            :main-ns ns))))

(defmacro read-config
  "Helper to call read-config* with current namespace."
  ([]
   `(read-config* ~*ns*))
  ([profile]
   `(read-config* ~profile ~*ns*)))
