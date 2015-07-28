(ns kamituel.s-tlbx-chrome.messages
  "Table with messages as intercepted from the current tab."
  (:require [matthiasn.systems-toolbox.reagent :as r]))


(defn trim-long-msg
  [msg]
  (subs msg 0 100))

(defn view-fn
  [{:keys [observed local cmd]}]
  (prn "re-render" observed)
  (let [messages (:messages observed)
        last-ts (-> messages last :msg-meta :frntnd/dev-tools :in-timestamp)]
    [:div "Messages received: " (count messages)
     [:table
      [:tr
       [:th.msg-timestamp "Timestamp [s]"]
       [:th.msg-from-component "From component"]
       [:th.msg-to-component "To component"]
       [:th.msg-command "Command"]
       [:th.msg-message "Message"]]
      (for [{:keys [guid msg-meta msg-payload dest-cmp] :as msg} messages]
        ^{:key guid}
        [:tr {:on-click (cmd :cmd/message-details msg)}
         [:td (/ (- (-> msg-meta :frntnd/dev-tools :in-timestamp) last-ts) 1000)]
         [:td (str (:cmp-id msg-payload))]
         [:td (str dest-cmp)]
         [:td (str (-> msg-payload :msg first))]
         [:td (trim-long-msg (.stringify js/JSON (clj->js (rest (:msg msg-payload)))))]])]]))

(defn component
  [cmp-id]
  (r/component {:cmp-id      cmp-id
                :view-fn     view-fn
                :dom-id      "messages"}))
