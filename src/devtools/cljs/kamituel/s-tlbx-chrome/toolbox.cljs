(ns kamituel.s-tlbx-chrome.toolbox
  "Toolbox with buttons."
  (:require [matthiasn.systems-toolbox.reagent :as r]))


(defn view-fn
  [{:keys [observed local cmd]}]
  (let []
    [:div
     [:div#components
      [:button {:on-click (cmd :cmd/show-component :cmp/messages)} "m"]
      [:button {:on-click (cmd :cmd/show-component :cmp/state-snapshots)} "s"]]
     [:div#tools
      [:button {:on-click (cmd :cmd/clear-messages)} "c"]]]))

(defn component
  [cmp-id]
  (r/component {:cmp-id      cmp-id
                :view-fn     view-fn
                :dom-id      "toolbox"}))
