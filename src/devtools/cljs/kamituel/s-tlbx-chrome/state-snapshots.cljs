(ns kamituel.s-tlbx-chrome.state-snapshots
  "Table with state snapshots as intercepted from the current tab."
  (:require [kamituel.s-tlbx-chrome.utils :as u]
            [matthiasn.systems-toolbox-ui.reagent :as r]))


(defn view-fn
  [{:keys [observed local cmd]}]
  (let [snapshots (:state-snapshots @observed)
        _ (when-not(:selected-snapshot-cmp-id @local)
            (swap! local assoc :selected-snapshot-cmp-id (first (keys snapshots))))
        selected-snapshot (:selected-snapshot-cmp-id @local)]
    (if-not (empty? snapshots)
      [:div
        [:ul.tabs
         (for [[cmp-id snapshot] snapshots]
           ^{:key cmp-id}
           [:li (merge {:on-click (u/cbk #(swap! local assoc :selected-snapshot-cmp-id cmp-id))}
                       (when (= cmp-id selected-snapshot)
                         {:class :selected}))
            (str cmp-id)])]
        [:div.edn-tree
         (u/data->hiccup (-> snapshots (get-in [selected-snapshot]) :msg-payload :snapshot)
                         (-> @local :expanded selected-snapshot)
                         (fn [path]
                           (fn [evt]
                             (swap! local assoc-in [:expanded selected-snapshot] path))))]]
      [:h2 "No state snapshots captured yet."])))

(defn cmp-map
  [cmp-id]
  (r/cmp-map {:cmp-id      cmp-id
              :view-fn     view-fn
              :initial-state {:selected-snapshot-cmp-id nil
                              :expanded {}}
              :dom-id      "state-snapshots"}))
