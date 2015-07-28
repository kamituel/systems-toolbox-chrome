(ns kamituel.s-tlbx-chrome.core
  (:require [matthiasn.systems-toolbox.switchboard :as sb]
            [matthiasn.systems-toolbox.scheduler :as sched]
            [kamituel.s-tlbx-chrome.state :as state]
            [kamituel.s-tlbx-chrome.messages :as msgs]
            [kamituel.s-tlbx-chrome.background-page-relay :as relay]
            [kamituel.s-tlbx-chrome.message-details :as msg-details]))

(enable-console-print!)

(defonce switchboard (sb/component :switchbrd))

(defn init
  []
  (sb/send-mult-cmd
    switchboard
    [[:cmd/wire-comp
      [(sched/component :cmp/scheduler)
       (state/component :cmp/state)
       (msgs/component :cmp/messages)
       (msg-details/component :cmp/msg-details)
       (relay/component :cmp/relay)]]

     [:cmd/route {:from :cmp/relay :to :cmp/state}]
     [:cmd/route {:from :cmp/messages :to :cmp/state}]

     [:cmd/observe-state {:from :cmp/state :to :cmp/messages}]
     [:cmd/observe-state {:from :cmp/state :to :cmp/msg-details}]

     ;; Send a periods message to relay to read the latest recordings from the app.
     [:cmd/route {:from :cmp/scheduler :to :cmp/relay}]
     [:cmd/send {:to :cmp/scheduler
                 :msg [:cmd/schedule-new
                       {:timeout 1000 :id :cmd/read-from-app :message [:cmd/read-from-app]
                        :repeat true :initial true}]}]]))

(init)
