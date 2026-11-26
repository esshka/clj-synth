(ns cljjev.report
  "BENCHMARKS.md: tables of the saved benchmark runs. Every number is derived from
  bench/results/, so the file is written again after each run and never edited by hand."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cljjev.env :refer [extract]]))

(def ^:private set-order ["split" "whole" "basic" "hard" "held"])

(defn- status [r]
  (str (cond (:checked r) "PASS" (:passed r) "OVER" :else "FAIL")
       (when (= 2 (:attempts r)) " in 2 searches")))

(defn- fname [r]
  (first (:names (extract (:spec r) []))))

(defn- code [s]
  (str "`" s "`"))

(defn- table [header rows]
  (str/join "\n" (concat [(str "| " (str/join " | " header) " |")
                          (str "|" (apply str (repeat (count header) " --- |")))]
                         (for [row rows] (str "| " (str/join " | " row) " |")))))

(defn- over-runs
  "Returns f of the results one problem got in each run of file, joined."
  [file i f]
  (str/join ", " (for [run (:runs file)] (f (nth run i)))))

(defn- problems
  "One row per function of file's set, a part before its main, with every run's result."
  [file]
  (table ["Function" "Result" "API calls" "Program of the last run"]
         (for [[i main] (map-indexed vector (peek (:runs file)))
               [k r] (map-indexed vector (conj (vec (:parts main)) main))
               :let [part? (< k (count (:parts main)))
                     pick (if part? #(nth (:parts %) k) identity)]]
           [(str (code (fname r)) (when part? " (part)"))
            (over-runs file i (comp status pick))
            ;; a main's calls hold its parts' calls too
            (over-runs file i (comp str :calls pick))
            (code (:program r))])))

(defn- split-against-whole
  "The same main functions solved after their parts and alone."
  [split whole]
  (table ["Function" "Parts" "After its parts" "API calls" "Alone" "API calls"]
         (for [[i main] (map-indexed vector (peek (:runs split)))]
           [(code (fname main))
            (str/join ", " (map (comp code fname) (:parts main)))
            (over-runs split i status)
            (over-runs split i (comp str :calls))
            (if whole (over-runs whole i status) "not run")
            (if whole (over-runs whole i (comp str :calls)) "")])))

(defn- title [file]
  (str "`" (:set file) "`, " (str/replace (subs (:date file) 0 16) "T" " ") ", flow " (:flow file)))

(defn render
  "Returns the text of BENCHMARKS.md for files, the saved runs, oldest first."
  [files]
  (let [latest (into {} (for [f files] [(:set f) f]))
        sets (concat (filter latest set-order) (sort (remove (set set-order) (keys latest))))]
    (str/join
     "\n\n"
     (concat
      ["# Benchmarks"
       (str "Written by `clojure -M:bench report`, and after every benchmark run, from the files in "
            "`bench/results/`. Do not edit it by hand.")
       (str "PASS: the program passes the spec's tests and hidden checks the search never sees. "
            "OVER: it passes only the spec's tests. FAIL: it fails a test of the spec. "
            "Model answers change between runs, so one run is a sample, not a score. "
            "A set named `<set>-flat` ran with no model: every option had the same probability, "
            "and its API calls are questions that code answered.")]
      (when-let [split (latest "split")]
        ["## Hard functions: after their parts and alone"
         (str "Each main function is solved after its parts, which are then options for it ("
              (title split) "), and alone (" (if-let [w (latest "whole")] (title w) "not run")
              "). The API calls of a main function include the calls of its parts.")
         (split-against-whole split (latest "whole"))])
      ["## Latest run of each set"]
      (mapcat (fn [s] [(str "### " (title (latest s))) (problems (latest s))]) sets)
      ["## All runs"
       (table ["Date" "Set" "Flow" "PASS" "OVER" "FAIL" "API calls"]
              (for [f (reverse files)
                    run (:runs f)]
                [(str/replace (subs (:date f) 0 16) "T" " ") (code (:set f)) (str (:flow f))
                 (count (filter :checked run))
                 (count (remove :checked (filter :passed run)))
                 (count (remove :passed run))
                 (reduce + (map :calls run))]))
       ""]))))

(defn write!
  "Writes BENCHMARKS.md from the live runs in dir; a mock run is not a result."
  ([] (write! "bench/results" "BENCHMARKS.md"))
  ([dir out]
   (let [files (->> (.listFiles (io/file dir))
                    (filter #(str/ends-with? (.getName ^java.io.File %) ".edn"))
                    (sort-by #(.getName ^java.io.File %))
                    (map (comp edn/read-string slurp))
                    (remove :mock))]
     (spit out (render files))
     (println "wrote" out))))
