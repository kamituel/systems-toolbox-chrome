(ns kamituel.s-tlbx-chrome.message-details
  "Opens a sidebar pane displaying the whole message."
  (:require [kamituel.s-tlbx-chrome.utils :as u]
            [kamituel.s-tlbx-chrome.chrome :as chrome]
            [matthiasn.systems-toolbox.reagent :as r]))

(defn bind-to-key-up-down-evts
  "Allow key up / key down navigation throught the list of messages."
  [{:keys [put-fn]}]
  (u/on-key-press :arrow-down #(put-fn [:cmd/next-older-message]))
  (u/on-key-press :arrow-up #(put-fn [:cmd/next-younger-message])))

(defn view-fn
  [{:keys [observed local cmd]}]
  (let [{:keys [src-cmp dst-cmp command ts-time payload meta corr-id tag] :as msg}
        (-> @observed :selected-message u/msg-printable)]
    [:div
     [:h2 "Message details"]
     [:div
      (if msg
      [:div
       [:div.value [:h3 "Source"] [:span src-cmp]]
       [:div.value [:h3 "Destination"] [:span dst-cmp]]
       [:div.value [:h3 "Command"] [:span command]]
       [:div.value [:h3 "Timestamp"] [:span ts-time]]
       [:div.value [:h3 "Tag"] [:span tag]]
       [:div.value [:h3 "Correlation UUID"] [:span corr-id]]
       [:div.value [:h3 "Message body"]]
       [:div.edn-tree.light (u/data->hiccup payload (:expanded-body @local)
                                            (fn [path]
                                              (fn [_]
                                                (swap! local assoc :expanded-body path))))]
       [:div.value [:h3 "Message meta"]]
       [:div.edn-tree.light (u/data->hiccup meta (:expanded-meta @local)
                                            (fn [path]
                                              (fn [_]
                                                (swap! local assoc :expanded-meta path))))]]
      [:div "No message selected."])]]))

(defn component
  [cmp-id]
  (r/component {:cmp-id      cmp-id
                :view-fn     view-fn
                :init-fn     bind-to-key-up-down-evts
                :dom-id      "message-details"}))
