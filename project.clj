(defproject kamituel/systems-toolbox-chrome "0.1.0-SNAPSHOT"
  :description "Chrome DevTools support for systems-toolbox library"
  :url "https://github.com/kamituel/systems-toolbox-chrome"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [matthiasn/systems-toolbox "0.2.19-SNAPSHOT"]
                 [org.clojure/clojurescript "0.0-3308"]]
  :plugins [[lein-cljsbuild "1.0.6"]]
  :clean-targets ^{:protect false} ["resources/devtools/js/"]
  :cljsbuild {:builds {:devtools {:source-paths ["src/devtools/cljs"]
                                  :compiler {:asset-path "js"
                                             :optimizations :simple
                                             :output-dir "resources/devtools/js"
                                             :output-to "resources/devtools/js/all.js"
                                             :source-map "resources/devtools/js/all.map"}}}})
