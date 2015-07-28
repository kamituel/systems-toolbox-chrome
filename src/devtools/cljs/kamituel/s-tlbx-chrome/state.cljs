(ns kamituel.s-tlbx-chrome.state
  (:require [matthiasn.systems-toolbox.component :as comp]))

(defonce initial-state
  (atom {:messages '()
   :state-snapshots '()
   :selected-message nil}))

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

(defn handle-new-messages
  "Analyzes new messages and appends them to the :messages list in a state."
  [{:keys [cmp-state msg-payload]}]
  (let [messages (->> msg-payload
                      correlate-sender-with-receiver
                      (map #(assoc % :guid (kinda-guid))))]
   (swap! cmp-state update-in [:messages] #(concat messages %))))

(defn show-message-details
  "Makes one message selected, so it can be shown in the sidebar."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state assoc :selected-message msg-payload))

(defn mk-state
  [put-fn]
  initial-state)

(defn component
  [cmp-id]
  (comp/make-component {:cmp-id      cmp-id
                        :state-fn    mk-state
                        :handler-map {:cmd/new-messages    handle-new-messages
                                      :cmd/message-details show-message-details}}))
