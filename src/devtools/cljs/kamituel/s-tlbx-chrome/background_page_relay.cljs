(ns kamituel.s-tlbx-chrome.background-page-relay
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
                  ; (prn "split" k k-ns k-name)
                  (if (= "keyword" k)
                    (if k-name
                      (keyword k-ns k-name)
                      (keyword k-ns))
                    form))
                form)) data))

(defn start-recording
  []
  (.eval (chrome/inspected-window) "clj_web_client_console.devtools.start_recording();"
         (fn [response is-exception?]
           (prn "Start recording error?" is-exception?))))

(defn read-from-app
  [{:keys [put-fn]}]
  (let [eval-js "clj_web_client_console.devtools.read_commands();"]
    (.eval (chrome/inspected-window) eval-js (fn [response is-exception?]
                                               (let [msg (decode-js-keywords (js->clj response :keywordize-keys true))]
                                                 ; (prn "msg in relay" msg)
                                                 (put-fn [:cmd/new-messages msg]))))))


; (defn init-background-page
;  [put-fn]
;  (let [bckgrnd-conn (.connect runtime (clj->js {:name "s-tlbx-page"}))
 ;       on-msg-fn (fn [msg] (prn "Message from background page:" msg))]
;    (prn "adding listener")
;    (.addListener (.-onMessage bckgrnd-conn) on-msg-fn)
;    (prn "sending inject message")
;    (.sendMessage runtime (clj->js {:tabId (tab-id)
;                                    :scriptToInject "src/background/injectable.js"}))
;    (prn "eval test")
;    (.eval (inspected-window) "clj_web_client_console.devtools.take_last_n_js(1);" (fn [res is-exception?]
;                                                                                     (let [msg (js->clj res :keywordize-keys true)]
;                                                                                       (prn msg (nil? put-fn))
;                                                                                       (put-fn [:cmd/captured (first msg)]))))))

(defn mk-state
  [put-fn]
  (let [state (atom {})]
    ;(init-background-page put-fn)
    (start-recording)
    state))

(defn component
  [cmp-id]
  (comp/make-component {:cmp-id      cmp-id
                        :state-fn    mk-state
                        :handler-map {:cmd/read-from-app read-from-app}}))
