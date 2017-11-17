(ns kamituel.s-tlbx-chrome.relay
  "Relays data to/from extensions's background page."
  (:require [matthiasn.systems-toolbox.component :as comp]
            [kamituel.s-tlbx-chrome.chrome :as chrome]
            [cognitect.transit :as transit]
            [com.cognitect.transit.types :as transit.types]
            [clojure.string :as s]
            [clojure.walk :refer [postwalk]]))

;; In order for UUID's deserialised from Transit to satisfy uuid?.
;; https://github.com/cognitect/transit-cljs/issues/18
(extend-type transit.types/UUID IUUID)

(def transit-reader
  (transit/reader :json))

(defn probe-ipc
  [method-name args callback]
  (.eval (chrome/inspected-window) (str "kamituel.s_tlbx_probe.probe." method-name "(" args ");") callback))

(defn read-from-app
  [{:keys [put-fn]}]
  (probe-ipc
    "read_logs" ""
    (fn [response err]
      (prn "r" (subs response 0 100))
      (if err
        (put-fn [:cmd/probe-error])
        (let [{:keys [messages state-snapshots probe-init-timestamp]}
              (transit/read transit-reader response)]
          (put-fn [:cmd/new-messages messages])
          (put-fn [:cmd/state-snapshots state-snapshots]))))))

(defn add-ignored-cmd-type
  [{:keys [put-fn msg-payload]}]
  (probe-ipc "ignore_cmd_type" (str "'" (namespace msg-payload) "/" (name msg-payload) "'")
    (fn [response err]
      (when err
        (put-fn [:cmd/probe-error])))))

(defn remove-ignored-cmd-type
  [{:keys [put-fn msg-payload]}]
  (probe-ipc "stop_ignoring_cmd_type" (str "'" (namespace msg-payload) "/" (name msg-payload) "'")
    (fn [response err]
      (when err
        (put-fn [:cmd/probe-error])))))

(defn mk-state
  [put-fn]
  {:state (atom {})})

(defn cmp-map
  [cmp-id]
  {:cmp-id      cmp-id
   :state-fn    mk-state
   :handler-map {:relay/read-from-app read-from-app
                 :relay/add-ignored-cmd-type add-ignored-cmd-type
                 :relay/remove-ignored-cmd-type remove-ignored-cmd-type}})
