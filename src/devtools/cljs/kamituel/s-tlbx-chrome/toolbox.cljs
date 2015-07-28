(ns kamituel.s-tlbx-chrome.toolbox
  "Toolbox with buttons."
  (:require [matthiasn.systems-toolbox.reagent :as r]))


(defn view-fn
  [{:keys [observed local cmd]}]
  (let []
    [:div
     [:button {:on-click (cmd :cmd/clear-messages)} "C"]]))

(defn component
  [cmp-id]
  (r/component {:cmp-id      cmp-id
                :view-fn     view-fn
                :dom-id      "toolbox"}))
