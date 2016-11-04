(ns kamituel.s-tlbx-chrome.relay
  "Relays data to/from extensions's background page."
  (:require [matthiasn.systems-toolbox.component :as comp]
            [kamituel.s-tlbx-chrome.chrome :as chrome]
            [cognitect.transit :as transit]
            [clojure.string :as s]
            [clojure.walk :refer [postwalk]]))


(def transit-reader
  (transit/reader :json))

(defn probe-ipc
  [method-name callback]
  (.eval (chrome/inspected-window) (str "kamituel.s_tlbx_probe.probe." method-name "();") callback))

(defn read-from-app
  [{:keys [put-fn]}]
  (probe-ipc
    "read_recordings"
    (fn [response err]
      (if err
        (put-fn [:cmd/probe-error])
        (let [{:keys [messages state-snapshots probe-init-timestamp]}
              (transit/read transit-reader response)]
          (put-fn [:cmd/set-probe-init-ts probe-init-timestamp])
          (put-fn [:cmd/new-messages messages])
          (put-fn [:cmd/new-state-snapshots state-snapshots]))))))

(defn mk-state
  [put-fn]
  {:state (atom {})})

(defn cmp-map
  [cmp-id]
  {:cmp-id      cmp-id
   :state-fn    mk-state
   :handler-map {:cmd/read-from-app read-from-app}})
