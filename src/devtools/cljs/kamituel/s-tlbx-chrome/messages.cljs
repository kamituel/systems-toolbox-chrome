(ns kamituel.s-tlbx-chrome.messages
  "Table with messages as intercepted from the current tab."
  (:require [matthiasn.systems-toolbox.reagent :as r]))


(defn trim-long-msg
  [msg]
  (subs msg 0 100))

(defn view-fn
  ;; #^{:component-did-mount #(prn "@!!!!!!!!!!!!")}
  [{:keys [observed local cmd]}]
  (let [messages (:messages @observed)
        last-ts (-> messages last :msg-meta :frntnd/dev-tools :in-timestamp)]
    [:table
     [:tr
       [:th.msg-count "#"]
       [:th.msg-timestamp "Timestamp [s]"]
       [:th.msg-from-component "From component"]
       [:th.msg-to-component "To component"]
       [:th.msg-command "Command"]]
      (for [{:keys [idx guid msg-meta msg-payload dest-cmp] :as msg}
            (map-indexed (fn [idx msg] (assoc msg :idx idx)) (reverse messages))]
        ^{:key guid}
        [:tr {:on-click (cmd :cmd/message-details msg)}
         [:td idx]
         [:td (/ (- (-> msg-meta :frntnd/dev-tools :in-timestamp) last-ts) 1000)]
         [:td (str (:cmp-id msg-payload))]
         [:td (str dest-cmp)]
         [:td (str (-> msg-payload :msg first))]])]))

(defn component
  [cmp-id]
  (r/component {:cmp-id      cmp-id
                :view-fn     view-fn
                :dom-id      "messages"}))
