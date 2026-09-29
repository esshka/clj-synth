(ns cljjev.validate
  "Shape checks on printed Clojure, and test runs in a separate bb or clojure process."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :refer [postwalk-replace]]
            [cljjev.tree :refer [branch? flat focus-text head open-text read-all]])
  (:import (java.io File)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (java.util.concurrent TimeUnit)))

(def ^:private test-mark "@@cljjev ")

(defn- check
  "Returns the shape errors in node and its children."
  [node]
  (let [h (head node)
        items (if (branch? node) (vec node) [])
        b (second items)]
    (concat
     (when (and (= h "defn")
                (not (and (>= (count items) 4) (symbol? (nth items 1)) (vector? (nth items 2)))))
       [(str "defn needs a name and a params vector: " (flat node))])
     (when (and (= h "fn") (not (and (>= (count items) 3) (vector? (nth items 1)))))
       [(str "fn needs a params vector: " (flat node))])
     (when (and (#{"let" "loop" "if-let"} h)
                (not (and (vector? b)
                          (even? (count b))
                          (or (not= h "if-let") (= 2 (count b)))
                          (every? symbol? (take-nth 2 b)))))
       [(str h " needs a [name expr ...] vector: " (flat node))])
     (when (and (= h "cond") (even? (count items)))
       [(str "cond needs test/value pairs: " (flat node))])
     (when (and (symbol? node) (#{focus-text open-text} (str node)))
       ["open hole left in program"])
     (mapcat check items))))

(defn structure
  "Returns the reader error, else the shape errors, of source. Empty means well formed."
  [source]
  (try
    (vec (mapcat check (read-all source)))
    (catch Exception e
      [(or (ex-message e) (str e))])))

(defn- which [tool]
  (some #(let [f (io/file % tool)]
           (when (and (.isFile f) (.canExecute f)) (.getPath f)))
        (str/split (or (System/getenv "PATH") "") (re-pattern File/pathSeparator))))

(defn runtime
  "Returns the command that runs a Clojure script, bb preferred, or nil if neither
  bb nor clojure is on PATH."
  []
  (some (fn [[tool args]] (when-let [path (which tool)] (into [path] args)))
        [["bb" []] ["clojure" ["-M"]]]))

(defn run-script
  "Runs script as a file through command. Returns {:out :err}, or nil if it takes more
  than timeout seconds."
  [command script timeout]
  (let [dir (.toFile (Files/createTempDirectory "cljjev" (make-array FileAttribute 0)))
        path (io/file dir "gen.clj")
        out (io/file dir "out")
        err (io/file dir "err")]
    (try
      (spit path script)
      (let [p (-> (ProcessBuilder. ^java.util.List (conj command (.getPath path)))
                  (.redirectOutput out)
                  (.redirectError err)
                  .start)]
        (if (.waitFor p timeout TimeUnit/SECONDS)
          {:out (slurp out) :err (slurp err)}
          (do (.destroyForcibly p)
              (.waitFor p)
              nil)))
      (finally
        (run! #(.delete ^File %) [path out err dir])))))

(def ^:private probe-template
  '(println (str MARK (try BODY
                           (catch Throwable e
                             (str "ERROR " (or (ex-message e) (.getName (class e)))))))))

(defn- calls? [x]
  (boolean (some seq? (tree-seq branch? seq x))))

(defn- probe
  "Returns a form that prints one line for test; an (= want got) test also reports
  both values."
  [test]
  (let [form (first (read-all test))
        body (if (and (= (head form) "=") (= 3 (count form)))
               (let [[want got] (rest form)
                     ;; the side that calls something is the one computed
                     [want got] (if (and (calls? want) (not (calls? got))) [got want] [want got])]
                 (list 'let ['want want 'got got]
                       '(if (= want got)
                          "PASS"
                          (str "FAIL expected " (pr-str want) ", got " (pr-str got)))))
               (list 'if form "PASS" "FAIL"))]
    (postwalk-replace {'MARK test-mark 'BODY body} probe-template)))

(defn- lines [s]
  (let [s (str/trim s)]
    (if (str/blank? s) [] (str/split-lines s))))

(defn- failed [tests detail]
  (mapv #(hash-map :test % :ok false :detail detail) tests))

(defn run-tests
  "Runs source followed by tests. Returns [{:test :ok :detail}], one per test, or nil
  if no Clojure runtime is installed. bb starts in about 0.2 s, so the 4 s default
  timeout means a loop."
  ([source tests] (run-tests source tests (runtime) 4))
  ([source tests command timeout]
   (when command
     (let [script (str source "\n\n" (str/join "\n" (map (comp pr-str probe) tests)) "\n")
           done (run-script command script timeout)
           found (when done
                   (for [ln (str/split-lines (:out done))
                         :when (str/starts-with? ln test-mark)]
                     (subs ln (count test-mark))))]
       (cond
         (nil? done)
         (failed tests (str "ERROR timeout after " timeout "s"))

         (not= (count found) (count tests))
         (let [errs (lines (:err done))
               err (or (first (filter #(str/starts-with? % "Message:") errs))
                       (first errs)
                       "no output")]
           (failed tests (str "ERROR " (subs err 0 (min 300 (count err))))))

         :else
         (mapv #(hash-map :test %1 :ok (= %2 "PASS") :detail %2) tests found))))))
