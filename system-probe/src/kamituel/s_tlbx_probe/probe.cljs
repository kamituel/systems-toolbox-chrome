(ns kamituel.s-tlbx-probe.probe
  "Systems toolbox component that attaches to systems' firehose to capture state snapshots
  and messages and exposes those to the Chrome DevTools extension."
  (:require [matthiasn.systems-toolbox.component :as comp]
            [matthiasn.systems-toolbox.switchboard :as sb]
            [clojure.walk :refer [postwalk]]
            [cljs.pprint :as pprint]))


(defonce state (atom {:recording? false
                      :messages '()
                      :state-snapshots '()
                      ;; DevTools extension is expected to read recordings from this probe every
                      ;; second. A timeout of 10 seconds is set up after each reading to stop
                      ;; recording in case extension has been closed. This is to prevent
                      ;; accumulating many messages / state snapshots in this atom which could clog
                      ;; the memory and make target application unresponsive.
                      :readout-timeout 10000
                      :readout-timeout-id nil}))

(defn encode-js-keywords
  "clj->js strips the namespace part from the keywords. This fn replaces :a/b with \"a__b\"
  so it can be recovered on the other end."
  [data]
  (postwalk (fn [form]
              (if (keyword? form)
                (str "keyword---" (namespace form) "---" (name form))
                form)) data))

(defn stop-recording
  []
  (prn "Toolbox DevTools recording stopped because of a timeout.")
  (swap! state assoc :recording? false))

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
    (swap! state assoc :recording? true)))

(defn ^:export read-recordings
  [n]
  (start-recording)
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
  {:state state})

(defn cmp-map
  [cmp-id]
  {:cmp-id       cmp-id
   :state-fn     mk-state
   :handler-map {:firehose/cmp-put           handle-message
                 :firehose/cmp-recv          handle-message
                 :firehose/cmp-publish-state handle-state-snapshot
                 ;:firehose/cmp-recv-state    log
                 }
   ;; TODO: verify it helps with figwheel reloading.
   :opts         {:reload-cmp false}})

(defn init
  [switchboard]
  (sb/send-mult-cmd
    switchboard
    [[:cmd/init-comp [(cmp-map :s-tlbx-probe/probe)]]
     [:cmd/attach-to-firehose :s-tlbx-probe/probe]]))
