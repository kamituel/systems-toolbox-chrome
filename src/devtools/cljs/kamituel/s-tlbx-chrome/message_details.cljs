(ns kamituel.s-tlbx-chrome.message-details
  "Opens a sidebar pane displaying the whole message."
  (:require [matthiasn.systems-toolbox.reagent :as r]
            [kamituel.s-tlbx-chrome.chrome :as chrome]))


(defn handle-set-message
  [{:keys [cmp-state msg-payload]}]
  (prn "set message" msg-payload)
  (let [local (:local cmp-state)]
    (swap! local assoc :message msg-payload)))

(defn ts->time
  [ts]
  (let [date (js/Date. ts)
        h (.getHours date)
        m (.getMinutes date)
        s (.getSeconds date)]
    (str (if (< h 10) "0") h ":"
         (if (< m 10) "0") m ":"
         (if (< s 10) "0") s)))

(defn view-fn
  [{:keys [observed local cmd]}]
  (prn "re-render detauls" observed)
  (let [{:keys [msg-meta msg-payload dest-cmp] :as msg} (:message @local)
        ts (-> msg-meta :frntnd/dev-tools :in-timestamp)]
    (if msg
      [:div
       [:h3 "From"] [:p (str (:cmp-id msg-payload))]
       [:h3 "To"] [:p (str dest-cmp)]
       [:h3 "Command"] [:p (str (-> msg-payload :msg first))]
       [:h3 "Timestamp"] [:p (ts->time ts)]
       [:pre (.stringify js/JSON (clj->js (:msg msg-payload)) nil 2)]]
      [:h2 "No message selected."])))

(defn component
  [cmp-id]
  (r/component {:cmp-id      cmp-id
                :view-fn     view-fn
                :dom-id      "sidebar"}))
