[![Clojars Project](https://img.shields.io/clojars/v/cark/cark.behavior-tree.svg)](https://clojars.org/cark/cark.behavior-tree) [![cljdoc badge](https://cljdoc.org/badge/cark/cark.behavior-tree)](https://cljdoc.org/d/cark/cark.behavior-tree/CURRENT)
# cark.behavior-tree
A behavior tree implementation for clojure and clojurescript. Also includes a state machine built on top of its primitives.
## Features
- Persistent, fully functional.
- Separation between the changing parts (the database) and the static parts (the tree), for keeping/serializing between tick invokations.
- Hiccup-like format for tree definition.
- Compatible with both clojure and clojurescript
- A state machine implementation.
- Quite fast
## What is it good for ?
- This is well suited for modeling and keeping track of concurrent, asynchronous processes and workflows.
- Makes these processes and workflows easily testable.
- Sequential notation, asynchronous execution.
- There is a bit of an overlap with state machines, but this has more powerful primitives.
- This implementation is not for real time game AI programming. The goal was to be functional and easy to understand. There is still much allocation going on.
- This is not for fine grained, computationally intensive tasks, you won't be writing the next regex parser with it.
- On the other hand, it's still fast enough to control a few animations on a web page.
## What is a behavior tree ?
This implementation started with the idea that the game AI behavior trees could find a place in regular application developement. 

There are many flavors of behavior trees. This one takes inspiration on the libgdx behavior trees. Although we had to adapt our implementation to our functional purity 
requirements, it might be a good idea to read their excellent [libgdx behavior trees documentation](https://github.com/libgdx/gdx-ai/wiki/Behavior-Trees).

This implementation is processing nodes in a depth first manner, leaving these in one of 4 states:
- :fresh : The node is ready to be used for the "first time"
- :running : The node is currently running, probably waiting some sort of condition before reaching its final state
- :success and :failure : The node has reached its final state, this result is cached and the node will not be rerun until being "refreshed"

Each time the core/tick function is called, the tree is traversed, skipping :success and :failure nodes. The :fresh and :running nodes are "ticked", possibly ticking their children. They adjust their status according to their parameters and children status. 

This allows the user to describe the modeled processes sequentially although these might be asynchronous.

This implementation communicates with the outside world via incoming and outgoing events and, most importantly, the blackboard.
### The blackboard
The behavior tree's database has a blackboard key which contains the application specific data. The tree can
act or react on/to this data. The user can also update its value between tick invokations.

The [core namespace](https://cljdoc.org/d/cark/cark.behavior-tree/CURRENT/api/cark.behavior-tree.core) defines a whole menagerie of functions to query and manipulate the blackboard. These functions have their names starting with `bb-`.
### Nodes
This is a little overview, the nodes are described in more details in the [node definitions documentation](https://cljdoc.org/d/cark/cark.behavior-tree/CURRENT/api/cark.behavior-tree.node-defs).
#### Leaf nodes
These node have no children.
- :update : updates the tree context, using the func parameter.
- :predicate : succeeds or fail according to the result of it's func function parameter.
- :send-event : Sends an event to the library user, with an optional argument.
- :timer : Succeeds when the time provided by its duration parameter has elapsed, keeps in the running state otherwise.
- :timer-init : Usefull in setting the start time of named timers.
- :success-leaf : always succeed
- :failure-leaf : always fails
#### Decorator nodes
These nodes have a single child
- :inverter : succeeds when its child fails, or fails when its child succeeds.
- :repeat : repeats forever or until the count value is reached, refreshing its child on each iteration.
- :until-failure : repeats until the child fails, and then succeeds. Refreshes its child on each iteration.
- :until-success : repeats until the child succeeds, and then succeeds. Refreshes its child on each iteration.
- :always-failure : as soon as the child fails or succeeds, this node fails.
- :always-success : as soon as the child fails or succeeds, this node succeeds.
- :bind : binds one or several values taken from the blackboard (and/or modified by some function) to vars that will be available to its children
- :map : this node will loop over a sequence, binding each item to a var before executing its child
- :on-event : tests for the presence of an event, possibly waiting for it, before executing its child, binding the event arg to a var
- :trace : Activates tracing for the subtree it is the root of. Use for debugging.
#### Branch nodes
These nodes have several children
- :sequence : This node ticks its children one by one, succeeding when they all succeed, or failing if any fails.
- :seletor : This node ticks its children one by one, succeeding if any of these succeeds, or failing if they all fail.
- :parallel : This node tick all its children "concurrently", succeeding or failing according to its policy parameter.
- :guard : checks that its predicate node succeeds before executing its payload node, the check happens even when the node is running.
- :guard-selector : accepts only guard childrens, executes the first child whose predicate succeeds, possibly interrupting an already running child.
- :on-cancel : executes its on-cancel node when it passes from the :running to the :fresh status. Use for resource disposal.
### Extending
Extending the tree with new nodes is expected and probably necessary depending on the use cases. Ping me on slack (Carkh) if the need arises and you can't figure it out.
### Hiccup
The tree is described with a hiccup-like notation, then compiled to a more efficient data structure. Many of the parameters defined for different node types can take either a value or a function, that will then be executed receiving the tree context as its parameter. Additional keys may be passed to a node's parameter map with no adverse effect.
## A Quick example
```clojure
  ;; we define a traffic light that goes through all colors
  (defn traffic-light-1 []
    (-> [:repeat
         [:sequence
          [:update {:func (bt/bb-setter :green)}]
          [:timer {:timer :traffic-light :duration 50000}]
          [:update {:func (bt/bb-setter :yellow)}]
          [:timer {:timer :traffic-light :duration 10000}]
          [:update {:func (bt/bb-setter :red)}]
          [:timer {:timer :traffic-light :duration 60000}]]]
        bt/hiccup->context (bt/tick 0)))

  ;; Real use would not use the time parameter when calling the tick function.
  (defn do-traffic-light-tests [traffic-light]
    (is (= :green (-> traffic-light bt/bb-get)))
    (is (= :green (-> traffic-light (bt/tick+ 49999) bt/bb-get)))
    (is (= :yellow (-> traffic-light (bt/tick+ 50000) bt/bb-get)))
    (is (= :red (-> traffic-light (bt/tick+ 50000) (bt/tick+ 10000) bt/bb-get)))
    (is (= :green (-> traffic-light (bt/tick+ 50000) (bt/tick+ 10000) (bt/tick+ 60000) bt/bb-get)))
    (is (= :yellow (-> traffic-light (bt/tick+ 50000) (bt/tick+ 10000) (bt/tick+ 60000) (bt/tick+ 50000) bt/bb-get)))
    ;;do the same in a single tick (catching up an exceptionally long GC pause !)
    (is (= :red (-> traffic-light (bt/tick+ 60000) bt/bb-get)))
    (is (= :green (-> traffic-light (bt/tick+ 120000) bt/bb-get)))
    (is (= :yellow (-> traffic-light (bt/tick+ 170000) bt/bb-get))))
```
## State machines
In the [cark.behavior-tree.state-machine namespace](https://cljdoc.org/d/cark/cark.behavior-tree/CURRENT/api/cark.behavior-tree.state-machine), we present an implementation of state machines based on the primitives offered by the behavior trees. The functions of this namespace are indeed merely producing a hiccup tree that will then need to be compiled with the [bt/hiccup->context](https://cljdoc.org/d/cark/cark.behavior-tree/CURRENT/api/cark.behavior-tree.core#hiccup-%3Econtext), like any other regular behavior tree.

Compared to a hand coded state machine, our implementation has poor performances (though still quite fast!). On the other hand, it brings the full power of the behavior trees with it. First we gain the benefits of the blackboard. Also such a state machine may be included inside a behavior tree, or use full sub-trees as event handlers. That means that we automatically support hierarchical and parallel state machines, although special care must then be taken with event naming and blackboard paths.

If nothing else, this state machine implementation illustrates how the relatively verbose behavior tree hiccup notation can be encapsulated into higher level, more specialized constructs.
### A quick example
```clojure
;; we model the UI for a backup and restore page
(def ctx
  (-> (sm/make [:sm] :start
        (sm/state :start
          (sm/enter-event [:update {:func (bt/bb-updater-in [:flags] set/union #{:restore-button :backup-button})}])
          (sm/event :restore-pressed (sm/transition :restore))
          (sm/event :backup-pressed (sm/transition :backup))
          (sm/event :ok-pressed 
		    [:update {:func (bt/bb-updater-in [:flags] set/difference #{:success-dialog :error-dialog})}]))
        (sm/state :backup
          (sm/enter-event
           [:sequence
            [:send-event {:event :prepare-backup}]
            [:update {:func (bt/bb-assocer-in [:flags] #{:backuping-dialog})}]])
          (sm/event :got-database-string
            [:sequence
             [:send-event {:event :download-database-string :arg sm/event-arg}]
             [:update {:func (bt/bb-updater-in [:flags] disj :backuping-dialog)}]
             (sm/transition :start)]))
        (sm/state :restore
          (sm/enter-event [:update {:func (bt/bb-assocer-in [:flags] #{:confirm-dialog})}])
          (sm/event :cancel-pressed
            [:sequence
             [:update {:func (bt/bb-assocer-in [:flags] #{})}]
             (sm/transition :start)])
          (sm/event :got-file-data
            [:sequence
             [:send-event {:event :restore-file :arg sm/event-arg}]
             [:update {:func (bt/bb-assocer-in [:flags] #{:restoring-dialog})}]])
          (sm/event :restore-result
            [:sequence
             [:update {:func #(cond-> (bt/bb-update % assoc :flags #{:restore-button :backup-button})
                                (= :success (sm/event-arg %)) (bt/bb-update-in [:flags] conj :success-dialog)
                                (= :error (sm/event-arg %)) (bt/bb-update-in [:flags] conj :error-dialog))}]
             (sm/transition :start)])))
      bt/hiccup->context bt/tick))
```

## Tests
There are quite a few tests that should help in understanding how the tree works. 

They all can be run, for both clojurescript and clojure, with these commands :
```
  npm install
  clojure -A:all-tests
```

It is also possible to run the clojurescript tests with shadow-cljs in your prefered browser with this command :

```
  npx shadow-cljs watch test
```

You then can navigate to http://localhost:8022/ and see the test results.
## Examples
In the src/test directory, you'll find a couple real world'ish examples.

Of particular interest is the [change control example](https://github.com/cark/cark.behavior-tree/blob/master/src/test/cark/behavior_tree/change_control_example_test.cljc) were we present the process of coming up with a working, modular behavior tree, testing each part along the way. That example exercises many of the primitives provided by this library.
## License
Copyright (c) Sacha De Vos and contributors. All rights reserved.

The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file LICENSE.html at the root of this distribution. By using this software in any fashion, you are agreeing to be bound by the terms of this license. You must not remove this notice, or any other, from this software.
