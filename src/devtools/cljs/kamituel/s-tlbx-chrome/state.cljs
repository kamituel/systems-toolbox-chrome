(ns kamituel.s-tlbx-chrome.state
  (:require [kamituel.s-tlbx-chrome.utils :as u]
            [matthiasn.systems-toolbox.component :as comp]
            [alandipert.storage-atom :refer [local-storage]]))

;; A copy used only to persist message filters so they're there when dev tool is re-opened.
;; To retrieve it via JS: localStorage.getItem('["k","settings"]')
(defonce settings
  (local-storage (atom {:ignored-cmds-types #{}}) :settings))

(defn initial-state
  []
  {
   ;; List of messages captured. Oldest first. When messages are filtered, they are
   ;; removed/not stored here.
   :messages '()
   ;; State snapshots. Keys are component ID's, values are the latest snapshots captured.
   :state-snapshots {}
   ;; Currently selected message.
   :selected-message nil
   ;; User can click a message tag and all messages with that tag will get highlighted.
   :selected-tag nil
   :ignored-cmds-types (set (:ignored-cmds-types @settings))
   :view :messages})

(def persistent-settings
  [:ignored-cmds-types])

(defn persist-settings
  [cmp-state]
  (reset! settings (select-keys @cmp-state persistent-settings)))

(defn show-component
  "Shows eitehr a messages, or snapshots panel."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state assoc :view msg-payload)
  (.setAttribute (.. js/document -body) "view" (name msg-payload)))

(defn handle-new-messages
  "Analyzes new messages and appends them to the :messages list in a state."
  [{:keys [cmp-state msg-payload]}]
  #_(.time js/console "handle-new-messages")
  (let [{:keys [view]} @cmp-state
        messages (map-indexed #(assoc %2 :idx (inc %1)) msg-payload)]
   #_(.timeEnd js/console "handle-new-messages")
   (when (= :probe-error view) (show-component {:cmp-state cmp-state :msg-payload :cmp/messages}))
   (swap! cmp-state assoc :messages messages)))

(defn handle-state-snapshots
  "Handle all state messages. Only one state for each component is stored."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state assoc :state-snapshots msg-payload))

(defn show-message-details
  "Makes one message selected, so it can be shown in the sidebar."
  [{:keys [cmp-state msg-payload]}]
  (swap! cmp-state assoc :selected-message msg-payload)
  (swap! cmp-state assoc :selected-tag (:tag msg-payload)))

(defn add-ignored-cmd-type
  "Adds a command type filter."
  [{:keys [cmp-state msg-payload put-fn]}]
  (swap! cmp-state update :ignored-cmds-types conj msg-payload)
  (persist-settings cmp-state)
  (put-fn [:relay/add-ignored-cmd-type msg-payload]))

(defn remove-ignored-cmd-type
  "Removes message filter."
  [{:keys [cmp-state msg-payload put-fn]}]
  (swap! cmp-state update :ignored-cmds-types disj msg-payload)
  (persist-settings cmp-state)
  (put-fn [:relay/remove-ignored-cmd-type msg-payload]))

(defn clear-messages
  "Clears the list of messages."
  [{:keys [cmp-state]}]
  (swap! cmp-state assoc :messages '()))

(defn clear-state-snapshots
  "Clears state snapshots."
  [{:keys [cmp-state]}]
  (swap! cmp-state assoc :state-snapshots '()))

(defn reset
  "Resets state."
  [{:keys [cmp-state]}]
  (reset! cmp-state (initial-state)))

(defn next-younger-message
  [{:keys [cmp-state]}]
  (let [{:keys [selected-message messages]} @cmp-state
        younger-msg (second (drop-while #(not (u/msg-eq selected-message %)) messages))]
    (swap! cmp-state assoc :selected-message younger-msg)))

(defn next-older-message
  [{:keys [cmp-state]}]
  (let [{:keys [selected-message messages]} @cmp-state
        older-msg (second (drop-while #(not (u/msg-eq selected-message %)) (reverse messages)))]
    (swap! cmp-state assoc :selected-message older-msg)))

(defn handle-probe-error
  "Probe error can occur in various situations, i.e. no probe being present. But it can also occur
  when user reloads the page without closing the extension first. In this case we should also clear
  the state so the extension is empty after page is reloaded. Otherwise old messages/state snapshots
  would be displayed even though they're not present in the app after reload."
  [{:keys [cmp-state]}]
  (reset! cmp-state (initial-state))
  (show-component {:cmp-state cmp-state :msg-payload :probe-error}))

(defn mk-state
  [put-fn]
  (let [{:keys [ignored-cmds-types] :as s} (initial-state)]
    (doseq [cmd ignored-cmds-types]
      (put-fn [:relay/add-ignored-cmd-type cmd]))
    {:state (atom s)}))

(defn cmp-map
  [cmp-id]
  {:cmp-id      cmp-id
   :state-fn    mk-state
   :handler-map {:cmd/probe-error             handle-probe-error
                 :cmd/new-messages            handle-new-messages
                 :cmd/state-snapshots         handle-state-snapshots
                 :cmd/message-details         show-message-details
                 :cmd/add-ignored-cmd-type    add-ignored-cmd-type
                 :cmd/remove-ignored-cmd-type remove-ignored-cmd-type
                 :cmd/clear-messages          clear-messages
                 :cmd/clear-state-snapshots   clear-state-snapshots
                 :cmd/next-younger-message    next-younger-message
                 :cmd/next-older-message      next-older-message
                 :cmd/reset                   reset
                 :cmd/show-component          show-component}})
