(ns kamituel.s-tlbx-probe.setup
  "Helpers for host application to set up probe correctly."
  (:require [matthiasn.systems-toolbox.component :as comp]
            [matthiasn.systems-toolbox.switchboard :as sb]
            [clojure.walk :refer [postwalk]]
            [cljs.pprint :as pprint]))

(def cmp-opts
  "Set of sytems-toolbox component options that enable both messages, and state snapshots
  to be put onto the firehose."
  {:msgs-on-firehose      true
   :snapshots-on-firehose true})

(defn enable-firehose
  "Given component map, merges current :opts with cmp-opts that enable firehose support."
  [cmp-map]
  (update cmp-map :opts #(merge cmp-opts (or % {}))))

(defn with-firehose
  "If set to be enabled, enables msgs and state snapshots for each component map supplied."
  [enabled? cmp-map-xs]
  (set
   (map (fn [cmp-map]
          (if enabled?
            (enable-firehose cmp-map)
            cmp-map))
        cmp-map-xs)))
