(ns eway.component)

(def default-flags
  #{:dev :prod})

(def profile
  (if-let [profile (System/getenv "EWAY_PROFILE")]
    (keyword profile)
    :dev))

(defn assert-flag
  [flag flag-set]
  (when-not (flag-set flag)
    (throw (ex-info (str ::unknown-flag)
                    {:flag flag
                     :expected flag-set})))
  flag)

(defn assert-1-flags
  [flags flag-set]
  (when (not= 1 (count flags))
    (throw (ex-info (str ::1-flag-expected) {:found flags})))
  (assert-flag (first flags) flag-set)
  flags)

;; TODO: Add check macros
;; define flags at top and check against case arg
;; warn if branch has unknown flag
;; are nil values ok if a branch is left out?
;;   - require default in that case?
;;   - require explicit?

(comment
  (let [flag :dev]
    (ec/case flag #{:dev :prod}
             :dev 1
             :prod 2
             )))

(comment
  (ec/with-flag flag #{:dev :prod}
    {:foo :bar
     :baz (ec/case
              :dev 1
              :prod 2)}))

;; case
;; fcase

;; - ? is default case allowed? YES
;; - ? is match required if there is no default case? YES



(defmacro with-component
  [[sym component] & body]
  `(let [~sym (.start ~component)]
     (try
       ~@body
       (finally
         (.stop ~sym)))))

(comment
  (fipp.edn/pprint
   (macroexpand-1
    '(with-component [riak (component #{:dev})]
       (prn "do stuff"))
    )))
