(ns kamituel.s-tlbx-probe.probe
  "Systems toolbox component that attaches to systems' firehose to capture state snapshots
  and messages and exposes those to the Chrome DevTools extension."
  (:require [matthiasn.systems-toolbox.component :as comp]
            [matthiasn.systems-toolbox.switchboard :as sb]
            [cognitect.transit :as transit]
            [clojure.data :refer [diff]]
            [clojure.walk :refer [postwalk]]
            [cljs.pprint :as pprint]
            [kamituel.s-tlbx-probe.utils :as u]))

(defonce state (atom {;; Recording for the external tool (i.e. Chrome DevTools extension) to pick up.
                      :recording? false
                      ;; Logging to the console.
                      :logging? false
                      ;; Captured messages.
                      :messages '()
                      ;; Captures state snapshots.
                      :state-snapshots '()
                      ;; Buffer for messages and state-snapshots read in recording mode.
                      :messages-to-read nil
                      :state-snapshots-to-read nil
                      ;; Command types that will be ignored.
                      :ignored-cmds-types #{}
                      ;; Component ID's whose state snapshots will be ignored.
                      :ignored-snapshots-cmp-ids #{:s-tlbx-probe/probe}
                      ;; DevTools extension is expected to read recordings from this probe every
                      ;; second. A timeout of 10 seconds is set up after each reading to stop
                      ;; recording in case extension has been closed. This is to prevent
                      ;; accumulating many messages / state snapshots in this atom which could clog
                      ;; the memory and make target application unresponsive.
                      :readout-timeout 10000
                      :analyze-process-interval-id nil
                      :analyze-process-interval-ms 300
                      ;; Messages older than this will be logged or read. Any younger messages
                      ;; will be stored, because soon some messages with the same correlation ID,
                      ;; or the same tag, might arrive, and we want to show them together.
                      :analyze-process-old-threshold-ms 1000
                      :readout-timeout-id nil
                      :print-collapsed-logs? false
                      :probe-init-timestamp (.getTime (js/Date.))}))

(defn ->str
  [x]
  (with-out-str (pprint/pprint x)))

(def transit-writer
  (transit/writer :json))

(defn analyze-messages
  [old-threshold-ms messages]
  (let [now (.getTime (js/Date.))
        msg-old? #(> (- now (:ts %)) old-threshold-ms)
        msg-related? #(or (u/correlated? %1 %2) (u/same-tag? %1 %2))
        old-messages (filter msg-old? messages)
        not-old-messages (filter (complement msg-old?) messages)
        ;; Returns a tuple [related unrelated], where:
        ;;   - related   - messages from not-old-messages that are related to at least one message
        ;;                 from old-messages.
        ;;   - unrelated - all other messages from not-old-messages.
        split-by-relation (fn split-by-relation [old-messages not-old-messages]
                            (loop [not-old-messages not-old-messages
                                   related []
                                   unrelated []]
                              (if (empty? not-old-messages)
                                [related unrelated]
                                (let [[msg & new-not-old-messages] not-old-messages
                                      related? (some (partial msg-related? msg) old-messages)]
                                  (recur
                                    new-not-old-messages
                                    (if related? (conj related msg) related)
                                    (if related? unrelated (conj unrelated msg)))))))
        ;; A tuple:
        ;;   - ready-messages     - messages that are ready to either be printed, or be returned to the
        ;;                          DevTools extension. Ready messages are either sufficiently old,
        ;;                          or are related to the sufficiently old message.
        ;;   - not-other-messages - any messages that are not ready.
        [ready-messages not-other-messages]
        (loop [ready old-messages
               [related unrelated] (split-by-relation old-messages not-old-messages)]
          (if (empty? related)
            [ready unrelated]
            (let [new-ready (concat ready related)]
              (recur new-ready (split-by-relation new-ready unrelated)))))
        ;; Once we have all messages, we want to fold pairs of messages with the same correlation
        ;; ID, as it's the same message really: one has been recorder when being sent, the other
        ;; when received.
        ready-correlated (->> ready-messages
                              (group-by :corr-id)
                              vals
                              (map (fn [[msg-1 msg-2-or-nil]]
                                     (if msg-2-or-nil
                                      (u/merge-correlated msg-1 msg-2-or-nil)
                                      msg-1))))]
    [ready-correlated not-other-messages]))

(defn analyze-state-snapshots
  [state-snapshots]
  (let [by-cmp-id (vals (group-by :cmp-id state-snapshots))
        initial-snapshot? #(= 1 (count %))
        initial-snapshots (->> by-cmp-id
                                (filter initial-snapshot?)
                                (map first))
        diff-snapshots #(loop [diffs []
                                xs (reverse %)]
                          (if (< (count xs) 2)
                            diffs
                            (let [[removed added _] (diff (:snapshot (first xs)) (:snapshot (second xs)))
                                  diff-map {:removed removed
                                            :added added
                                            :cmp-id (:cmp-id (second xs))
                                            :ts-rel (:ts-rel (second xs))}]
                              (recur
                                (if (or removed added) (conj diffs diff-map) diffs)
                                (rest xs)))))
        diffed-snapshots (->> by-cmp-id
                              (filter (complement initial-snapshot?))
                              (map diff-snapshots)
                              flatten)
        latest-snapshots-per-component (map first by-cmp-id)]
    [initial-snapshots diffed-snapshots latest-snapshots-per-component]))
        
