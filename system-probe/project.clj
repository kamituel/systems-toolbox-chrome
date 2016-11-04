(defproject kamituel/s-tlbx-probe "0.1.5"
  :description "Systems Toolbox Probe for use with Chrome DevTools extension."
  :url "https://github.com/kamituel/systems-toolbox-chrome"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [matthiasn/systems-toolbox "0.5.22"]
                 [com.cognitect/transit-cljs "0.8.239"]]
  :plugins [[lein-cljsbuild "1.1.1"]])
