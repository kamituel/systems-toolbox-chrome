(ns kamituel.s-tlbx-chrome.state
  (:require [kamituel.s-tlbx-chrome.utils :as u]
            [matthiasn.systems-toolbox.component :as comp]
            [alandipert.storage-atom :refer [local-storage]]))

;; A copy used only to persist message filters so they're there when dev tool is re-opened.
(defonce settings
  (local-storage (atom {:message-limit 500
                        :message-filters #{}}) :settings))

(defn initial-state
  []
  {
   ;; List of messages captured. Oldest first. When messages are filtered, they are
   ;; removed/not stored here.
   :messages '()
   ;; Total number of messages captured since extension started working. Does include messages
   ;; filtered and messages that have been removed because message-limit was reached.
   :total-messages-count 0
   ;; Timestamp of the first message captured. Used to display relative time of each subsequent
   ;; message.
   :first-message-ts 0
   ;; Number of messages to show on the list.
   :message-limit (:message-limit @settings)
   ;; User is allowed to adjust :message-limit. These set the min and max value possible.
   :message-limit-min 50
   :message-limit-max 2000
   ;; State snapshots. Keys are component ID's, values are the latest snapshots captured.
   :state-snapshots {}
   ;; Currently selected message.
   :selected-message nil
   ;; User can click a message tag and all messages with that tag will get highlighted.
   :selected-tag nil
   :message-filters (:message-filters @settings)
   :view :messages})

(def persistent-settings
  [:message-filters :message-limit])

(defn persist-settings
  [cmp-state]
  (reset! settings (select-keys @cmp-state persistent-settings)))

(defn show-component
  "Shows eitehr a messages, or snapshots panel."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state assoc :view msg-payload)
  (.setAttribute (.. js/document -body) "view" (name msg-payload)))

(defn correlate-sender-with-receiver
  "Matches message sent over a channel with a received one on the other and (by another component),
  so they can be treated as one."
  [messages]
  (let [sent (filter #(= :firehose/cmp-put (:type %)) messages)
        received (filter #(= :firehose/cmp-recv (:type %)) messages)
        recieved-matches-sent? (fn [sent-msg]
                                 (fn [received-msg]
                                   (u/msg-eq sent-msg received-msg)))]
    (map (fn [sent-message]
           (let [matching-received-msg (first (filter (recieved-matches-sent? sent-message) received))]
             (if matching-received-msg
               (assoc sent-message :dst-cmp (:src-cmp matching-received-msg))
               sent-message)))
         sent)))

(defn fix-message-order
  "Messages that originate in distributed systems (i.e. on the frontend ran in a browser and
  a backend ran on a remote server) could be delivered via the firehose out of order. This
  function restores that order based on an observation that all messages related to one event
  are using the same :tag, and that for messages with the given tag, first message will have
  one element in :cmp-seq, second - two, etc."
  [messages]
  (sort (fn [msg1 msg2]
          (if (not= (:tag msg1) (:tag msg2))
            0
            (cond
              (< (count (:cmp-seq msg1))
                 (count (:cmp-seq msg2)))
              -1
              (> (count (:cmp-seq msg1))
                 (count (:cmp-seq msg2)))
              1
              :else
              0))) messages))

(defn message-matches-all-criteria?
  [pred-fn filter-criteria msg]
  (let [msg-matches-one-criterium? (fn [{:keys [src-cmp dst-cmp command]} msg-map]
                                     (and (if src-cmp (= src-cmp (:src-cmp msg-map)) true)
                                          (if dst-cmp (= dst-cmp (:dst-cmp msg-map)) true)
                                          (if command (= command (:command msg-map)) true)))]
   (pred-fn #(msg-matches-one-criterium? % msg) filter-criteria)))

(defn apply-message-filters
  [cmp-state messages]
  (let [hide-similar (->> @cmp-state :message-filters (filter #(= :hide-similar (:type %))))
        show-similar (->> @cmp-state :message-filters (filter #(= :show-similar (:type %))))]
    (->> messages
         (filter (partial message-matches-all-criteria? every? show-similar))
         (filter (partial message-matches-all-criteria? not-any? hide-similar)))))

(defn handle-new-messages
  "Analyzes new messages and appends them to the :messages list in a state."
  [{:keys [cmp-state msg-payload]}]
  (prn "new msgs raw" (count msg-payload))
  (let [{:keys [view total-messages-count message-limit first-message-ts]} @cmp-state
        assign-message-idx (fn [total-count msgs]
                             (map-indexed #(assoc %2 :idx (+ %1 1 total-count)) msgs))
        messages (->> msg-payload
                      reverse
                      (map (partial u/raw-msg->map))
                      correlate-sender-with-receiver
                      fix-message-order
                      (assign-message-idx total-messages-count))
        messages-filtered (apply-message-filters cmp-state messages)]
   (prn "new messages correlated" (count messages))
   (when (= :probe-error view) (show-component {:cmp-state cmp-state :msg-payload :cmp/messages}))
   (when (zero? first-message-ts) (swap! cmp-state assoc :first-message-ts (-> messages first :ts)))
   (swap! cmp-state update-in [:total-messages-count] (partial + (count messages)))
   (swap! cmp-state update-in [:messages] (fn [msgs]
                                            (->> (concat msgs messages-filtered)
                                                 (take-last message-limit))))))

(defn handle-new-state-snapshots
  "Handle new state messages. Only one state for each component is stored, rest is currently
  discarded."
  [{:keys [cmp-state msg-payload]}]
  (let [last-snapshot-for-each-cmp (->> msg-payload
                                        (group-by #(-> % :msg-payload :cmp-id))
                                        (map (fn [[cmp-id snapshots]]
                                               [cmp-id (first snapshots)]))
                                        (into {}))]
   (swap! cmp-state update-in [:state-snapshots] #(merge % last-snapshot-for-each-cmp))))

(defn show-message-details
  "Makes one message selected, so it can be shown in the sidebar."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state assoc :selected-message msg-payload)
  (swap! cmp-state assoc :selected-tag (:tag msg-payload)))

(defn add-message-filter
  "Filters message fiter."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state update-in [:message-filters] conj msg-payload)
  (swap! cmp-state update-in [:messages] (partial apply-message-filters cmp-state))
  (persist-settings cmp-state))

(defn remove-message-filter
  "Removes message filter. Since we're not keeping messages that do not match filters,
  only messages that will be captured after filter gets removed will be displayed."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state update-in [:message-filters] disj msg-payload)
  (persist-settings cmp-state))

(defn clear-messages
  "Clears the list of messages."
  [{:keys [cmp-state]}]
  (swap! cmp-state assoc :messages '()))

(defn clear-state-snapshots
  "Clears state snapshots."
  [{:keys [cmp-state]}]
  (swap! cmp-state assoc :state-snapshots '()))

(defn reset
  "Resets state."
  [{:keys [cmp-state]}]
  (reset! cmp-state (initial-state)))

(defn next-younger-message
  [{:keys [cmp-state]}]
  (let [{:keys [selected-message messages]} @cmp-state
        younger-msg (second (drop-while #(not (u/msg-eq selected-message %)) messages))]
    (swap! cmp-state assoc :selected-message younger-msg)))

(defn next-older-message
  [{:keys [cmp-state]}]
  (let [{:keys [selected-message messages]} @cmp-state
        older-msg (second (drop-while #(not (u/msg-eq selected-message %)) (reverse messages)))]
    (swap! cmp-state assoc :selected-message older-msg)))

(defn handle-probe-error
  "Probe error can occur in various situations, i.e. no probe being present. But it can also occur
  when user reloads the page without closing the extension first. In this case we should also clear
  the state so the extension is empty after page is reloaded. Otherwise old messages/state snapshots
  would be displayed even though they're not present in the app after reload."
  [{:keys [cmp-state]}]
  (reset! cmp-state (initial-state))
  (show-component {:cmp-state cmp-state :msg-payload :probe-error}))

(defn set-message-limit
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state assoc :message-limit msg-payload)
  (persist-settings cmp-state))

(defn mk-state
  [put-fn]
  {:state (atom (initial-state))})

(defn component
  [cmp-id]
  (comp/make-component {:cmp-id      cmp-id
                        :state-fn    mk-state
                        :handler-map {:cmd/probe-error           handle-probe-error
                                      :cmd/new-messages          handle-new-messages
                                      :cmd/new-state-snapshots   handle-new-state-snapshots
                                      :cmd/message-details       show-message-details
                                      :cmd/add-message-filter    add-message-filter
                                      :cmd/remove-message-filter remove-message-filter
                                      :cmd/clear-messages        clear-messages
                                      :cmd/clear-state-snapshots clear-state-snapshots
                                      :cmd/next-younger-message  next-younger-message
                                      :cmd/next-older-message    next-older-message
                                      :cmd/set-message-limit     set-message-limit
                                      :cmd/reset                 reset
                                      :cmd/show-component        show-component}}))
