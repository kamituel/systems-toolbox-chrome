(ns kamituel.s-tlbx-chrome.state
  (:require [kamituel.s-tlbx-chrome.utils :as u]
            [matthiasn.systems-toolbox.component :as comp]))


(defonce initial-state
  {:messages '()
   :state-snapshots {}
   :selected-message nil
   :message-filters #{}
   :view :messages})

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
  (let [messages (->> msg-payload
                      (map (partial u/raw-msg->map))
                      correlate-sender-with-receiver
                      (apply-message-filters cmp-state))]
   (swap! cmp-state update-in [:messages] #(concat messages %))))

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
  (swap! cmp-state assoc :selected-message msg-payload))

(defn add-message-filter
  "Filters message fiter."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state update-in [:message-filters] conj msg-payload)
  (swap! cmp-state update-in [:messages] (partial apply-message-filters cmp-state)))

(defn remove-message-filter
  "Removes message filter. Since we're not keeping messages that do not match filters,
  only messages that will be captured after filter gets removed will be displayed."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state update-in [:message-filters] disj msg-payload))

(defn clear-messages
  "Clears the list of messages."
  [{:keys [cmp-state]}]
  (swap! cmp-state assoc :messages '()))

(defn clear-state-snapshots
  "Clears state snapshots."
  [{:keys [cmp-state]}]
  (swap! cmp-state assoc :state-snapshots '()))

(defn clear-filters
  "Clears filters."
  [{:keys [cmp-state]}]
  (swap! cmp-state assoc :filter-in [])
  (swap! cmp-state assoc :filter-out []))

(defn reset
  "Resets state."
  [{:keys [cmp-state]}]
  (reset! cmp-state initial-state))

(defn show-component
  "Shows eitehr a messages, or snapshots panel."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state assoc :view msg-payload)
  (.setAttribute (.. js/document -body) "view" (name msg-payload)))

(defn next-younger-message
  [{:keys [cmp-state]}]
  (let [{:keys [selected-message messages]} @cmp-state
        younger-msg (second (drop-while #(not (u/msg-eq selected-message %)) (reverse messages)))]
    (swap! cmp-state assoc :selected-message younger-msg)))

(defn next-older-message
  [{:keys [cmp-state]}]
  (let [{:keys [selected-message messages]} @cmp-state
        older-msg (second (drop-while #(not (u/msg-eq selected-message %)) messages))]
    (swap! cmp-state assoc :selected-message older-msg)))

(defn mk-state
  [put-fn]
  (atom initial-state))

(defn component
  [cmp-id]
  (comp/make-component {:cmp-id      cmp-id
                        :state-fn    mk-state
                        :handler-map {:cmd/new-messages          handle-new-messages
                                      :cmd/new-state-snapshots   handle-new-state-snapshots
                                      :cmd/message-details       show-message-details
                                      :cmd/add-message-filter    add-message-filter
                                      :cmd/remove-message-filter remove-message-filter
                                      :cmd/clear-messages        clear-messages
                                      :cmd/clear-state-snapshots clear-state-snapshots
                                      :cmd/clear-filters         clear-filters
                                      :cmd/next-younger-message  next-younger-message
                                      :cmd/next-older-message    next-older-message
                                      :cmd/reset                 reset
                                      :cmd/show-component        show-component}}))
