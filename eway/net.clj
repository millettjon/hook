(ns eway.net
  (:require [eway.process :refer [$>]]))

(defn hostname
  "Returns the name of the local host."
  []
  ($> ["hostname"]))
