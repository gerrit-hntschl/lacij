;;; Copyright © 2010-2013 Fraunhofer Gesellschaft
;;; Licensed under the EPL V.1.0

(ns ^{:doc "Utility functions to call layouts algorithms"}
  lacij.layouts.layout
  (:use (lacij.layouts
         core
         randomlayout
         naivelayout
         radiallayout
         hierarchicallayout)))

(defn layout [graph rlayout & options]
  (let [options (apply hash-map options)]
    (case rlayout
          :radial (layout-graph (radiallayout) graph options)
          :random (layout-graph (randomlayout) graph options)
          :naive (layout-graph (naivelayout) graph options)
          :hierarchical (layout-graph (hierarchicallayout) graph options) 
          (layout-graph rlayout graph options))))
