(ns cark.behavior-tree.node-defs.predicate
  (:require [cark.behavior-tree.context :as ctx]
            [cark.behavior-tree.db :as db]
            [cark.behavior-tree.tree :as tree]
            [cark.behavior-tree.type :as type]
            [cark.behavior-tree.base-nodes :as bn]
            [clojure.spec.alpha :as s]))

(s/def ::func fn?)
(s/def ::pred ifn?)
(s/def ::wait? (s/or :boolean boolean?
                     :function fn?))

(defn compile-node [tree id tag params _]
  (let [func (:func params)
        [type value] (:wait? params)
        wait? (case type
                :boolean (constantly value)
                :function value
                nil (constantly false))
        pred (or (:pred params) identity)]
    [(fn predicate-tick [ctx arg]
       (if (pred (func ctx))
         (ctx/set-node-status ctx id :success)
         (if (wait? ctx)
           (ctx/set-node-status ctx id :running)
           (ctx/set-node-status ctx id :failure))))
     tree]))

(defn register []
  (type/register
   (bn/leaf
    {::type/tag :predicate
     ::type/params-spec (s/keys :req-un [::func] :opt-un [::wait? ::pred])
     ::type/compile-func compile-node})))
