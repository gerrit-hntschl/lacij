;;; Copyright © 2010-2013 Fraunhofer Gesellschaft
;;; Licensed under the EPL V.1.0

(ns ^{:doc "Implementation of the radial layout described in

            ﻿Wills, Graham J. 1999.
           'NicheWorks: Interactive Visualization of Very Large Graphs.'
            Journal of Computational and Graphical Statistics 8(2): 190.

            This layout looks well on graphs that are closed
            to a tree in their topology."}
  lacij.layouts.radiallayout
  (:use clojure.pprint
        lacij.model.graph
        lacij.edit.graph
        lacij.utils.core
        lacij.layouts.core
        lacij.opt.annealing
        lacij.layouts.utils.position)
  (:require [clojure.set :as set]
            [lacij.layouts.utils.topology :refer :all]
            [lacij.layouts.utils.cycle :refer [break-cycles flip-edges]]))

(defn f-in-children
  "Returns a function to get the in or out children of a node, depending of the
  flow. "
  [flow]
  (if (= flow :in)
    in-children
    out-children))

(defn assoc-node-to-layer
  "Updates the layer-data structures and set the layer of a
   given node."
  [layers-data layer node]
  (let [nodes (get (:layers layers-data) layer)
        nodes (conj nodes node)
        layers (assoc (:layers layers-data) layer nodes)]
    (assoc layers-data :layers layers)))

(defn add-to-tree
  "Adds children of nid to the tree."
  [tree nid children visited layer layers-data flow]
  (reduce (fn [[tree visited layers-data] child]
            (if (contains? visited child)
              [tree visited layers-data]
              (let [tree (add-node tree child (str (name child)))
                    eid (keyword (str (name nid) (name child)))
                    layers-data (assoc-in layers-data [:nodes child :layer] layer)
                    layers-data (assoc-node-to-layer layers-data layer child)
                    edge (if (= flow :in)
                           (add-edge tree eid child nid)
                           (add-edge tree eid nid child))]
                [edge (conj visited child) layers-data])))
          [tree visited layers-data]
          children))

(defn explore-border
  "Adds nid to the tree and its children and extends the current border."
  [nid graph nextborder visited layer tree layers-data flow]
  (let [children ((f-in-children flow) graph nid)
        [tree visited layers-data]
        (add-to-tree tree nid children visited layer layers-data flow)]
    [(concat nextborder children) visited tree layers-data]))

(defn build-tree-helper
  "Explores each node on the border and extends the border
   until all nodes have been reached. At the end of the process, we have
   a tree from the graph."
  [graph border visited layer tree layers-data flow]
  (if (seq border)
    (let [[nextborder visited tree layers-data]
          (reduce (fn [[nextborder visited tree layers-data] nid]
                    (explore-border nid graph nextborder
                                    visited layer tree layers-data flow))
                  [() visited tree layers-data]
                  border)]
      (recur graph nextborder visited (inc layer) tree layers-data flow))
    [tree layers-data]))

(defn add-virtual-root
  [graph virtualrootid roots flow]
  (let [graph (add-node graph virtualrootid)
        graph (reduce (fn [graph root]
                        (if (= flow :in)
                          (add-edge graph (geneid) root virtualrootid)
                          (add-edge graph (geneid) virtualrootid root)))
                      graph
                      roots)]
    graph))

(defn build-tree
  "Builds a tree from the graph and returns the layering information."
  ([graph flow rootnode]
     (let [tree (create-graph)
           tree (add-node tree rootnode (str (name rootnode)))
           layers-data {:layers [(list rootnode)] :nodes {rootnode {:layer 0}}}
           [tree layers-data] (build-tree-helper graph
                                                 [rootnode]
                                                 #{rootnode}
                                                 1
                                                 tree
                                                 layers-data
                                                 flow)]
       [tree layers-data]))
  ([graph flow]
     (let [roots (find-roots graph flow)]
       (if (= (count roots) 1)
         (build-tree graph flow (first roots))
         ;; if there are several roots, we add a virtual root node that
         ;; becomes the unique root
         (let [virtualrootid (gensym "virtualroot")
               graph (add-virtual-root graph virtualrootid roots flow)
               [tree layers-data] (build-tree graph
                                              flow
                                              virtualrootid)]
           [tree (assoc layers-data :has-virtual-root true)])))))

(defn leafs
  "Returns the leafs of a node in the tree."
  [tree nid flow]
  (leafs-seq #(seq ((f-in-children flow) tree %))
             #((f-in-children flow) tree %) nid))

(defn label-sizes
  "Calculates the sizes of all nodes in the tree. The size depends
   only of the number of leafs nodes."
  [tree layers-data flow]
  ;; this function could be optimized
  (reduce (fn [layers-data nid]
            (assoc-in layers-data [:sizes nid] (count (leafs tree nid flow))))
          layers-data
          (keys (:nodes tree))))

(defn label-angles-helper
  "Helper function to calculate the relative angle of each node in the tree."
  [tree layers-data nextchildren flow]
  (if-let [[[parentangle layer children] & res] nextchildren]
    (let [sizes (select-keys (:sizes layers-data) children)
          sum (apply + (vals sizes))
          [layers-data res]
          (reduce (fn [[layers-data res] child]
                    (let [size (get-in layers-data [:sizes child])
                          angle (* parentangle (/ size sum))
                          layers-data
                          (assoc-in layers-data [:angle child] angle)]
                      [layers-data (conj res [angle (inc layer)
                                              ((f-in-children flow) tree child)])]))
                  [layers-data res]
                  children)]
      (recur tree layers-data res flow))
    layers-data))

(defn label-angles
  "Calculates the relative angle of each node in the tree."
  [tree layers-data flow]
  (let [rootnode (ffirst (:layers layers-data))
        layers-data (assoc-in layers-data [:angle rootnode] 360)
        children ((f-in-children flow) tree rootnode)]
    (label-angles-helper tree layers-data [[360 2 children]] flow)))

(defn angle [layers-data nid]
  "Returns the relative angle of a node."
  (get-in layers-data [:angle nid]))

(defn assoc-children-data
  "Calculates various information for each node, such as the absolute angles
   and assoc them to the layers-data map."
  [layers-data children offset layer center-x center-y]
  (first
   (reduce (fn [[childrendata sum] child]
             (let [ang (angle layers-data child)
                   halfang (double (/ ang 2))
                   absolute-angle (+ sum halfang offset)
                   prevsum sum
                   sum (+ sum ang)]
               ;; (printf "child = %s offset = %s prevsum = %s sum = %s absolute = %s\n"
               ;;         child offset prevsum sum absolute-angle)
               [(conj childrendata {:child child
                                    :offset (+ offset prevsum)
                                    :absolute-angle
                                    absolute-angle
                                    :layer layer :center-x center-x :center-y center-y})
                sum]))
           [[] 0]
           children)))

(defn place-nodes-helper
  "Helper for the place nodes function."
  [graph tree layers-data radius nextchildren sort-children flow]
  (if (seq nextchildren)
    (let [[child & res] nextchildren]
      (let [{:keys [child absolute-angle offset layer center-x center-y]} child
            ang (angle layers-data child)
            angle-radian (Math/toRadians absolute-angle)
            r (* radius layer)
            x (* r (Math/sin angle-radian))
            y (* r (Math/cos angle-radian))
            x (+ x center-x)
            y (- center-y y)
            graph (move-node-center graph child x y)
            children-of-child (sort-children
                               graph
                               flow
                               ((f-in-children flow) tree child))
            reschildren (concat res (assoc-children-data
                                     layers-data
                                     children-of-child
                                     offset
                                     (inc layer)
                                     center-x
                                     center-y))]
        ;; (printf "  child = %s absolute = %s layer = %s  x = %s y = %s\n"
        ;;         child absolute-angle layer x y)
        (recur graph tree layers-data radius reschildren sort-children flow)))
    graph))

(defn place-nodes
  "Given layers-data and a tree, assign x-y coordinates to the nodes
   of the graph to build a hierarchical layout. "
  [graph tree layers-data options]
  (let [{:keys [width height radius sort-children]} options
        rootnode (ffirst (:layers layers-data))
        center-x (double (/ width 2))
        center-y (double (/ height 2))
        ;; _ (do (pprint layers-data))
        graph (if (:has-virtual-root layers-data)
                graph
                (move-node-center graph rootnode center-x center-y))
        children (assoc-children-data
                  layers-data (sort-children graph
                                             (:flow options)
                                             ((f-in-children (:flow options)) tree rootnode))
                  0 1 center-x center-y)]
    ;; (printf "root-node = %s center-x %s center-y %s\n" rootnode center-x center-y)
    (place-nodes-helper graph tree layers-data radius children sort-children (:flow options))))

;;; sort-children functions:

(defn indexed
  "Returns the collection as a collection of indexed element.
   Each indexed element is a vector [idx element]."
  [coll]
  (map vector (iterate inc 0) coll))

(defn proximity
  "Returns the proximity value between one child and one sibling, or the
   sum of proximity values of one child and its other siblings.

   The proximity value of one child and one sibling is 0 if they have
   no in-children in common, otherwise it is the value of the distance
   between the indexes of the two siblings. "
  ([graph idxchild1 child1 idxchild2 child2 flow]
     (let [children1 ((f-in-children flow) graph child1)
           children2 ((f-in-children flow) graph child2)
           stmt-in-common (not (empty? (set/intersection (set children1)
                                                         (set children2))))]
       (if stmt-in-common
         (inc (Math/abs (- idxchild2 idxchild1)))
         0)))
  ([graph [idxchild child] siblings flow]
     (reduce (fn [sum indexed-child2]
               (+ sum (proximity graph
                                 idxchild
                                 child
                                 (first indexed-child2)
                                 (second indexed-child2)
                                 flow)))
             0
             siblings)))

(defn entropy
  "Returns the entropy of a collection of children.
   The entropy is the sum of all proximity values for
   each children."
  [graph flow children]
  (let [indexed-children (indexed children)
        set-indexed-children (set indexed-children)
        size (count children)]
    (reduce (fn [sum n]
              (let [indexed-child (nth indexed-children n)
                    siblings (set/difference set-indexed-children #{indexed-child})]
                (+ sum (proximity graph indexed-child siblings flow))))
            0
            (range size))))

(defn swap
  "Swaps elements with indexes idx1 and idx2 in the vector coll."
  [coll idx1 idx2]
  (let [e1 (get coll idx1)
        e2 (get coll idx2)]
   (assoc coll idx1 e2 idx2 e1)))

(defn neighbour
  "Swaps randomly two elements in vector coll."
  [coll]
  (let [size (count coll)
        e1 (rand-int size)
        e2 (rand-int size)]
    (swap coll e1 e2)))

(defn neg-entropy
  "Returns the negated value of the entropy function."
  [graph flow children]
  (- (entropy graph flow children)))

(defn default-sort-children
  "The default sorting function for the radial layout algorithms.
   A simulatead annealing is used to reduce the number of crossings, i.e.
   to reduce the entropy of each layer."
  [graph flow children]
  (if (seq children)
    (optimize (vec children) (partial neg-entropy graph flow) neighbour
              :temp 20 :iterations 50 :calibration false)
    children))

(defn- greedy-break-cycles
  "Breaks the cycle in the graph, if any."
  [graph flow]
  (if (has-cycle? graph flow)
    (let [[graph flipped-edges] (break-cycles graph flow)]
      {:graph graph
       :flipped-edges flipped-edges
       :has-cycle true})
    {:graph graph :has-cycle false}))

(defn- restore-cycles
  "Restores the cycle in the graph"
  [graph flipped]
  (flip-edges graph flipped))

(defrecord RadialLayout
    []
  Layout

  (layout-graph
   [this graph options]
   ;; Layouts the graph radially.
   ;;
    ;;  Options are: width, height, radius, flow (:in or :out) and
   ;;  sort-children, a function to sort the children. The default function
   ;;  tries to minimize the crossing
   (let [options (merge {:width (or (:width graph) 1900)
                         :height (or (:height graph) 1200)
                         :radius 180
                         :sort-children default-sort-children
                         :root nil
                         :flow :in}
                        options)
         context (greedy-break-cycles graph (:flow options))
         graph (:graph context)
         [tree layers-data] (if (nil? (:root options))
                              (build-tree graph (:flow options))
                              (build-tree graph (:flow options) (:root options)))
         layers-data (label-sizes tree layers-data (:flow options))
         layers-data (label-angles tree layers-data (:flow options))
         graph (place-nodes graph tree layers-data options)
         graph (if (:has-cycle context)
                 (restore-cycles graph (:flipped-edges context))
                 graph)
         graph (make-graph-visible graph)
         graph (adjust-size graph)]
     graph)))

(defn radiallayout
  []
  (RadialLayout.))
