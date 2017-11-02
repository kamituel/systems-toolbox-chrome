(ns kamituel.s-tlbx-chrome.utils)

(defn ts->time
  "Converts timestamp to HH:mm:ss format."
  [ts]
  (let [date (js/Date. ts)
        h (.getHours date)
        m (.getMinutes date)
        s (.getSeconds date)]
    (str (if (< h 10) "0") h ":"
         (if (< m 10) "0") m ":"
         (if (< s 10) "0") s)))

(defn msg-printable
  [msg]
  (if-not msg
    nil
    (-> msg
      (update-in [:src-cmp] (partial str))
      (update-in [:dst-cmp] (partial str))
      (update-in [:command] (partial str))
      (assoc :ts-time (ts->time (:ts msg))))))

(defn msg-eq
  [msg1 msg2]
  (= (:corr-id msg1) (:corr-id msg2)))

(defn data->hiccup
  "Converts an arbitrary EDN data structure to the HTML where each element (i.e. map, vector,
  sequence, number, string) are wrapped in DOM elements such as DIV's or SPAN's so they are
  easy to style using CSS."
  ([data expanded-path on-expand-fn]
   (data->hiccup data expanded-path on-expand-fn []))
  ([data expanded-path on-expand-fn current-path]
    (let [key-to-expand (first expanded-path)
          handle-coll (fn [v expand-key]
                        (if (or (not (coll? v)) (= key-to-expand expand-key))
                          [:div (data->hiccup v (rest expanded-path) on-expand-fn (conj current-path expand-key))]
                          [:div.collapsed
                           {:on-click (on-expand-fn (conj current-path expand-key))}
                           (data->hiccup (empty v) expanded-path on-expand-fn [])]))]
      (cond
        (map? data)
        [:div.map (for [[k v] (sort data)]
                    ^{:key (hash (conj current-path k))}
                    [:div.key-val
                     [:div (data->hiccup k expanded-path on-expand-fn (conj current-path k))]
                     (handle-coll v k)])]

        (vector? data)
        [:div.vector (for [[idx v]
                           (map-indexed (fn [idx v] [idx v]) data)]
                       ^{:key (hash (conj current-path idx))}
                       (handle-coll v idx))]

        (seq? data)
        [:div.seq (for [[idx v] (map-indexed (fn [idx v] [idx v]) data)]
                    ^{:key (hash (conj current-path idx))}
                    (handle-coll v idx))]

        (string? data)
        [:span.string data]

        (number? data)
        [:span.number data]

        (keyword? data)
        [:span.keyword (str data)]

        (nil? data)
        [:span.nil "nil"]

        (or (true? data) (false? data))
        [:span.boolean (str data)]

        :else
        (str data)))))

(defn on-key-press
  [key-name f]
  (let [keymap {40 :arrow-down 38 :arrow-up}]
    (.addEventListener
      js/window "keydown"
      (fn [evt]
        (if (= key-name (get keymap (or (.-which evt) (.-keyCode evt))))
          (.preventDefault evt)
          (f))))))

(defn cbk
  [f]
  (fn [evt]
    (.stopPropagation evt)
    (f)
    true))
