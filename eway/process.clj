(ns eway.process
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]))

(defn pid
  "Returns the pid of the current process."
  []
  (.pid (java.lang.ProcessHandle/current)))

(defn success?
  "Returns true if sh result exited with code 0."
  [{:keys [exit]}]
  (= 0 exit))

(defn $>
  [args]
  (let [result (apply shell/sh args)]
    (when-not (success? result)
      (throw (ex-info (str "Command " (pr-str args) " failed.") result)))
    (-> result
        :out
        str/trim-newline)))

(defn complete?
  "Returns true if process has completed."
  [process]
  (try
    (.exitValue process)
    true
    (catch IllegalThreadStateException _
      false)))

(defn ->map
  [process]
  {:out  (-> process .getInputStream slurp)
   :err  (-> process .getErrorStream slurp)
   :exit (.exitValue process)})
