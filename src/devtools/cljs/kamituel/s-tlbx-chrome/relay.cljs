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
                (let [form-name (if (keyword? form) (subs (str form) 1) form)
                      [k k-ns k-name] (s/split form-name #"---")]
                  (if (= "keyword" k)
                    (keyword (if (empty? k-ns) nil k-ns) k-name)
                    form))
                form)) data))

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
        (let [{:keys [messages state-snapshots]}
              (decode-js-keywords (js->clj response :keywordize-keys false))]
          (put-fn [:cmd/new-messages messages])
          (put-fn [:cmd/new-state-snapshots state-snapshots]))))))

(defn mk-state
  [put-fn]
  (atom {}))

(defn component
  [cmp-id]
  (comp/make-component {:cmp-id      cmp-id
                        :state-fn    mk-state
                        :handler-map {:cmd/read-from-app read-from-app}}))
