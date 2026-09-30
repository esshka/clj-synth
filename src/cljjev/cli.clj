(ns cljjev.cli
  "Command line: clojure -M:run [--mock] [--trace] [--test FORM]... SPEC"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [cljjev.generate :as g]
            [cljjev.jev :as jev]
            [cljjev.policy :refer [mock-policy]]))

(defn- dotenv
  "Returns the KEY=VALUE lines of the file at path as a map, {} if there is no file."
  ([] (dotenv ".env"))
  ([path]
   (let [f (io/file path)]
     (if-not (.isFile f)
       {}
       (into {} (for [ln (str/split-lines (slurp f))
                      :when (and (str/includes? ln "=")
                                 (not (str/starts-with? (str/triml ln) "#")))
                      :let [[k v] (str/split ln #"=" 2)]]
                  [(str/trim k) (-> v str/trim (str/replace #"^['\"]+|['\"]+$" ""))]))))))

(defn api-key
  "Returns TYPESAFE_API_KEY or JEV_API_KEY: from the environment, else from ./.env."
  []
  (let [env (into (dotenv) (System/getenv))]
    (some not-empty [(env "TYPESAFE_API_KEY") (env "JEV_API_KEY")])))

(def ^:private cli-options
  (let [limit (fn [flag doc k]
                [nil flag doc :default (get g/default-limits k) :parse-fn parse-long])]
    [[nil "--test FORM" "extra test form, e.g. '(= 120 (factorial 5))'"
      :multi true :default [] :update-fn conj]
     [nil "--mock" "use the local mock policy, not the API"]
     [nil "--trace" "print every Choice to stderr"]
     [nil "--pick" "let Jev pick each repair among concrete changes (experimental)"]
     [nil "--model MODEL" "Jev model" :default "jev-latest"]
     (limit "--max-depth N" "deepest nesting for compound forms" :max-depth)
     (limit "--max-steps N" "decisions per program" :max-steps)
     (limit "--tries N" "programs to build and test" :tries)
     [nil "--out FILE" "also write the program to this file"]
     ["-h" "--help"]]))

(def ^:private usage-error
  "cljjev: expected one SPEC argument; (= ...) forms inside it become tests")

(defn- err [& xs]
  (binding [*out* *err*]
    (apply println xs)))

(defn- log-decision [d]
  (err (format "  %-18s → %-16s p=%.2f of %d" (get-in d [:hole :hint]) (:choice d)
               (double (get (:probs d) (:choice d) 1.0)) (max 1 (count (:probs d))))))

(defn- log-attempt [a]
  (err (str "-- program " (if (:edit a) (str "edit " (:edit a)) "#1") ": "
            (if (:results a) (str (g/score a) "/" (count (:results a)) " tests") "no tests"))))

(defn- policy [options]
  (let [key (when-not (:mock options) (api-key))]
    (cond
      key (jev/client key :model (:model options))
      (:mock options) (mock-policy)
      :else (do (err "no TYPESAFE_API_KEY / JEV_API_KEY: using the mock policy")
                (mock-policy)))))

(defn- report
  "Prints the best program to stdout and its test results to stderr. Returns the exit
  code."
  [outcome out-file]
  (let [best (g/best outcome)]
    (println (g/source best))
    (when out-file
      (spit out-file (g/source best)))
    (if (nil? (:results best))
      (do (err "tests: none run (no tests, or no bb/clojure on PATH)")
          0)
      (do (doseq [r (:results best)]
            (err (str (if (:ok r) "PASS" "FAIL") "  " (:test r) "  " (if (:ok r) "" (:detail r)))))
          (err (str "programs: " (count (:attempts outcome)) ", API calls: " (:calls outcome)))
          (if (g/passed? outcome) 0 1)))))

(defn run
  "Runs the command line args. Returns the exit code: 0 pass (or no tests run), 1 tests
  fail, 2 error."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      (do (println "usage: cljjev [options] SPEC\n" summary)
          0)

      (or errors (not= 1 (count arguments)))
      (do (err (str/join "\n" (or errors [usage-error])))
          2)

      :else
      (try
        (report (g/solve (first arguments) (policy options)
                         :tests (:test options)
                         :limits (merge g/default-limits
                                        (select-keys options [:max-depth :max-steps :tries]))
                         :log (if (:trace options) log-decision (fn [_]))
                         :log-attempt log-attempt
                         :flow (if (:pick options) :pick :score))
                (:out options))
        (catch clojure.lang.ExceptionInfo e
          (if (or (g/generation-error? e) (jev/jev-error? e))
            (do (err (str "cljjev: " (ex-message e)))
                2)
            (throw e)))))))

(defn -main [& args]
  (System/exit (run args)))
