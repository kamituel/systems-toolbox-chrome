(ns kamituel.s-tlbx-chrome.message-details
  "Opens a sidebar pane displaying the whole message."
  (:require [matthiasn.systems-toolbox.reagent :as r]
            [kamituel.s-tlbx-chrome.chrome :as chrome]))


(defn handle-set-message
  [{:keys [cmp-state msg-payload]}]
  (prn "set message" msg-payload)
  (let [local (:local cmp-state)]
    (swap! local assoc :message msg-payload)))

(defn ts->time
  [ts]
  (let [date (js/Date. ts)
        h (.getHours date)
        m (.getMinutes date)
        s (.getSeconds date)]
    (str (if (< h 10) "0") h ":"
         (if (< m 10) "0") m ":"
         (if (< s 10) "0") s)))


;; TODO: copied.
(defn checked-handler
  "Event handler, similar to (value-handler), but for checkboxes (so using .-checked
  instead of .-value)"
  [local path]
  (fn
    [ev]
    (swap! local assoc-in path (-> ev .-nativeEvent .-target .-checked))))

(defn filtered-label-value
  [{:keys [filterable? label value local filter-id]}]
  [:div.value
   [:input (merge {:type :checkbox :on-change (checked-handler local [:filter filter-id])}
                  (when-not filterable? {:class :hide-filter}))]
   [:h3 label]
   [:span value]])

(defn build-filter-criteria
  [{:keys [filter]} {:keys [msg-payload dest-cmp]}]
  {:src-cmp (when (:src-cmp filter) (:cmp-id msg-payload))
   :dst-cmp (when (:dst-cmp filter) dest-cmp)
   :cmd (when (:cmd filter) (-> msg-payload :msg first))})

(defn filter-valid?
  [local-snapshot msg]
  (some #(not (nil? %)) (vals (build-filter-criteria local-snapshot msg))))

(defn view-fn
  [{:keys [observed local cmd]}]
  (let [{:keys [msg-meta msg-payload dest-cmp] :as msg} (:selected-message @observed)
        ts (-> msg-meta :frntnd/dev-tools :in-timestamp)]
    (prn "selected msg" msg)
    (if msg
      [:div
       (filtered-label-value {:filterable? true :local local :filter-id :src-cmp :label "Source"
                              :value (str (:cmp-id msg-payload))})
       (filtered-label-value {:filterable? true :local local :filter-id :dst-cmp :label "Destination"
                              :value (str dest-cmp)})
       (filtered-label-value {:filterable? true :local local :filter-id :cmd :label "Command"
                              :value (str (-> msg-payload :msg first))})
       (filtered-label-value {:filterable? false :label "Timestamp" :value (ts->time ts)})
       (when (filter-valid? @local msg)
         [:div.filters
           [:button {:on-click (cmd :cmd/filter-out-messages (build-filter-criteria @local msg))}
            "Hide similar messages"]
           [:button {:on-click (cmd :cmd/filter-in-messages (build-filter-criteria @local msg))}
            "Show similar messages"]])
       [:pre (.stringify js/JSON (clj->js (:msg msg-payload)) nil 2)]]
      [:h2 "No message selected."])))

(defn component
  [cmp-id]
  (r/component {:cmp-id      cmp-id
                :view-fn     view-fn
                :dom-id      "sidebar"}))
