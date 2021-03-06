(defproject wenxinbnb "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-cljsbuild "0.3.2"]
            [lein-ring "0.8.5"]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [ring-mock "0.1.5"]
                 [hiccup "1.0.3"]
                 [clj-time "0.6.0"]
                 [compojure "1.1.5"]
                 [me.raynes/laser "1.1.1"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [org.clojure/java.jdbc "0.3.0-alpha4"]
                 [org.clojure/clojure "1.5.1"]
                 [enfocus "2.0.0-beta1"]
                 [enlive "1.1.1"]]
  :source-paths ["src/clj"]
  :ring {:handler wenxinbnb.core/app
         :auto-relod? true}
  :main wenxinbnb.core
  :profiles {:uberjar {:aot [wenxinbnb.core]}}
  :cljsbuild{:builds [{:source-paths ["src/qr_cljs"]
                       :compiler {:output-to "resources/public/qr.js"
                                  :externs ["src/qr_cljs/r0_rich/externs.js"]
                                  :optimizations :advanced
                                  :pretty-print true}}
                      {:source-paths ["src/invoice_new_cljs"]
                       :compiler {:output-to "resources/public/invoice_new.js"
                                  :externs ["src/invoice_new_cljs/r0_rich/externs.js"]
                                  :optimizations :advanced
                                  :pretty-print true}
                       :jar true} 
                      {:source-paths ["src/cljs"],
                       :compiler {:output-to "resources/public/app.js",
                                  :optimizations :whitespace,
                                  :pretty-print true}
                       :jar true}]})
