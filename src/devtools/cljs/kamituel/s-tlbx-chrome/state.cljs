(ns kamituel.s-tlbx-chrome.state
  (:require [matthiasn.systems-toolbox.component :as comp]))

(defonce initial-state
  {:messages '()
   :state-snapshots {}
   :selected-message nil
   :filter-in []
   :filter-out []
   :view :messages})

(defn kinda-guid
  "Generates a GUID-looking string. Does not conform to RFC though."
  []
  (let [floor #(.floor js/Math %)
        random (fn [] (.random js/Math))
        s4 (fn [] (-> (random) inc (* 65536) floor (.toString 16) (.substring 1)))]
    (str (s4) (s4) "-" (s4) "-" (s4) "-" (s4) "-" (s4) (s4) (s4))))

(defn correlate-sender-with-receiver
  "Matches message sent over a channel with a received one on the other and (by another component),
  so they can be treated as one."
  [messages]
  (let [sent (filter #(= :firehose/cmp-put (:msg-type %)) messages)
        received (filter #(= :firehose/cmp-recv (:msg-type %)) messages)
        ts #(-> % :msg-meta :frntnd/dev-tools :in-timestamp)
        msg #(-> % :msg-payload :msg)
        cmp-id #(-> % :msg-payload :cmp-id)
        recieved-matches-sent? (fn [sent-msg]
                                 (fn [received-msg]
                                   (and (< -5 (- (ts sent-msg) (ts received-msg)) 5)
                                        (= (msg sent-msg) (msg received-msg)))))]
    (map (fn [sent-message]
           (let [matching-received-msg (first (filter (recieved-matches-sent? sent-message) received))]
             (if matching-received-msg
               (assoc sent-message :dest-cmp (cmp-id matching-received-msg))
               sent-message)))
         sent)))

(defn message-matches-all-criteria?
  [pred-fn filter-criteria msg]
  (let [msg-matches-one-criterium? (fn [{:keys [src-cmp dst-cmp cmd]} msg]
                                     (and (if src-cmp (= src-cmp (-> msg :msg-payload :cmp-id)) true)
                                          (if dst-cmp (= dst-cmp (:dest-cmp msg)) true)
                                          (if cmd (= cmd (-> msg :msg-payload :msg first)) true)))]
   (pred-fn #(msg-matches-one-criterium? % msg) filter-criteria)))

(defn apply-message-filters
  [cmp-state messages]
  (->> messages
       (filter (partial message-matches-all-criteria? every? (:filter-in @cmp-state)))
       (filter (partial message-matches-all-criteria? not-any? (:filter-out @cmp-state)))))

(defn handle-new-messages
  "Analyzes new messages and appends them to the :messages list in a state."
  [{:keys [cmp-state msg-payload]}]
  (let [messages (->> msg-payload
                      correlate-sender-with-receiver
                      (apply-message-filters cmp-state)
                      (map #(assoc % :guid (kinda-guid))))]
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
  (prn "showing msg detauls" msg-payload)
  (swap! cmp-state assoc :selected-message msg-payload))

(defn add-filter-inclusive
  "Filters out all messages BUT the ones maching cirteria given."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state update-in [:filter-in] conj msg-payload)
  (swap! cmp-state update-in [:messages] (partial apply-message-filters cmp-state)))

(defn add-filter-exclusive
  "Filters out all messages matching criteria given."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state update-in [:filter-out] conj msg-payload)
  (swap! cmp-state update-in [:messages] (partial apply-message-filters cmp-state)))

(defn clear-messages
  "Clears the list of messages."
  [{:keys [cmp-state]}]
  (swap! cmp-state assoc :messages '()))

(defn show-component
  "Shows eitehr a messages, or snapshots panel."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state assoc :view msg-payload)
  (.setAttribute (.. js/document -body) "view" (name msg-payload)))

(defn mk-state
  [put-fn]
  (atom initial-state))

(defn component
  [cmp-id]
  (comp/make-component {:cmp-id      cmp-id
                        :state-fn    mk-state
                        :handler-map {:cmd/new-messages         handle-new-messages
                                      :cmd/new-state-snapshots  handle-new-state-snapshots
                                      :cmd/message-details      show-message-details
                                      :cmd/filter-in-messages   add-filter-inclusive
                                      :cmd/filter-out-messages  add-filter-exclusive
                                      :cmd/clear-messages       clear-messages
                                      :cmd/show-component       show-component}}))
