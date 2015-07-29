(ns kamituel.s-tlbx-chrome.state-snapshots
  "Table with state snapshots as intercepted from the current tab."
  (:require [matthiasn.systems-toolbox.reagent :as r]))


(defn data->hiccup
  ([data expanded-path on-expand-fn]
   (data->hiccup data expanded-path on-expand-fn []))
  ([data expanded-path on-expand-fn current-path]
    (let [key-to-expand (first expanded-path)
          handle-coll (fn [v expand-key]
                        (if (or (not (coll? v)) (= key-to-expand expand-key))
                          [:div (data->hiccup v (rest expanded-path) on-expand-fn (conj current-path expand-key))]
                          [:div.collapsed
                           {:on-click (on-expand-fn (conj current-path expand-key))}
                           (data->hiccup (empty v) expanded-path on-expand-fn [])]))]
      (cond
        (map? data)
        [:div.map (for [[k v] data]
                    [:div.key-val
                     [:div (data->hiccup k expanded-path on-expand-fn (conj current-path k))]
                     (handle-coll v k)])]

        (vector? data)
        [:div.vector (for [[idx v] (map-indexed (fn [idx v] [idx v]) data)] (handle-coll v idx))]

        (seq? data)
        [:div.seq (for [[idx v] (map-indexed (fn [idx v] [idx v]) data)] (handle-coll v idx))]

        (string? data)
        [:span.string data]

        (number? data)
        [:span.number data]

        (keyword? data)
        [:span.keyword (str data)]

        (nil? data)
        [:span.nil "nil"]

        (or (true? data) (false? data))
        [:span.boolean (str data)]

        :else
        (str data)))))

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
           [:li (merge {:on-click #(swap! local assoc :selected-snapshot-cmp-id cmp-id)}
                       (when (= cmp-id selected-snapshot)
                         {:class :selected}))
            (str cmp-id)])]
        [:div.edn-tree
         (data->hiccup (-> snapshots (get-in [selected-snapshot]) :msg-payload :snapshot)
                       (-> @local :expanded selected-snapshot)
                       (fn [path]
                         (fn [evt]
                           (prn "expanded" path)
                           (swap! local assoc-in [:expanded selected-snapshot] path))))]]
      [:h2 "No state snapshots captured yet."])))

(defn component
  [cmp-id]
  (r/component {:cmp-id      cmp-id
                :view-fn     view-fn
                :initial-state {:selected-snapshot-cmp-id nil
                                :expanded {}}
                :dom-id      "state-snapshots"}))
