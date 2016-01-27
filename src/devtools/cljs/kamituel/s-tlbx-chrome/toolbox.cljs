(ns kamituel.s-tlbx-chrome.toolbox
  "Toolbox with buttons."
  (:require [matthiasn.systems-toolbox-ui.reagent :as r]))

(defn component-button
  [cmd current-view label id]
  [:button (merge {:on-click (cmd :cmd/show-component id)}
                  (when (= id current-view) {:class "selected"})) label])

(defn view-fn
  [{:keys [observed local cmd]}]
  (let [view (:view @observed)]
    [:div
     [:div#components
      (component-button cmd view [:span.icon-mail-alt] :cmp/messages)
      (component-button cmd view [:span.icon-camera] :cmp/state-snapshots)
      (component-button cmd view [:span.icon-cog] :cmp/settings)]
     [:div#tools
      (when (= :cmp/messages view)
        [:button {:on-click (cmd :cmd/clear-messages)} [:span.icon-cancel-circled]])
      (when (= :cmp/state-snapshots view)
        [:button {:on-click (cmd :cmd/clear-state-snapshots)} [:span.icon-cancel-circled]])
      [:button {:on-click (cmd :cmd/reset)} [:span.icon-attention.warn]]]]))

(defn cmp-map
  [cmp-id]
  (r/cmp-map {:cmp-id      cmp-id
              :view-fn     view-fn
              :dom-id      "toolbox"}))
