;;; Copyright © 2010-2012 Fraunhofer Gesellschaft
;;; Licensed under the EPL V.1.0

(ns ^{:doc "Default 'factory' to create different types of nodes views
  with default styles."}
  lacij.view.nodeview
  (:use lacij.utils.core
        lacij.view.utils.style
        (lacij.view rectnodeview circlenodeview))
  (:import lacij.view.rectnodeview.RectNodeView
           lacij.view.circlenodeview.CircleNodeView))

(defn create-nodeview
  [id shape x y style attrs]
  (let [default-style {:fill "white" :stroke "black"}
        {:keys [x y width height r] :or {x x y y width 100 height 40 r 20}} attrs]
    (condp = shape
        :rect (RectNodeView. id x y width height [] default-style style attrs #{})
        :circle (CircleNodeView. id x y r [] default-style style attrs #{})
      
        (RectNodeView. id x y width height [] default-style style attrs #{}))))

