(ns kamituel.s-tlbx-chrome.filters
  "List of filters that have been set up."
  (:require [kamituel.s-tlbx-chrome.utils :as u]
            [matthiasn.systems-toolbox-ui.reagent :as r]))

(defn toggle-button
  [local path on-label off-label]
  [:div.toggle-button.inline {:data-on on-label :data-off off-label
                              :data-state (if (get-in @local path) "on" "off")
                              :on-click (u/cbk #(swap! local update-in path not))}])

(defn toggle-text
  [local path label initial-state]
  [:div.toggle-text.inline {:data-state (if (get-in @local path) "on" "off")
                            :on-click (u/cbk #(swap! local update-in path not))} label])

(defn filter-criteria
  [{:keys [new-filter]} {:keys [src-cmp dst-cmp command]}]
  {:src-cmp (if (:src-cmp new-filter) src-cmp nil)
   :dst-cmp (if (:dst-cmp new-filter) dst-cmp nil)
   :command (if (:command new-filter) command nil)
   :type (if (:type new-filter) :show-similar :hide-similar)})

(defn new-filter-valid?
  "Filter is valid if user enabled at least one criteria: src-cmp, dst-cmp or command."
  [local msg]
  (some #(not (nil? %)) (vals (dissoc (filter-criteria @local msg) :type))))

(defn view-fn
  [{:keys [observed local cmd]}]
  (let [{:keys [message-filters selected-message]} @observed]
    [:div
     [:h2 "Filters"]
     [:div
       [:table
        [:thead
          [:tr [:th.type ""] [:th.src "Source"] [:th.dst "Destination"] [:th.cmd "Command"]
           [:th.action ""]]]
        [:tbody
         (if (empty? message-filters)
           [:tr
            [:td.single-col {:col-span 5}
             "No filters specified. Select message, select filtering criteria and then click green icon to add."]]
           (for [{:keys [type src-cmp dst-cmp command] :as mf} message-filters]
             ^{:key (hash mf)}
             [:tr
              [:td (if (= :hide-similar type) [:span.icon-eye-off] [:span.icon-eye])]
              [:td (if src-cmp (str src-cmp) "-")]
              [:td (if dst-cmp (str dst-cmp) "-")]
              [:td (if command (str command) "-")]
              [:td [:span.icon-minus-circled.warn {:on-click (cmd :cmd/remove-message-filter mf)}]]]))]
        (when selected-message
          (let [{:keys [type]} (:new-filter @local)]
            [:tfoot
             [:td.single-col {:col-span 5}
              (toggle-button local [:new-filter :type] "Show" "Hide")
              (if type "only messages" "all messages")
              (toggle-text local [:new-filter :src-cmp] (str "from " (:src-cmp selected-message)) "off")
              (toggle-text local [:new-filter :dst-cmp] (str "to " (:dst-cmp selected-message)) "off")
              (toggle-text local [:new-filter :command] (str "with command " (:command selected-message)) "on")
              (when (new-filter-valid? local selected-message)
                [:span.icon-plus-circled.confirm {:on-click (cmd :cmd/add-message-filter (filter-criteria @local selected-message))}])]]))]]]))

(defn cmp-map
  [cmp-id]
  (r/cmp-map {:cmp-id      cmp-id
              :view-fn     view-fn
              :initial-state {:new-filter {:type false :src-cmp false :dst-cmp false :command true}}
              :dom-id      "filters"}))
