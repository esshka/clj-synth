(ns cljjev.library
  "Divide and conquer across functions: a solved function is an option for the next specs,
  so a hard function is a few choices over its solved parts."
  (:require [clojure.string :as str]
            [cljjev.batch :refer [run-batch]]
            [cljjev.env :refer [extract]]
            [cljjev.generate :as g]
            [cljjev.trace :refer [trace-values]]
            [cljjev.tree :refer [read-all]]
            [cljjev.validate :refer [run-tests]]))

(defn entry
  "Returns the library entry for attempt, a program that passes spec: what catalog offers
  (:name :params :kinds :returns :doc) and what the runners load (:tree :source)."
  [spec attempt]
  (let [pools (extract spec [])
        [_ fname params :as tree] (first (read-all (:code attempt)))
        name (str fname)]
    {:name name
     :params (mapv str params)
     :kinds (or (get-in pools [:param-kinds name]) (mapv (constantly "any") params))
     :returns (get-in pools [:returns name] "any")
     :doc (str "(" name " " (str/join " " params) ") " (:prose pools))
     :tree tree
     :source (g/source attempt)}))

(defn solve
  "g/solve with the functions of library on offer. Every runner loads them before the
  program, so the options are those of g/solve."
  [spec policy library & {:keys [run batch trace]
                          :or {run run-tests batch run-batch trace trace-values}
                          :as options}]
  (let [sources (apply str (map :source library))
        trees (map :tree library)]
    (g/solve spec policy
             (assoc options
                    :library library
                    :run (fn [source tests] (run (str sources source) tests))
                    :batch (when batch
                             (fn [programs tests]
                               (batch (map #(apply list 'do (concat trees [%])) programs) tests)))
                    :trace (fn [code test] (trace (str sources code) test))))))
