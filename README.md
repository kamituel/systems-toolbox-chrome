# systems-toolbox-chrome
Chrome DevTools support for [systems-toolbox library](https://github.com/matthiasn/systems-toolbox).

**This is an early preview, submit bugs found in this repository.**

## Installation

**Step 1:**
Clone this repository and install it locally:

```shell
git clone git@github.com:kamituel/systems-toolbox-chrome.git
cd systems-toolbox-chrome/system-probe
lein install
```

**Step 2:**
Add a probe dependency:

[![Clojars Project](http://clojars.org/kamituel/s-tlbx-probe/latest-version.svg)](http://clojars.org/kamituel/s-tlbx-probe)

Then, attach the probe to the switchboard you wish to monitor:

```clojure
(ns example
  (:require [matthiasn.systems-toolbox.switchboard :as sb]
            [kamituel.s-tlbx-probe.probe :as devtools-probe]))

(defonce switchboard (sb/component :frntnd/switchbrd))
(devtools-probe/init switchboard)
```

**Step 3:**

Make sure messages and snapshots for each of your components are being put onto the firehose.

You can do that manually for each component by setting the following options:

```clojure
{:cmp-id     :some/component
 :state-fn   (fn [] ...)
 :opts       {:msgs-on-firehose      true
              :snapshots-on-firehose true}}
```

Alternatively, you can use helpers provided with the probe that help out with that:

```clojure
(ns example
  (require [kamituel.s-tlbx-probe.setup :as probe-setup]))

(probe-setup/enable-firehose
  {:cmp-id     :some/component
   :state-fn   (fn [] ...)})

```

If you have many components, or you want a straigtforward way of enabling/disabling firehose / probe in development and production environmnets, you can also use a batch `with-firehose` that works in conjunction with systems-toolbox's `:cmd/init-cmp`:

```clojure
(ns example
  (require [kamituel.s-tlbx-probe.setup :as probe-setup]))

(sb/send-mult-cmd switchboard
  [[:cmd/init-comp
    (probe-setup/with-firehose true ; if false, firehose will not be enabled.
      [cmp-map-1 cmp-map-2 cmp-map-3])]])

;; Above it equivalent to:
(sb/send-mult-cmd switchboard
  [[:cmd/init-comp
    [(if true (probe-setup/enable-firehose cmp-map-1) cmp-map-1)
     (if true (probe-setup/enable-firehose cmp-map-2) cmp-map-2)
     (if true (probe-setup/enable-firehose cmp-map-3) cmp-map-3)]]])
```

**Step 4:**
Install the Chrome DevTools extension. First of all, it needs to be compiled from inside the main repository directory:

```shell
lein cljsbuild once
```

Then, in Chrome, go to "settings" (top right corner icon) ->
"Extensions" and then click the "Load unpacked extension" button. In the file picker dialog that pops up,
point to the local clone of this repo ("systems-toolbox-chrome").

**Step 4:**
Open your systems application and then open Chrome Developer Tools. You'll see "Systems Toolbox" tab there.

## Known bugs

- When you pass around result of transducer/sequence operation, it might break systems probe.
  It's a bug in Transit, see **[this issue](https://github.com/cognitect/transit-cljs/issues/30)**.
  Workaround is:

  ```clojure
  (apply list (sequence ...))
  ```

  or:

  ```clojure
  (vec (sequence ...))
  ```
