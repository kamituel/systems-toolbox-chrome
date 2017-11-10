(ns kamituel.s-tlbx-probe.probe
  "Systems toolbox component that attaches to systems' firehose to capture state snapshots
  and messages and exposes those to the Chrome DevTools extension."
  (:require [matthiasn.systems-toolbox.component :as comp]
            [matthiasn.systems-toolbox.switchboard :as sb]
            [cognitect.transit :as transit]
            [kamituel.s-tlbx-probe.utils :as u]))

(defonce state
  (atom {;; Are messages and state snapshots being recorded.
         :active? false
         ;; Captured messages.
         :messages '()
         ;; Last captured state snapshots (one per component - keys are component ID's).
         :state-snapshots-latest {}
         ;; Diffs of state snapshots.
         :state-snapshots-diffs '()
         ;; If true, any non-keyword, non-primitive values will be obscured as soon
         ;; as it's received. Doesn't apply to :state-snapshots-latest.
         :sanitize-immediately? true
         ;; Messages or state snapshot diffs older than that will be removed.
         ;; Latest state snapshots are always kept.
         :max-age-ms 0
         ;; Command types that will be ignored.
         :ignored-cmds-types #{}
         ;; Component ID's whose state snapshots will be ignored.
         :ignored-snapshots-cmp-ids #{:s-tlbx-probe/probe :frntnd/switchbrd}
         ;; Time when probe has been initialized. Will be used to compute :ts-rel -
         ;; - relative timestamp of each message and snapshot diff.
         :init-ts (.getTime (js/Date.))}))

(def transit-writer
  "Used when transferring data over to the Chrome DevTools extension."
  (transit/writer :json))

(defn drop-too-old
  "Removes too old things (messages or snapshot diffs) from a collection. A 'thing' is too old
  if it has been added more than max-age-ms ms ago."
  [max-age-ms xs]
  {:pre [(pos? max-age-ms) (seq? xs)]}
  (let [now-ms (.getTime (js/Date.))
        too-old? (fn too-old? [x]
                   (> (- now-ms (:ts x)) max-age-ms))]
    (filter (complement too-old?) xs)))

(defn conj-and-drop-too-old
  [max-age-ms xs x]
  {:pre [(pos? max-age-ms) (seq? xs) (map? x)]}
  (drop-too-old max-age-ms (conj xs x)))

(defn handle-message
  [{:keys [current-state] :as msg-map}]
  #_(js/console.log "Handling message " (:active? current-state) " n msgs " (count (:messages current-state)))
  (when (:active? current-state)
    (let [{:keys [init-ts max-age-ms ignored-cmds-types sanitize-immediately?]} current-state
          msg-insecure (u/raw-msg->map init-ts msg-map)
          msg (if sanitize-immediately? (u/sanitize-message msg-insecure) msg-insecure)]
      (when-not (contains? ignored-cmds-types (:command msg))
        {:new-state (update current-state :messages (partial conj-and-drop-too-old max-age-ms) msg)}))))

(defn handle-state-snapshot
  [{:keys [current-state] :as msg-map}]
  #_(js/console.log "Handling state snapshot " (:active? current-state) "n s diffs " (count (:state-snapshots-diffs current-state)))
  (when (:active? current-state)
    (let [{:keys [init-ts max-age-ms ignored-snapshots-cmp-ids sanitize-immediately?]} current-state
          {:keys [cmp-id] :as snapshot} (u/raw-state-snapshot->map init-ts msg-map)
          prev-snapshot (get-in current-state [:state-snapshots-latest cmp-id])
          snapshot-diff-insecure (when prev-snapshot (u/diff-state-snapshots prev-snapshot snapshot))
          snapshot-diff (if sanitize-immediately? (u/sanitize-snapshot-diff snapshot-diff-insecure) snapshot-diff-insecure)]
      (when-not (contains? ignored-snapshots-cmp-ids cmp-id)
        {:new-state
        (-> current-state
          (assoc-in [:state-snapshots-latest cmp-id] snapshot)
          (cond-> snapshot-diff (update :state-snapshots-diffs (partial conj-and-drop-too-old max-age-ms) snapshot-diff)))}))))

(defn ^:export dump-logs-to-a-file
  [_msg]
  (js/console.log "Dupming logs to the file.")
  (let [{:keys [state-snapshots-latest state-snapshots-diffs messages sanitize-immediately?]} @state]
    (u/save-logs
      ;; State snapshots aren't sanitized when are received, to faciliate diffing with the previous
      ;; snapshot. It is not insecure, as the very same snapshot is overwritten as soon as the next
      ;; one arrives, and the very same snapshot is stored in the origianal component's state anyway.
      (into {} (map (fn [[k v]] [k (u/sanitize-state-snapshot v)]) state-snapshots-latest))
      ;; Diffs and messages might've already been sanitized.
      (if sanitize-immediately? state-snapshots-diffs (map u/sanitize-snapshot-diff state-snapshots-diffs))
      (if sanitize-immediately? messages (map u/sanitize-message messages)))))

(defn ^:export read-logs
  "Used by a Chrome DevTools extension."
  []
  (let [{:keys [state-snapshots-latest messages]} @state
        recordings {:messages (->> messages
                                   u/correlate-sender-with-receivers
                                   (sort-by :ts)
                                   u/fix-message-order)
                    :state-snapshots state-snapshots-latest}
        try-write #(try
                     (transit/write transit-writer %)
                     (catch :default e
                       (.error js/console "Failed to serialize" e (.-data e))))]
    (try-write recordings)))

(defn ^:export ignore-cmd-type
  [& cmd-type-kw-strs]
  (let [cmd-types (map keyword cmd-type-kw-strs)]
    (js/console.log "Ignoring: " cmd-types)
    (swap! state update :ignored-cmds-types #(apply conj % cmd-types))))

(defn ^:export stop-ignoring-cmd-type
  [& cmd-type-kw-strs]
  (let [cmd-types (map keyword cmd-type-kw-strs)]
    (js/console.log "Not ignoring: " cmd-types)
    (swap! state update :ignored-cmds-types #(apply disj % cmd-types))))

(defn mk-state
  [sanitize-logs logs-retention-ms]
  (swap! state assoc
    :max-age-ms logs-retention-ms
    :sanitize-immediately? sanitize-logs
    :active? true)
  (js/console.log "Probe is now active, with a max age of <<" logs-retention-ms ">> ms.")
  {:state state})

(defn cmp-map
  [cmp-id sanitize-logs logs-retention-ms]
  {:cmp-id       cmp-id
   :state-fn     #(mk-state sanitize-logs logs-retention-ms)
   :handler-map {:firehose/cmp-put           handle-message
                 :firehose/cmp-recv          handle-message
                 :firehose/cmp-publish-state handle-state-snapshot
                 :s-tlbx-probe/dump-logs-to-a-file dump-logs-to-a-file}
   :opts         {:reload-cmp false ;; TODO: verify it helps with figwheel reloading.
                  :validate-in false
                  :validate-out false
                  :validate-state false}})

(defn init
  [switchboard & {:keys [sanitize-immediately? logs-retention-ms]
                  :or {sanitize-immediately? true
                       :logs-retention-ms -1}}]
  (sb/send-mult-cmd
    switchboard
    [[:cmd/init-comp #{(cmp-map :s-tlbx-probe/probe sanitize-immediately? logs-retention-ms)}]
     [:cmd/attach-to-firehose :s-tlbx-probe/probe]]))
