(ns kamituel.s-tlbx-chrome.core
  (:require [matthiasn.systems-toolbox.switchboard :as sb]
            [matthiasn.systems-toolbox.scheduler :as sched]
            [kamituel.s-tlbx-probe.probe :as devtools-probe]
            [kamituel.s-tlbx-chrome.mock :as mock]
            [kamituel.s-tlbx-chrome.chrome :as chrome]
            [kamituel.s-tlbx-chrome.state :as state]
            [kamituel.s-tlbx-chrome.messages :as msgs]
            [kamituel.s-tlbx-chrome.filters :as filters]
            [kamituel.s-tlbx-chrome.state-snapshots :as state-snapshots]
            [kamituel.s-tlbx-chrome.toolbox :as toolbox]
            [kamituel.s-tlbx-chrome.relay :as relay]
            [kamituel.s-tlbx-chrome.message-details :as msg-details]))

(enable-console-print!)

(defonce switchboard (sb/component :switchbrd))

(defn init
  []
  (devtools-probe/init switchboard)
  (sb/send-mult-cmd
    switchboard
    [[:cmd/wire-comp
      [(sched/component :cmp/scheduler)
       (state/component :cmp/state)
       (msgs/component :cmp/messages)
       (filters/component :cmp/filters)
       (state-snapshots/component :cmp/state-snapshots)
       (msg-details/component :cmp/msg-details)
       (toolbox/component :cmp/toolbox)]]

     [:cmd/route {:from :cmp/messages :to :cmp/state}]
     [:cmd/route {:from :cmp/state-snapshots :to :cmp/state}]
     [:cmd/route {:from :cmp/msg-details :to :cmp/state}]
     [:cmd/route {:from :cmp/toolbox :to :cmp/state}]
     [:cmd/route {:from :cmp/filters :to :cmp/state}]

     [:cmd/observe-state {:from :cmp/state :to :cmp/messages}]
     [:cmd/observe-state {:from :cmp/state :to :cmp/state-snapshots}]
     [:cmd/observe-state {:from :cmp/state :to :cmp/msg-details}]
     [:cmd/observe-state {:from :cmp/state :to :cmp/toolbox}]
     [:cmd/observe-state {:from :cmp/state :to :cmp/filters}]

     ;; Show messages panel by default.
     [:cmd/send {:to :cmp/state :msg [:cmd/show-component :cmp/messages]}]])

  (if chrome/in-chrome?
    (sb/send-mult-cmd
      switchboard
      [[:cmd/wire-comp [(relay/component :cmp/relay)]]
       [:cmd/route {:from :cmp/relay :to :cmp/state}]

       ;; Send a periods message to relay to read the latest recordings from the app.
       [:cmd/route {:from :cmp/scheduler :to :cmp/relay}]
       [:cmd/send {:to :cmp/scheduler
                   :msg [:cmd/schedule-new
                         {:timeout 1000 :id :cmd/read-from-app :message [:cmd/read-from-app]
                          :repeat true :initial true}]}]])
    ;; When not running as a Chrome DevTools extension, use mock data to populate some of the UI.
    ;; This allows for in-browser development workflow.
    (sb/send-mult-cmd
      switchboard
      [[:cmd/send {:to :cmp/state :msg [:cmd/new-messages mock/messages]}]
       [:cmd/send {:to :cmp/state :msg [:cmd/new-state-snapshots mock/state-snapshots]}]])))

(init)
