;;; Copyright © 2010-2013 Fraunhofer Gesellschaft
;;; Licensed under the EPL V.1.0

(ns ^{:doc "Various utilities functions"}
  lacij.utils.core
  (:use clojure.pprint))

(def debug false)

(defn by-two
  [i]
  (int (/ i 2)))

(defmacro p [x]
  (let [s (str x " = ")]
    (when debug
     `(do
        (prn ~s)
        (pprint ~x)
        (prn)))))

(defn leafs-seq
  [branch? children root]
  (let [leafs (fn leafs [node]
                (lazy-seq
                 (if-not (branch? node)
                   [node]
                   (mapcat leafs (children node)))))]
    (leafs root)))

(defn graph-seq
  "Returns a sequence of the nodes in the graph traversed with a depth-first manner"
  [branch? children root]
  (let [walk (fn walk [tovisit visited visited-nodes]
               (let [[node & restnodes] tovisit]
                 (cond (nil? node)
                       visited-nodes

                       (not (contains? visited node))
                       (recur (concat restnodes (when (branch? node) (children node)))
                              (conj visited node)
                              (conj visited-nodes node))

                       :else
                       (recur restnodes visited visited-nodes))))]
     (walk [root] #{} [])))

(defn divide
  "Divides the collection into two collections of the same number of elements,
   with the same order. If the number of elements in the collection is odd
   then the first divided collection has one element more than the second."
  [col]
  (split-at (int (Math/ceil (/ (count col) 2))) col))

(defn index-of
  "Returns the index of an element in a collection. This performs
   a linear search."
  [col x]
  (second (first (filter #(= (first %) x) (partition 2 (interleave col (iterate inc 0)))))))
