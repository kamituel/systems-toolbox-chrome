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
    (let [{:keys [init-ts max-age-ms ignored-cmds-types]} current-state
          msg (u/raw-msg->map init-ts msg-map)]
      (when-not (contains? ignored-cmds-types (:command msg))
        {:new-state (update current-state :messages (partial conj-and-drop-too-old max-age-ms) msg)}))))

(defn handle-state-snapshot
  [{:keys [current-state] :as msg-map}]
  #_(js/console.log "Handling state snapshot " (:active? current-state) "n s diffs " (count (:state-snapshots-diffs current-state)))
  (when (:active? current-state)
    (let [{:keys [init-ts max-age-ms ignored-snapshots-cmp-ids]} current-state
          {:keys [cmp-id] :as snapshot} (u/raw-state-snapshot->map init-ts msg-map)
          prev-snapshot (get-in current-state [:state-snapshots-latest cmp-id])
          snapshot-diff (when prev-snapshot (u/diff-state-snapshots prev-snapshot snapshot))]
      (when-not (contains? ignored-snapshots-cmp-ids cmp-id)
        {:new-state
        (-> current-state
          (assoc-in [:state-snapshots-latest cmp-id] snapshot)
          (cond-> snapshot-diff (update :state-snapshots-diffs (partial conj-and-drop-too-old max-age-ms) snapshot-diff)))}))))

(defn activate
  [max-age-ms]
  (js/console.log "Probe is now active, with a max age of <<" max-age-ms ">> ms.")
  (swap! state assoc
    :active? true
    :max-age-ms max-age-ms))

(defn ^:export dump-logs-to-a-file
  []
  (js/console.log "Dupming logs to the file.")
  (let [{:keys [state-snapshots-latest state-snapshots-diffs messages]} @state]
    (u/save-logs state-snapshots-latest state-snapshots-diffs messages)))

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
  []
  {:state state})

(defn cmp-map
  [cmp-id]
  {:cmp-id       cmp-id
   :state-fn     mk-state
   :handler-map {:firehose/cmp-put           handle-message
                 :firehose/cmp-recv          handle-message
                 :firehose/cmp-publish-state handle-state-snapshot}
   :opts         {:reload-cmp false ;; TODO: verify it helps with figwheel reloading.
                  :validate-in false
                  :validate-out false
                  :validate-state false}})

(defn init
  [switchboard]
  (sb/send-mult-cmd
    switchboard
    [[:cmd/init-comp #{(cmp-map :s-tlbx-probe/probe)}]
     [:cmd/attach-to-firehose :s-tlbx-probe/probe]]))
