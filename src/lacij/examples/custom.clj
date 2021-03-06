(ns lacij.examples.custom 
  (:use lacij.edit.graph
        lacij.view.graphview
        lacij.view.core
        analemma.xml
        (lacij.view rectnodeview circlenodeview))
  (:require [analemma.svg :as svg]))

(defn create-node-view []
  (let [xmlcontent (parse-xml (slurp "src/lacij/examples/blue-rect.svg"))
        rect (import-rect xmlcontent :blueRectangle0 :blueRectangle0 0 0)]
    (fn [shape x y style attrs]
      (condp = shape
          :rect (assoc rect :x x :y y :id (genid))
          nil))))

(defrecord CrossDecorator
    []
  Decorator

  (decorate
    [this view context]
    (let [width (:width view)
          height (:height view)]
      (prn "vi" view)
      (prn "w" width)
      (prn "h" height)
      (-> (svg/path [:M [(double (/ width 2)) 0]
                     :L [(double (/ width 2)) height]
                     :M [0 (double (/ height 2))]
                     :L [width (double (/ height 2))]
                     :Z []])
          (svg/style :stroke-width 3 :stroke "black")))))

(defn -main []
  (let [xmlcircle (parse-xml (slurp "src/lacij/examples/green-circle.svg"))]
   (-> (graph)
       (set-node-view-factory (create-node-view))
       (add-node :athena "Athena" :x 10 :y 30)
       (add-node :zeus "Zeus" :x 200 :y 150)
       (add-node :hera "Hera" :x 500 :y 150)
       (add-node :ares "Ares" :x 350 :y 250)
       (add-node :cross-rect "" :x 450 :y 350 :shape :rect)
       (add-decorator :cross-rect (CrossDecorator.))
       (add-node :cross-circle "" :x 550 :y 450 :shape :circle)
       (add-decorator :cross-circle (CrossDecorator.))
       (add-node :matrimony "♥" :x 0 :y 0 :shape :circle)
       (set-node-view :matrimony (import-circle xmlcircle :greenCircle0 :greenCircle0 400 170))
       (add-edge :father1 :athena :zeus)
       (add-edge :zeus-matrimony :zeus :matrimony)
       (add-edge :hera-matrimony :hera :matrimony)
       (add-edge :son-zeus-hera :ares :matrimony)
       (build)
       (export "/tmp/custom.svg" :indent "yes"))))

