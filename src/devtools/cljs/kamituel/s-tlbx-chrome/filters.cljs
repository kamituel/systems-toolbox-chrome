(ns kamituel.s-tlbx-chrome.filters
  "List of filters that have been set up."
  (:require [kamituel.s-tlbx-chrome.utils :as u]
            [matthiasn.systems-toolbox-ui.reagent :as r]))

(def no-filters-message
  [:tbody
    [:tr
      [:td {:colSpan 2}
      "All commands are being captured. Select message and then click green icon to ignore it the next time."]]])

(defn view-fn
  [{:keys [observed cmd]}]
  (let [{:keys [ignored-cmds-types selected-message]} @observed
        selected-command (:command selected-message)]
    [:div
     [:h2 "Filters"]
     [:div
       [:table
        [:thead
          [:tr [:th.cmd "Command"] [:th]]]
        (if (empty? ignored-cmds-types)
          no-filters-message
          (into [:tbody]
            (for [command ignored-cmds-types]
              [:tr {:key (str command)}
               [:td (str command)]
               [:td [:span.icon-minus-circled.warn {:on-click (cmd :cmd/remove-ignored-cmd-type command)}]]])))
        (when (and selected-message (not (contains? ignored-cmds-types selected-command)))
          [:tfoot
            [:td
              (str selected-command)]
            [:td
              [:span.icon-plus-circled.confirm
                {:on-click (cmd :cmd/add-ignored-cmd-type selected-command)}]]])]]]))

(defn cmp-map
  [cmp-id]
  (r/cmp-map {:cmp-id      cmp-id
              :view-fn     view-fn
              :dom-id      "filters"}))
