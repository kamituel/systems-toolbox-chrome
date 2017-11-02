(ns kamituel.s-tlbx-probe.utils)
          
(defn number->str
  "Converts a number to a string with a given number of decimal places.
  I.e. for three decimal places:
    3      -> 3.000
    3.1    -> 3.100
    3.123  -> 3.123
    3.1238 -> 3.123"
  [n decimal-places]
  (let [s (str n)
        s-decimal (if (re-find #"\." s) s (str s ".0"))
        fill-zeros (apply str (take decimal-places (repeat 0)))]
    (re-find (re-pattern (str ".*\\..{" decimal-places "}")) (str s-decimal fill-zeros))))

(defn relative-timestamp-str
  [reference-timestamp firehose-msg]
  (let [msg-ts (-> firehose-msg :msg-meta :s-tlbx-probe/probe :in-ts)]
    (number->str (/ (- msg-ts reference-timestamp) 1000) 3)))

(defn raw-msg->map
  "Converts message as received from the toolbox's firehose to the simple map."
  [reference-timestamp msg]
  (if-not msg
    nil
    {:src-cmp (-> msg :msg-payload :cmp-id)
     :dst-cmp (-> msg :dest-cmp)
     :command (-> msg :msg-payload :msg first)
     :payload (-> msg :msg-payload :msg second)
     :meta    (-> msg :msg-payload :msg-meta)
     :ts      (-> msg :msg-meta :s-tlbx-probe/probe :in-ts)
     :ts-rel  (relative-timestamp-str reference-timestamp msg)
     :type    (-> msg :msg-type)
     :corr-id (-> msg :msg-payload :msg-meta :corr-id)
     :tag     (-> msg :msg-payload :msg-meta :tag)
     :cmp-seq (-> msg :msg-payload :msg-meta :cmp-seq)}))

(defn raw-state-snapshot->map
  [reference-timestamp state-snapshot]
  (if-not state-snapshot
    nil
    {:cmp-id   (-> state-snapshot :msg-payload :cmp-id)
     :snapshot (-> state-snapshot :msg-payload :snapshot)
     :ts       (-> state-snapshot :msg-meta :s-tlbx-probe/probe :in-ts)
     :ts-rel   (relative-timestamp-str reference-timestamp state-snapshot)}))

(defn sent?
  [msg]
  (= :firehose/cmp-put (:type msg)))

(defn received?
  [msg]
  (= :firehose/cmp-recv (:type msg)))

(defn correlated?
  [msg-1 msg-2]
  (= (:corr-id msg-1) (:corr-id msg-2)))

(defn same-tag?
  [msg-1 msg-2]
  (= (:tag msg-1) (:tag msg-2)))

(defn merge-correlated
  [msg-1 msg-2]
  (if (sent? msg-1)
    (assoc msg-1 :dst-cmp (:src-cmp msg-2))
    (assoc msg-2 :dst-cmp (:src-cmp msg-1))))

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