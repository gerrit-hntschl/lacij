;;; Copyright © 2013 Fraunhofer Gesellschaft
;;; Licensed under the EPL V.1.0

(ns lacij.edit.graph
  (:use [lacij.view.nodeview :only [create-nodeview]]
        [lacij.view.edgeview :only [create-edgeview]]
        [lacij.view.edgelabelview :only [create-edgelabelview]]
        [lacij.view.core :only [add-view-label]]
        [lacij.view.nodelabelview :only [create-nodelabelview]])
  (:require [lacij.model.node :as n]
            [lacij.model.edge :as e]))

(defn create-node-view
  [id shape x y node-styles style node-attrs attrs node-view-factory]
  (if (nil? node-view-factory)
    (create-nodeview id shape x y (merge node-styles style) (merge node-attrs attrs))
    (if-let [node-view (node-view-factory shape x y style attrs)]
      node-view
      (create-nodeview id shape x y
                       (merge node-styles style)
                       (merge node-attrs attrs)))))

(defn create-node
  [id params node-styles node-attrs node-view-factory]
  (let [{:keys [label x y style shape] :or {shape :rect style {}
                                            x 0 y 0}}
        (merge node-attrs params)
        attrs (dissoc params :label :x :y :style :shape)
        node-view (create-node-view id shape x y
                                    node-styles
                                    style
                                    node-attrs
                                    attrs
                                    node-view-factory)
         node-view (add-view-label node-view (create-nodelabelview label :center))]
    [(n/create-node id node-view) node-view]))

(defn- update-node-edges
  [graph edgeid srcid dstid]
  {:pre [(not (nil? ((:nodes graph) srcid)))
         (not (nil? ((:node graph) dstid)))]}
  (let [srcnode ((:node graph) srcid)
        srcnode (update-in srcnode [:outedges] conj edgeid)
        dstnode ((:nodes graph) dstid)
        dstnode (update-in dstnode [:inedges] conj edgeid)]
    (update-in graph [:nodes] assoc
               srcid srcnode
               dstid dstnode)))

(defn add-edge-kv
 [graph id id-node-src id-node-dst params]
 (let [{:keys [label style]} params
       rest-params (dissoc params :label :style)
       edgeattrs (merge (:edge-attrs graph) rest-params)
       edgeview (create-edgeview id (merge (:edge-styles graph) style) edgeattrs)
       edgeview (if (nil? label)
                  edgeview
                  (add-view-label edgeview (create-edgelabelview label :center)))
       edge (e/create-edge id edgeview id-node-src id-node-dst)
       graph (update-node-edges graph id id-node-src id-node-dst)]
   (update-in graph [:edges] assoc id edge)))

(defn add-segmented-edge-kv
 [graph id id-node-src id-node-dst params points]
 (let [{:keys [label style]} params
       rest-params (dissoc params :label :style)
       edgeattrs (merge (:edge-attrs graph) rest-params)
       edgeview (svgsegmentededgeview (merge edge-styles style) edgeattrs points)
        edgeview (if (nil? label)
                  edgeview
                  (add-edge-label edgeview (edgelabelview label :center)))
       edge (svgedge id edgeview id-node-src id-node-dst)
       graph (update-node-edges graph id id-node-src id-node-dst)]
   (update-in graph [:edges] assoc id edge)))


