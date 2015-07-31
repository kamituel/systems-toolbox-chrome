(ns kamituel.s-tlbx-chrome.chrome
  "Helpers for Chrome DevTools Extension API interop.")

(def chrome (.-chrome js/window))
(def runtime (.-runtime chrome))
(def devtools (.-devtools chrome))
(defn inspected-window [] (.-inspectedWindow devtools))
(defn tab-id [] (.-tabId (inspected-window)))

;; True if running as a Chrome DevTools extension.
(def in-chrome? (not (nil? devtools)))
