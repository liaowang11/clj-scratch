(ns scratch
  (:require
   [clojure.repl :refer [apropos dir doc source]]
   [clojure.reflect :refer [reflect]]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.data :refer [diff]]
   [clojure.java.browse :refer [browse-url]]
   [clojure.pprint :refer [pp pprint print-table]]
   [clojure.set :as set]
   [clojure.spec.alpha :as spec]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.walk :refer [postwalk]]
   [rebel-readline.core]
   [rebel-readline.clojure.main]
   [rebel-readline.clojure.line-reader]
   [rebel-readline.clojure.service.local]
   [cider-nrepl.main :as cider]
   [com.rpl.specter :as specter]
   [aleph.http :as http]
   [aleph.tcp :as tcp]
   [aleph.udp :as udp]
   [byte-streams :as byte-streams]
   [manifold.stream :as stream]
   [manifold.deferred :as deferred]
   [java-time :as time]
   [clojure.java.jdbc :as jdbc]
   [buddy.core.hash :as hash]
   [buddy.core.mac :as mac]
   [buddy.core.codecs :as codecs]
   [buddy.core.codecs.base64 :as base64]
   [buddy.hashers :as hashers]
   [pl.danieljanus.tagsoup :as tagsoup]
   [hiccup.core :refer [html]]
   [postal.core :as postal]
   [scratch.system :refer [username home pwd os]]
   [scratch.java :refer [jmethods]]
   [scratch.json :as json]
   [scratch.net :refer [valid-port? valid-url? get-free-port hostname ping nslookup]]
   [scratch.fs :refer [ls dir? exists?]]
   [pyro.printer :as pyro]
   [expound.alpha :as expound]))

(def printer (expound/custom-printer {:print-specs? false
                                      :show-valid-values? true
                                      :theme :figwheel-theme}))

(defn err->msg
  "Helper to return an error message string from an exception."
  [^Throwable e]
  (-> e Throwable->map clojure.main/ex-triage clojure.main/ex-str))

(defn repl-caught [e]
  (let [ex (clojure.main/repl-exception e)
        tr (.getStackTrace ex)
        el (when-not (zero? (count tr)) (aget tr 0))
        ex-m (Throwable->map ex)]
    (binding [*out* *err*]
      (cond
        ;; If the output is a clojure spec issue...
        (::spec/problems (:data ex-m))
        ;; print expound output
        (do
          (println (str (re-find  #"Call to .* did not conform to spec\:" (.getMessage ex))
                        "\n"
                        (with-out-str (printer (:data ex-m))))))

        (instance? clojure.lang.LispReader$ReaderException e)
        (println (:cause (Throwable->map e)))

        :else
        ;; otherwise print exception
        (println (str (if (instance? clojure.lang.Compiler$CompilerException ex)
                        (err->msg ex)
                        (str " " (if el
                                   (clojure.stacktrace/print-stack-trace ex)
                                   "[trace missing]")))))))))

(defn -main []
  (in-ns 'scratch)
  (set! spec/*explain-out* printer)
  (st/instrument)
  (pyro/swap-stacktrace-engine!)
  (doto (Thread. #(cider/init)) (.setDaemon true) .start)
  (rebel-readline.core/with-line-reader
    (rebel-readline.clojure.line-reader/create
     (rebel-readline.clojure.service.local/create))
    (clojure.main/repl
     :prompt (fn []) ;; prompt is handled by line-reader
     :read (rebel-readline.clojure.main/create-repl-read)
     :print pprint
     :caught repl-caught)))
