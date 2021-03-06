(require '[riemann.streams :refer :all]
         '[riemann.index :refer [lookup]]
         '[riemann.core :as core]
         '[beavis.deletions :as deletions]
         '[beavis.habilitationsschrift :refer [next-stage-fn]]
         '[clojure.tools.logging :as log])

(defn is-result [& children]
  (fn [event]
    (when (contains? event :responses)
      (call-rescue event children))))

(defn ignore-old-events [index & children]
  (fn [event]
    (let [indexed (lookup index (:host event) (:service event))]
      (if indexed
        (when (> (:time event) (:time indexed))
          (call-rescue event children))
        (call-rescue event children)))))

(defn not-indexed [index & children]
  (fn [event]
    (let [indexed (lookup index (:host event) (:service event))]
      (when (nil? indexed)
        (call-rescue event children)))))

(defn safe-index [& children]
  (fn [event]
    (if (deletions/is-deleted? (:check_id event))
      (log/info "ignoring event:" event)
      (call-rescue event children))))

(defn to-next-function [& children]
  (fn [event]
    (when-not (deletions/is-deleted? (:check_id event))
      (@next-stage-fn event))))

(periodically-expire 20)

(let [index (core/wrap-index (index))]
  ;; This stream must be, in total, synchronous. Any asynchronous operations
  ;; must happen in another stream. This stream only updates the in-memory
  ;; state so that after a call to stream!, we can immediately query the
  ;; index to get the state of the mutated event. -greg
  (streams
    (default :ttl 240
      (by [:host :service]
          (not-indexed index
                       (safe-index index))
          ;; The timestamp for each response and result should be monotonically increasing.
          ;; If we see an older timestamp for any of these, then it is possible out-of-order
          ;; delivery, delayed delivery, or duplicate delivery. In any regard, let's simply
          ;; drop it on the floor. -greg
          (ignore-old-events index
                             ;; three consecutive failures indicates a non-flapping state.
                             (stable 90 :state
                                     (safe-index index)
                                     (changed :state
                                              (is-result
                                                (to-next-function)))))))))