(defmacro do-batik-update
  "Executes body inside a batik thread and insides an update"
  [graph & body]
  `(let [graph# ~graph
         svgcanvas# (canvas graph#)]
     (do-batik
      svgcanvas#
      (do-update
        graph#
        ~@body))))

;; helpers

;; (defn x-add-node
;;   [f this id & params]
;;   (cond
;;    (empty? params)
;;    (f this id {})

;;    (keyword? (first params)) 
;;    (f this id (merge {:label (name id) :x 0 :y 0}
;;                      (apply hash-map params)))

;;    :else
;;    (let [[label & res] params]
;;      (f this id (merge {:label label :x 0 :y 0}
;;                        (apply hash-map res))))))

;; (defn x-add-edge
;;   [f this id src-id dst-id & params]
;;   (cond
;;    (empty? params)
;;    (f this id src-id dst-id {})

;;    (keyword? (first params))
;;    (f this id src-id dst-id (apply hash-map params))

;;    :else
;;    (let [[label & res] params]
;;      (f this id src-id dst-id (merge {:label label}
;;                                      (apply hash-map res))))))

;; (defn add-edge
;;   [this id src-id dst-id & params]
;;   (apply x-add-edge add-edge-kv this id src-id dst-id params))

;; (defn add-edge!
;;   [this id src-id dst-id & params]
;;   (x-add-edge add-edge-kv! this id src-id dst-id params))

;; (defn add-label
;;   [this id label & params]
;;   (add-label-kv this id label (apply hash-map params)))

;; (defn add-default-node-style
;;   [this & params]
;;   (add-default-node-style-kv this (apply hash-map params)))

(defn add-default-edge-style
  [this & params]
  (add-default-edge-style-kv this (apply hash-map params)))

(defn add-default-node-attrs
  [this & params]
  (add-default-node-attrs-kv this (apply hash-map params)))

(defn add-default-edge-attrs
  [this & params]
  (add-default-edge-attrs-kv this (apply hash-map params)))

(defn add-listener
  [this id type f & args]
  (add-listener-vec this id type f args))

(defn add-node-styles!
  [this id & params]
  (add-node-styles-kv! this id (apply hash-map params)))


(defmacro do-update [graph & body]
  "Executes body inside an update"
  `(let [graph# ~graph]
     (try
       (begin-update graph#)
       ~@body
       (finally
        (end-update graph#)))))

(defn add-node-kv
 [graph id params]
 (let [[node _] (create-node id
                             params
                             (:node-styles graph)
                             (:node-attrs graph)
                             (:node-view-factory graph))]
   (update-in graph [:nodes] assoc id node)))


(defn remove-edge
 [graph n1 n2]
 (let [eid (first (filter #(= (dst (edge graph %)) n2) (:outedges (node graph n1))))]
   (remove-edge-by-id graph eid)))

(defn add-label-kv
 [graph id label params]
 (cond (contains? edges id)
       (let [edge (get edges id)
             edgeview (edge-view edge)
             edgeview (add-edge-label edgeview
                                      (edgelabelview label :center (:style params)
                                                     (dissoc params :style)))
             edge (svgedge id edgeview (src edge) (dst edge))]
         (update-in graph [:edges] assoc id edge))

       (contains? nodes id)
       (let [node (get nodes id)
             nodeview (node-view node)
             nodeview (add-node-label nodeview
                                      (nodelabelview label :center
                                                     (:style params)
                                                     (dissoc params :style)))
             node (svgnode id nodeview)]
         (update-in graph [:nodes] assoc id node))))

(defn add-default-node-style-kv
 [graph node-styles]
 (update-in graph [:node-styles] merge node-styles))

(defn add-default-node-attrs-kv
 [graph node-attrs]
 (update-in graph [:node-attrs] merge node-attrs))

(defn add-default-edge-style-kv
 [graph edge-styles]
 (update-in graph [:edge-styles] merge edge-styles))

(defn add-default-edge-attrs-kv
 [graph edge-attrs]
 (update-in graph [:edge-attrs] merge edge-attrs))

(defn add-def
 [graph def]
 (update-in graph [:defs] conj def))

(defn add-decorator
 [graph id decorator]
 (let [view (node-view (node graph id))
       view (add-node-decorator view decorator)
       node (svgnode id view)]
   (update-in graph [:nodes] assoc id node)))

(defn build
 [graph]
 (view-graph graphview graph {:defs defs :doc xmldoc :width width :height height
                             :viewBox viewBox})
 (set-document svgcanvas xmldoc)
 ;; set initial state:
 (swap! history add-state (graphstate graph))
 graph)

(defn export-kv
 [graph filename options]
 (export-graph graphview graph filename {:defs defs} options))

(defn set-node-view-factory
 [graph f]
 (assoc graph :node-view-factory f))

(defn set-node-view
 [graph id view]
 (let [node (get nodes id)
       current-view (node-view node)
       labels (node-labels current-view)
       view (reduce (fn [view label]
                      (add-node-label view label))
                    view labels)
       node (change-node-view node view)]
   (update-in graph [:nodes] assoc id node))
 )

(defn move-node
 [graph id x y]
 (let [node (get nodes id)
       current-view (node-view node)
       new-view (assoc current-view :x x :y y)
       node (change-node-view node new-view)]
   (update-in graph [:nodes] assoc id node)))

(defn move-node-center
 [graph id x y]
 (let [view (node-view (node graph id))
       xbox (node-x view)
       ybox (node-y view)
       width (node-width view)
       height (node-height view)
       xdelta (- (double (/ width 2)))
       ydelta (- (double (/ height 2)))
       destx (+ x xdelta)
       desty (+ y ydelta)]
   (move-node graph id destx desty)))

(defn add-listener-vec
 [graph id type f args]
 (if-let [el (dom/element-id xmldoc id)]
   (do
     (apply dom/add-event-listener el type f args)
     (update-listeners graph id type f args)
     graph)
   graph))

(defn add-node-kv!
 [graph id params]
 (let [[node node-view] (create-node id params node-styles node-attrs node-view-factory)
       node-element (view-node node-view node {:doc xmldoc})
       docel (dom/document-element xmldoc)
       edit (node-inserted-edit docel nil node-element)
       graph (update-in graph [:nodes] assoc id node)
       edit2 (add-state-edit history (graphstate graph))
       compedit (compound-edit edit edit2)]
   (post-edit undosupport compedit)
   graph))

(defn add-node-styles-kv!
 [graph id styles]
 (if-let [node (get nodes id)]
   (let [nodeview (node-view node)
         nodeview (add-node-styles-kv nodeview styles)
         node (svgnode id nodeview)
         node-element (view-node nodeview node {:doc xmldoc})
         [graph edit] (replace-node graph id node node-element)]
     (post-edit undosupport edit)
     graph)
   graph))

(defn add-edge-kv!
 [graph id id-node-src id-node-dst params]
 (let [[edge edgeview] (create-edge id params id-node-src id-node-dst edge-styles edge-attrs)
       edge-element (view-edge edgeview graph edge {:doc xmldoc})
       docel (dom/document-element xmldoc)
       edit (node-inserted-edit docel nil edge-element)
       graph (update-in graph [:edges] assoc id edge)
       graph (update-node-edges graph id id-node-src id-node-dst)
       edit2 (add-state-edit history (graphstate graph))
       compedit (compound-edit edit edit2)]
   (post-edit undosupport compedit)
   graph))

(defn can-undo?
 [graph]
 (.canUndo undomanager))

(defn undo!
 [graph]
 (if (can-undo? graph)
   (do
     (.undo undomanager)
     (let [graph (restore-state graph (current-state (deref history)))
           graph (refresh-nodes-selections graph
                                           (:nodes-selections graph)
                                           (:nodes-selections graph))]
       graph))
   graph))

(defn can-redo?
 [graph]
 (.canRedo undomanager))

(defn redo!
 [graph]
 (if (can-redo? graph)
   (do
     (.redo undomanager)
     (let [graph (restore-state graph (current-state (deref history)))
           graph (refresh-nodes-selections graph
                                           (get graph :nodes-selections)
                                           (:nodes-selections graph))]
       (swap! (:history graph) update-current-state (graphstate graph))
       graph))
   graph))

(defn begin-update
 [graph]
 (.beginUpdate undosupport))

(defn end-update
 [graph]
 (.endUpdate undosupport))

(defn set-node-selected!
 [graph id selected]
 (let [current-selected (conj (get graph :nodes-selections) id)
       graph (refresh-nodes-selections graph
                                       (get graph :nodes-selections)
                                       current-selected)]
   (swap! history update-current-state (graphstate graph))
   graph))


(def ^{:doc "Generates a unique id for an edge."} geneid (partial (comp keyword gensym) "e"))

(def ^{:doc "Generates a unique id for a node"} genid (partial (comp keyword gensym) "h"))
