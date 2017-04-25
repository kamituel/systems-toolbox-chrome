(ns kamituel.s-tlbx-probe.probe
  "Systems toolbox component that attaches to systems' firehose to capture state snapshots
  and messages and exposes those to the Chrome DevTools extension."
  (:require [matthiasn.systems-toolbox.component :as comp]
            [matthiasn.systems-toolbox.switchboard :as sb]
            [cognitect.transit :as transit]
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
                      :readout-timeout-id nil
                      :probe-init-timestamp (.getTime (js/Date.))}))

(def transit-writer
  (transit/writer :json))

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
  (let [recordings (select-keys @state [:messages :state-snapshots :probe-init-timestamp])
        try-write #(try
                     (transit/write transit-writer %)
                     (catch :default e
                       (.error js/console "Failed to serialize" e (.-data e))))]
    (swap! state assoc :messages '())
    (swap! state assoc :state-snapshots '())
    (try-write recordings)))

(defn handle-message
  [{:keys [current-state] :as msg-map}]
  (when (:recording? current-state)
    {:new-state
     (update-in current-state [:messages] conj
                (select-keys msg-map [:msg-meta :msg-type :msg-payload]))}))

(defn handle-state-snapshot
  [{:keys [current-state] :as msg-map}]
  (let [cmp-id (-> msg-map :msg-payload :cmp-id)]
    (when (and (:recording? current-state) (not= cmp-id :s-tlbx-probe/probe))
      {:new-state
       (update-in current-state [:state-snapshots] conj
                  (select-keys msg-map [:msg-meta :msg-type :msg-payload]))})))

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
