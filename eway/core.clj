(ns eway.core)

(defn deep-merge
  "Deeply merges maps so that nested maps are combined rather than replaced. "
  [& vs]
  (if (every? map? vs)
    (apply merge-with deep-merge vs)
    (last vs)))

;; with-component
;; singleton-lock
;; throttle
;; retry
;;
;; query
;; reduce
;;   process each row; print summary stats
;; map (i.e. ruby's each) (can it be used as is?a)

(defn exit-code
  [f]
  (try
    (f)
    0
    (catch Throwable _
      1)))

(defn log-ex
  [f]
  (try
    (f)
    (catch Throwable t
      (prn "log: ex" (str t))
      (throw t))
    (finally
      (prn "log: exiting"))))

(defn process
  [x]
  (+ x 1))

(defn bad-process
  []
  (throw (Exception. "boom")))

;; nest->
;;   opposite direction, target comes first
;;
;; fn->
;;   simplest
;;
;; % %2 to pass additional arguments to wrapped fn
;;   - useful to support this in -> and ->>?
;;
;; %:foo %2:bar pull named values from positional parameters
;;
;; wrap->fn
;; wrap->fn*

(comment
  ;; (#(identity %:foo) {:foo "FOO"})
  ;; currently supports % %& and %literal

  )


(defn wrap->fn*
  [& fns]

  )


(defmacro wrap->
  [form & forms]
  (let [forms (cons form forms)
        z     (last forms)
        forms (butlast forms)
        wrap  (fn [x] )]

    ;; last item
    (if (list? z)
      z
      (list z))
    ))

;; simple case
(comment
  (exit-code
   (fn []
     (log-ex
      (fn [] (bad-process)))))

  (wrap-> (process :dev)) ; simple case, just calls the fn

  ;; - ? Is it useful to call the final fn with any trailing args? Too confusing
  (wrap-> exit-code
          process
          :dev) ; too confusing
  (wrap-> exit-code
          (process :dev)) ; clear since all forms are functions and args to last fn are explicit
)
