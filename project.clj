(defproject ring-next "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-jetty-adapter "1.6.3"]]

  ;; lein-ring is setup in ~/.lein/profiles.clj
  ;; :plugins [lein-ring "0.12.4"]
  ;; lein ring server
  :ring {:handler ring-next.core/app}
  
  :main ring-next.core
  )

