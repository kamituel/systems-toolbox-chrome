(ns kamituel.s-tlbx-chrome.settings
  "Settings page."
  (:require [matthiasn.systems-toolbox.reagent :as r]))

(defn handle-input-event
  [f]
  (fn [evt]
    (f (-> evt .-nativeEvent .-target .-value))))

(defn view-fn
  [{:keys [observed put-fn]}]
  (let [{:keys [message-limit-min message-limit-max message-limit]} @observed]
    [:div
     [:label "Max number of messages to capture"]
     [:p message-limit]
     [:input {:type :range :min message-limit-min :max message-limit-max :value message-limit
              :on-change (handle-input-event #(put-fn [:cmd/set-message-limit %]))}]]))

(defn component
  [cmp-id]
  (r/component {:cmp-id      cmp-id
                :view-fn     view-fn
                :dom-id      "settings"}))
