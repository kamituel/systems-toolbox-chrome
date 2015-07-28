(ns kamituel.s-tlbx-chrome.chrome
  "Helpers for Chrome DevTools Extension API interop.")

(def chrome (.-chrome js/window))
(def runtime (.-runtime chrome))
(def devtools (.-devtools chrome))
(defn inspected-window [] (.-inspectedWindow devtools))
(defn tab-id [] (.-tabId (inspected-window)))

(defn create-sidebar-with-data
  [title data]
  (.createSidebarPane (.. devtools -panels -elements) title
                      (fn [sidebar]
                        (prn "sidebar created")
                        (.setObject sidebar data "sometitle" (fn [a b c] (prn "object set" a b c))))))
