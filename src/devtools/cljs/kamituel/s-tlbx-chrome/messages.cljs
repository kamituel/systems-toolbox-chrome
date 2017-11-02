(ns kamituel.s-tlbx-chrome.messages
  "Table with messages as intercepted from the current tab."
  (:require [kamituel.s-tlbx-chrome.utils :as u]
            [matthiasn.systems-toolbox-ui.reagent :as r]
            [clojure.string :as s]))

(defonce container-dom-id "messages")

(defn by-id
  [id]
  (.getElementById js/document id))

(defn is-element-scrolled-down?
  "Returns true if element that scrolls (i.e. div with overflow) is scrolled all way down
  to the bottom."
  [el]
  (= (.-scrollTop el) (- (.-scrollHeight el) (.-offsetHeight el))))

(defn scroll-to-the-bottom
  "Scrolls element all the way down to the bottom."
  [el]
  (set! (.-scrollTop el) (- (.-scrollHeight el) (.-offsetHeight el))))

(defn save-scroll-status
  "Before component re-renders, stores a flag whether messages window was scrolled to the bottom."
  [_ [_ {:keys [local]}]]
  (swap! local assoc :scrolled-to-the-bottom (is-element-scrolled-down? (by-id "messages"))))

(defn scroll-if-needed
  "If a flag is set, scrolls to the bottom."
  [_ [_ {:keys [local]}]]
  (if (:scrolled-to-the-bottom @local)
         (scroll-to-the-bottom (by-id "messages"))))

(defn view-fn
  [{:keys [observed local cmd]}]
  (let [{:keys [messages selected-message selected-tag]} @observed]
    [:table
     [:tr
       [:th.msg-count "#"]
       [:th.msg-timestamp "Timestamp [s]"]
       [:th.msg-from-component "Source"]
       [:th.msg-to-component "Destination"]
       [:th.msg-command "Command"]]
      (for [{:keys [row-number idx guid src-cmp dst-cmp command ts-rel corr-id tag] :as msg}
            (map-indexed (fn [row-number msg] (assoc msg :row-number row-number)) messages)]
        ^{:key row-number}
        [:tr {:on-click (cmd :cmd/message-details msg)
              :class (s/join " " (cond-> []
                                         (= (:corr-id selected-message) corr-id) (conj "hover")
                                         (= selected-tag tag) (conj "highlighted")))}
         [:td idx]
         [:td ts-rel]
         [:td (str src-cmp)]
         [:td (str dst-cmp)]
         [:td (str command)]])]))

(defn cmp-map
  [cmp-id]
  (r/cmp-map {:cmp-id      cmp-id
              :view-fn     view-fn
              :dom-id      container-dom-id
              :lifecycle-callbacks {:component-will-update save-scroll-status
                                    :component-did-update scroll-if-needed}}))
