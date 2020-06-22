(ns eway.core-test
  (:require [clojure.test :refer [deftest is]]
            ;; [fipp.edn :refer [pprint]]
            [eway.core :refer [wrap->]]))

(deftest test-wrap->
  (is (thrown? RuntimeException (eval (read-string "(c/wrap-> )"))))

  #_ (macroexpand '(c/wrap-> ))
  #_ (macroexpand '#(foo %))

  ;; process      -> (process f)
  ;; (process)    -> (process f)
  ;; #(process %) -> (#(process) f)
  ;; (fn [f] )    -> ((fn [f] ) f)

  ;; Note: Make sure to quote forms with ` instead of '.
  ;; 1 fn
  (is (= `(process)
         (macroexpand `(wrap-> (process)))))
  (is (= `(process)
         (macroexpand `(wrap-> process))))

  (is (= `((fn [_] 42))
         (macroexpand `(wrap-> (fn [_] 42)))))
  (is (= `((fn [_] 42))
         (macroexpand `(wrap-> ((fn [_] 42))))))

  ;; The % gets expanded via gensym and has an unpredictable name so
  ;; just run the code and check the output.
  #_ (is (= `(#(inc %))
         (macroexpand `(wrap-> #(inc %)))))
  (is (= 42
         (wrap-> (inc 41))))
  (is (= 42
         (wrap-> (#(inc %) 41))))

  ;; 2 fns
  #_ (is (= `(exit-code (process))
         (macroexpand `(wrap-> exit-code
                        process))))

  ;; no parens
  ;; parens
  ;; parens w/ arg
  ;; parens w/ 2 args

  #_ (exit-code
   (fn []
     (log-ex
      (fn [] (bad-process)))))


  ;; (walk-> log-ex
  ;;         bad-process)
  ;; (log-ex bad-process)

  ;; (walk-> (log-ex {:context :foo})
  ;;         bad-process)
  ;; (log-ex bad-process {:context :foo})

  ;; (walk-> exit-code
  ;;         log-ex
  ;;         bad-process)
  ;; (exit-code
  ;;  (log-ex
  ;;   bad-process))

)
