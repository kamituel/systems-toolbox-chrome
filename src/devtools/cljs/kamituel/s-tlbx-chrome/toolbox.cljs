(ns kamituel.s-tlbx-chrome.toolbox
  "Toolbox with buttons."
  (:require [matthiasn.systems-toolbox.reagent :as r]))

(defn component-button
  [cmd current-view label id]
  [:button (merge {:on-click (cmd :cmd/show-component id)}
                  (when (= id current-view) {:class "selected"})) label])

(defn view-fn
  [{:keys [observed local cmd]}]
  (let []
    [:div
     [:div#components
      (component-button cmd (:view @observed) [:span.icon-mail-alt] :cmp/messages)
      (component-button cmd (:view @observed) [:span.icon-camera] :cmp/state-snapshots)]
     [:div#tools
      [:button {:on-click (cmd :cmd/clear-messages)} [:span.icon-cancel-circled]]]]))

(defn component
  [cmp-id]
  (r/component {:cmp-id      cmp-id
                :view-fn     view-fn
                :dom-id      "toolbox"}))
