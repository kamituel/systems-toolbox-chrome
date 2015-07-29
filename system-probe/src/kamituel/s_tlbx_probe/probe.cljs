(ns kamituel.s-tlbx-probe.probe
  "Systems toolbox component that attaches to systems' firehose to capture state snapshots
  and messages and exposes those to the Chrome DevTools extension."
  (:require [matthiasn.systems-toolbox.component :as comp]
            [matthiasn.systems-toolbox.switchboard :as sb]
            [clojure.walk :refer [postwalk]]))


(defonce state (atom {:recording? false
                      :messages '()
                      :state-snapshots '()}))

(defn encode-js-keywords
  "clj->js strips the namespace part from the keywords. This fn replaces :a/b with \"a__b\""
  [data]
  (postwalk (fn [form]
              (if (and (keyword? form)
                       (namespace form))
                (str "keyword---" (namespace form) "---" (name form))
                form)) data))

(defn ^:export start-recording
  []
  (swap! state assoc :recording? true))

(defn ^:export stop-recording
  []
  (swap! state assoc :recording? false))

(defn ^:export read-recordings
  [n]
  (let [recordings (select-keys @state [:messages :state-snapshots])]
    (swap! state assoc :messages '())
    (swap! state assoc :state-snapshots '())
    (clj->js (encode-js-keywords recordings))))

(defn handle-message
  [arg]
  (when (:recording? @state)
    (swap! state update-in [:messages] conj (select-keys arg [:msg-meta :msg-type :msg-payload]))))

(defn handle-state-snapshot
  [arg]
  (let [cmp-id (-> arg :msg-payload :cmp-id)]
    (when (and (:recording? @state) (not= cmp-id :s-tlbx-probe/probe))
      (swap! state update-in [:state-snapshots] conj (select-keys arg [:msg-meta :msg-type :msg-payload])))))

(defn mk-state
  []
  state)

(defn component
  [cmp-id]
  (comp/make-component {:cmp-id cmp-id
                        :state-fn mk-state
                        :handler-map {:firehose/cmp-put           handle-message
                                      :firehose/cmp-recv          handle-message
                                      :firehose/cmp-publish-state handle-state-snapshot
                                      ;:firehose/cmp-recv-state    log
                                      }}))

(defn init
  [switchboard]
  (sb/send-mult-cmd
    switchboard
    [[:cmd/wire-comp [(component :s-tlbx-probe/probe)]]
     [:cmd/attach-to-firehose :s-tlbx-probe/probe]]))
