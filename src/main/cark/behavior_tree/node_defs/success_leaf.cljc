(ns cark.behavior-tree.node-defs.success-leaf
  "The :success-leaf always succeeds"
  (:require [cark.behavior-tree.context :as ctx]
            [cark.behavior-tree.db :as db]
            [cark.behavior-tree.tree :as tree]
            [cark.behavior-tree.type :as type]
            [cark.behavior-tree.base-nodes :as bn]))

(defn compile-node [tree id tag params children-ids]
  [(fn success-leaf-tick [context arg]
     (case (db/get-node-status context id)
       :fresh (db/set-node-status context id :success)))
   tree])

(defn register []
  (type/register
   (bn/leaf
    {::type/tag :success-leaf
     ::type/compile-func compile-node})))


