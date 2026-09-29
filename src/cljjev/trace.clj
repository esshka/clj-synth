(ns cljjev.trace
  "Runs a program with spies around chosen subtrees, and reports what each one evaluated
  to on one test."
  (:require [clojure.string :as str]
            [clojure.walk :refer [postwalk-replace]]
            [cljjev.tree :refer [branch? head read-all with-items]]
            [cljjev.validate :refer [run-script runtime]]))

(def ^:private spy-mark "@@spy")

(def ^:private prelude
  (postwalk-replace
   {'KEEP 4} ; values kept per subtree
   '[(def cljjev-trace (atom {}))
     (def cljjev-steps (atom 0))
     (defn cljjev-spy [k v]
       (when (> (swap! cljjev-steps inc) 100000) (throw (ex-info "trace limit" {})))
       (swap! cljjev-trace (fn [t] (if (< (count (get t k)) KEEP) (update t k (fnil conj []) v) t)))
       v)]))

(def ^:private report
  (postwalk-replace
   {'MARK spy-mark 'WIDTH 60} ; characters kept per value
   '(doseq [[k vs] @cljjev-trace v vs]
      (println MARK k (let [s (pr-str v)] (subs s 0 (min WIDTH (count s))))))))

(defn has-recur?
  "Returns true if node is, or holds, a recur form."
  [node]
  (or (= (head node) "recur") (and (branch? node) (boolean (some has-recur? node)))))

(defn instrument
  "Returns node with the subtree at each path in spots (a map of path to key) wrapped
  as (cljjev-spy key subtree)."
  ([node spots] (instrument node spots []))
  ([node spots path]
   (let [node (if (branch? node)
                (with-items node (map-indexed (fn [i c] (instrument c spots (conj path i))) node))
                node)]
     (if-let [k (get spots path)]
       (list 'cljjev-spy k node)
       node))))

(defn trace-values
  "Runs test against code, with its spies. Returns {key [value-text ...]} for the spies
  that ran; {} when the run times out."
  ([code test] (trace-values code test (runtime) 4))
  ([code test command timeout]
   (let [forms (concat ['(ns generated.core)] prelude (read-all code)
                       [(list 'try (first (read-all test)) '(catch Throwable e nil)) report])
         done (when command (run-script command (str/join "\n" (map pr-str forms)) timeout))
         prefix (str spy-mark " ")]
     (reduce (fn [acc line]
               (let [[k v] (str/split (subs line (count prefix)) #" " 2)]
                 (update acc k (fnil conj []) v)))
             {}
             (filter #(str/starts-with? % prefix) (some-> done :out str/split-lines))))))
