(defproject kamituel/systems-toolbox-chrome "0.1.1"
  :description "Chrome DevTools support for systems-toolbox library"
  :url "https://github.com/kamituel/systems-toolbox-chrome"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.189"]
                 [matthiasn/systems-toolbox "0.3.15"]
                 [kamituel/s-tlbx-probe "0.1.1"]
                 [alandipert/storage-atom "1.2.4" :exclusions [org.clojure/clojure org.clojure/clojurescript]]]
  :plugins [[lein-cljsbuild "1.1.1"]]
  :clean-targets ^{:protect false} ["resources/devtools/js/"]
  :cljsbuild {:builds {:devtools {:source-paths ["src/devtools/cljs"]
                                  :compiler {:asset-path "js"
                                             :optimizations :simple
                                             :output-dir "resources/devtools/js"
                                             :output-to "resources/devtools/js/all.js"
                                             :source-map "resources/devtools/js/all.map"}}
                       #_:prod #_{:source-paths ["src/devtools/cljs"]
                              :compiler {:asset-path "js"
                                         :optimizations :advanced
                                         :pretty-print false
                                         :output-dir "resources/devtools/js"
                                         :output-to "resources/devtools/js/all.js"
                                         :externs ["externs/chrome.js"]}}}})
