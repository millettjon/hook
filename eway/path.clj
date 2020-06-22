(ns eway.path
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(defn exists?
  "Returns true if path exists."
  [path]
  (-> path io/file .exists))

(defn split
  [path]
  (str/split path #"/"))

(defn join
  [& paths]
  (str/join "/" (flatten paths)))

(defn parent
  "Returns the parent of path."
  [path]
  (-> path
      split
      butlast
      join))

(defn sibling
  [a b]
  (-> a
      parent
      (join b)))

(defn ns->path
  "Converts ns to a relative path."
  [ns]
  (-> ns
      str
      (str/replace "-" "_")
      (str/split #"\.")
      join))