(defn analyze-data
  []
  (let [{:keys [messages state-snapshots logging? recording? print-collapsed-logs? messages-to-read
                analyze-process-old-threshold-ms]} @state
        [ready-messages not-ready-messages] (analyze-messages analyze-process-old-threshold-ms messages)
        [_ diffed-snapshots latest-snapshots-per-component] (analyze-state-snapshots state-snapshots)
        things-to-print-now (->> (concat ready-messages diffed-snapshots)
                                 (sort-by :ts)
                                 u/fix-message-order)
        group-fn (if (or print-collapsed-logs? (not logging?))
                    js/console.groupCollapsed
                    js/console.group)]
    (doseq [thing things-to-print-now]
      (if (:src-cmp thing)
        (do ; A message
          (group-fn
            (str "%c" (:ts-rel thing) "s " (:src-cmp thing) " -> " (or (:dst-cmp thing) "(?) ") " - " (:command thing))
            "color: #006060"
            )
          (js/console.log (->str thing))
          (js/console.groupEnd))
        (do ; A state snapshot.
          (group-fn
            (str "%c" (:ts-rel thing) "s " (:cmp-id thing) " state changed.")
            "color: #3C024A")
          (js/console.log "Removed:" (->str (:removed thing)))
          (js/console.log "Added:" (->str (:added thing)))
          (js/console.groupEnd))))
    (swap! state assoc
      :messages not-ready-messages
      :state-snapshots latest-snapshots-per-component
      :messages-to-read (when recording? (sort-by :ts (concat messages-to-read ready-messages)))
      :state-snapshots-to-read (when recording? latest-snapshots-per-component))))

(declare stop-recording)

(defn ^:export ignore-cmd-type
  [& cmd-type-kw-strs]
  (let [cmd-types (map keyword cmd-type-kw-strs)]
    (prn "Ignoring: " cmd-types)
    (swap! state update :ignored-cmds-types #(apply conj % cmd-types))))

(defn ^:export stop-ignoring-cmd-type
  [& cmd-type-kw-strs]
  (let [cmd-types (map keyword cmd-type-kw-strs)]
    (prn "Not ignoring: " cmd-types)
    (swap! state update :ignored-cmds-types #(apply disj % cmd-types))))

(defn start-analyze-process-if-not-running
  [state]
  (let [{:keys [analyze-process-interval-id analyze-process-interval-ms]} @state]
    (when-not analyze-process-interval-id
      (swap! state assoc
        :analyze-process-interval-id
        (js/window.setInterval analyze-data analyze-process-interval-ms)))))

(defn stop-analyze-process-if-not-needed
  [state]
  (let [{:keys [recording? logging? analyze-process-interval-id]} @state]
    (when (and (not recording?) (not logging?))
      (js/window.clearInterval analyze-process-interval-id))))

(defn refresh-timeout
  []
  (let [timeout-id (.setTimeout js/window stop-recording (:readout-timeout @state))
        previous-timeout-id (:readout-timeout-id @state)]
    (when previous-timeout-id (.clearTimeout js/window previous-timeout-id))
    (swap! state assoc :readout-timeout-id timeout-id)))

(defn start-recording
  []
  (refresh-timeout)
  (when (not (:recording? @state))
    (prn "Toolbox DevTools recording started/resumed.")
    (start-analyze-process-if-not-running state)
    (swap! state assoc :recording? true)))

(defn stop-recording
  []
  (prn "Toolbox DevTools recording stopped because of a timeout.")
  (swap! state assoc :recording? false)
  (stop-analyze-process-if-not-needed state))

(defn ^:export start-logging
  [print-collapsed-logs?]
  (start-analyze-process-if-not-running state)
  (swap! state assoc
    :logging? true
    :print-collapsed-logs? print-collapsed-logs?))

(defn ^:export finish-logging
  []
  (let [{:keys [state-snapshots]} @state]
    (swap! state assoc :logging? false)
    (stop-analyze-process-if-not-needed state)
    (js/console.log "Full state snapshots:")
    (js/console.log (->str state-snapshots))))

(defn ^:export read-recordings
  [n]
  (start-recording)
  (let [{:keys [messages-to-read state-snapshots-to-read]} @state
        recordings {:messages messages-to-read
                    :state-snapshots state-snapshots-to-read}
        try-write #(try
                      (transit/write transit-writer %)
                      (catch :default e
                        (.error js/console "Failed to serialize" e (.-data e))))]
    (swap! state assoc :messages-to-read '())
    (swap! state assoc :state-snapshots-to-read '())
    (try-write recordings)))

(defn handle-message
  [{:keys [current-state] :as msg-map}]
  (let [{:keys [probe-init-timestamp ignored-cmds-types]} current-state
        msg (u/raw-msg->map probe-init-timestamp msg-map)]
    (when (and (or (:recording? current-state)
                   (:logging? current-state))
               (not (contains? ignored-cmds-types (:command msg))))
      {:new-state
       (update current-state :messages conj msg)})))

(defn handle-state-snapshot
  [{:keys [current-state] :as msg-map}]
  (let [{:keys [probe-init-timestamp ignored-snapshots-cmp-ids]} current-state
        cmp-id (-> msg-map :msg-payload :cmp-id)]
    (when (and (or (:recording? current-state)
                   (:logging? current-state))
               (not (contains? ignored-snapshots-cmp-ids cmp-id)))
      {:new-state
       (update current-state :state-snapshots conj
         (u/raw-state-snapshot->map probe-init-timestamp msg-map))})))

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
