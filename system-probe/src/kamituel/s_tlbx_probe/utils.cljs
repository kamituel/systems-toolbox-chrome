(ns kamituel.s-tlbx-probe.utils
  (:require goog.object
            [clojure.data :refer [diff]]
            [clojure.walk :as walk]
            [cljs.pprint :as pprint]))
          
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

(defn correlate-sender-with-receivers
  "Each message should be observed by the probe (at least) twice - once when it is being sent
  by the originating component (it's :type is then :firehose/cmp-put) and once when it is being
  received by the destination component (:type = :firehose/cmp-recv).

  When routing in the switchboard is configured so that multiple destination components are going
  to receive the same message, there might be multiple observations at the destination components.

  When routing in the switchboard is invalid, there will be a sent message, but no received one.

  Thus the relationship between a sent message and received ones is 1-* (one-to-many), with 1-1
  being the most common case, but 1-0 and 1-2+ being also possible.

  Messages sent and received can be matched using :corr-id (assigned by systems-toolbox)."
  [messages]
  (let [by-corr-id (vals (group-by :corr-id messages))
        correlate-all (fn [msgs]
                        (let [all-sent (filter sent? msgs)
                              all-received (filter received? msgs)]
                          (if (empty? all-sent)
                            all-received

                            (let [sent (first all-sent)]
                              (when (> (count all-sent) 1)
                                (js/console.error "Multiple sent messages with the same corr-id. "
                                  "Discarding all but the first."
                                  (:command sent)))
                              (map (partial merge-correlated sent) all-received)))))]
    (flatten
      (map (fn [msgs]
             (if (= 1 (count msgs))
               [(first msgs)]
               (correlate-all msgs)))
        by-corr-id))))

(defn diff-state-snapshots
  [s1 s2]
  {:pre [(pos? (:ts s1)) (pos? (:ts s2))]}
  (let [[removed added _] (diff (:snapshot s1) (:snapshot s2))]
    (when (or removed added)
      {:removed removed
      :added added
      :cmp-id (:cmp-id s2)
      :ts (:ts s2)
      :ts-rel (:ts-rel s2)})))

(defn sanitize-value
  [v]
  (cond
    ;; Collections:
    (map? v) (into {} (map (fn [[k v]]
                             (let [k (if (or (string? k) (keyword? k)) k (hash k))]
                              [k (sanitize-value v)])) v))
    (set? v) (set (map sanitize-value v))
    (vector? v) (vec (map sanitize-value v))
    (list? v) (map sanitize-value v)
    
    ;; Not sanitized values:
    (uuid? v) v
    (keyword? v) v
    (nil? v) nil
    (true? v) true
    (false? v) false

    ;; Sanitized values:
    (string? v) (str "[[STRING:" (count v) "]]")
    (number? v) "[[NUMBER]]"
    (fn? v) "[[FUNCTION]]"
    :else "[[UNKNOWN-TYPE]]"))

(defn sanitize-message
  [msg]
  (let [meta-in-out-keys (fn meta-in-out-keys [meta]
                          (filter (fn [k]
                                    (let [v (get meta k)]
                                      (and (map? v)
                                           (= #{:in-ts :out-ts} (set (keys v))))))
                            (keys meta)))]
    (-> msg
      (update :payload sanitize-value)
      (update :tag sanitize-value)
      (update :meta (fn [meta]
                      ;; Anything can be added to meta by the app, so the whole thing neeeds to be
                      ;; sanitized, with the exception of fields we know are safe, because
                      ;; systems-toolbox overrides those.
                      (-> (sanitize-value meta)
                          (merge (select-keys meta [:cmp-seq :corr-id]))
                          ;; Any submap that contains only :in-ts and :out-ts that are numbers,
                          ;; is safe too. Unfortunately, keys where those maps are stored are unkown,
                          ;; as they're cmp-id's that each app will assign arbitrarily.
                          (merge (select-keys meta (meta-in-out-keys meta)))))))))

(defn sanitize-snapshot-diff
  [snapshot-diff]
  (-> snapshot-diff
    (update :added sanitize-value)
    (update :removed sanitize-value)))

(defn sanitize-state-snapshot
  [state-snapshot]
  (update state-snapshot :snapshot sanitize-value))

(defn save-file
  [filename contents-str]
  (let [blob (js/Blob. (clj->js [contents-str]))
        link (js/document.createElement "a")
        url (js/window.URL.createObjectURL blob)]
    (doto link
      (goog.object/set "download" filename)
      (goog.object/set "href" url))
    (js/document.body.appendChild link)
    (.click link)))

(defn prepare-logs
  [all-snapshots snapshots-diffs messages]
  (let [messages (correlate-sender-with-receivers messages)]
    {:timeline (->> (concat messages snapshots-diffs)
                    (sort-by :ts)
                    fix-message-order)
     :final-state-snapshots all-snapshots}))

(defn save-logs
  [& args]
  (try
    (let [logs (apply prepare-logs args)
          now (js/Date.)
          date-str (str (.getUTCFullYear now) "-" (inc (.getUTCMonth now)) "-" (.getUTCDate now)
                        "-" (.getUTCHours now) "-" (.getUTCMinutes now) "-" (.getUTCSeconds now))
          filename (str "console-debug-log-" date-str ".edn")]
      (js/console.log "Saving <<" filename ">> with " (count (:timeline logs)) " events (messages "
        "and snapshot changes) and " (count (:final-state-snapshots logs)) " final snapshots.")
      (save-file filename (with-out-str (pprint/pprint logs))))
    (catch js/Error e
      (js/console.error "Could not generate or save issue report file.")
      (js/console.error e))))
