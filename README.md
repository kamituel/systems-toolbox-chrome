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
Install the Chrome DevTools extension. First of all, it needs to be compiled from inside the main repository directory:

```shell
lein cljsbuild once
```

Then, in Chrome, go to "settings" (top right corner icon) ->
"Extensions" and then click the "Load unpacked extension" button. In the file picker dialog that pops up,
point to the local clone of this repo ("systems-toolbox-chrome").

**Step 4:**
Open your systems application and then open Chrome Developer Tools. You'll see "Systems Toolbox" tab there.
