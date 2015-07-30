(ns kamituel.s-tlbx-chrome.relay
  "Relays data to/from extensions's background page."
  (:require [matthiasn.systems-toolbox.component :as comp]
            [kamituel.s-tlbx-chrome.chrome :as chrome]
            [clojure.string :as s]
            [clojure.walk :refer [postwalk]]))

(defn decode-js-keywords
  "clj->js strips the namespace part from the keywords. This fn replaces :a/b with \"a__b\""
  [data]
  (postwalk (fn [form]
              (if (or (keyword? form) (string? form))
                (let [[k k-ns k-name] (s/split (if (keyword? form) (name form) form) #"---")]
                  (if (= "keyword" k)
                    (if k-name
                      (keyword k-ns k-name)
                      (keyword k-ns))
                    form))
                form)) data))

(defn start-recording
  []
  (.eval (chrome/inspected-window) "kamituel.s_tlbx_probe.probe.start_recording();"
         (fn [response is-exception?]
           (prn "Start recording error?" is-exception?))))

(defn read-from-app
  [{:keys [put-fn]}]
  (let [eval-js "kamituel.s_tlbx_probe.probe.read_recordings();"
        handle-response (fn [response err]
                          (let [{:keys [messages state-snapshots]}
                                (decode-js-keywords (js->clj response :keywordize-keys true))]
                            (put-fn [:cmd/new-messages messages])
                            (put-fn [:cmd/new-state-snapshots state-snapshots])))]
    (.eval (chrome/inspected-window) eval-js handle-response)))

(defn mk-state
  [put-fn]
  (let [state (atom {})]
    (start-recording)
    state))

(defn component
  [cmp-id]
  (comp/make-component {:cmp-id      cmp-id
                        :state-fn    mk-state
                        :handler-map {:cmd/read-from-app read-from-app}}))
