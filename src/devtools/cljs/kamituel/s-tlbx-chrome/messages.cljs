(ns kamituel.s-tlbx-chrome.messages
  "Table with messages as intercepted from the current tab."
  (:require [matthiasn.systems-toolbox.reagent :as r]
            [reagent.core :as reagent]))

(defonce container-dom-id "messages")

(defn by-id
  [id]
  (.getElementById js/document id))

(defn is-element-scrolled-down?
  "Returns true if element that scrolls (i.e. div with overflow) is scrolled all way down
  to the bottom."
  [el]
  (prn (.-scrollTop el) (.-scrollHeight el) (.-offsetHeight el))
  (= (.-scrollTop el) (- (.-scrollHeight el) (.-offsetHeight el))))

(defn scroll-to-the-bottom
  "Scrolls element all the way down to the bottom."
  [el]
  (set! (.-scrollTop el) (- (.-scrollHeight el) (.-offsetHeight el))))

(defn view-fn
  [{:keys [observed local cmd]}]
  (let [messages (:messages @observed)
        last-ts (-> messages last :msg-meta :s-tlbx-probe/probe :in-timestamp)]
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
         [:td (/ (- (-> msg-meta :s-tlbx-probe/probe :in-timestamp) last-ts) 1000)]
         [:td (str (:cmp-id msg-payload))]
         [:td (str dest-cmp)]
         [:td (str (-> msg-payload :msg first))]])]))

(def reagent-component
  (reagent/create-class
    {:component-will-update
     (fn [a [b {:keys [observed local put-fn cmd]}]]
       (prn a)
       (set! (.-a js/window) a)
       (set! (.-b js/window) b)
       (swap! local assoc :scrolled-to-the-bottom (is-element-scrolled-down? (by-id "messages"))))
     :component-did-update
     (fn [_ [_ {:keys [observed local put-fn cmd]}]]
       (if (:scrolled-to-the-bottom @local)
         (scroll-to-the-bottom (by-id "messages"))))
     :reagent-render view-fn}))

(defn component
  [cmp-id]
  (r/component {:cmp-id      cmp-id
                :view-fn     reagent-component
                :dom-id      container-dom-id}))
