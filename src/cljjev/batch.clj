(ns cljjev.batch
  "Runs many programs on the same tests in a few bb processes. Each program gets its own
  namespace, a step budget and a time limit per test, so a runaway costs its own
  verdicts, not the batch."
  (:require [clojure.string :as str]
            [clojure.walk :refer [postwalk-replace]]
            [cljjev.tree :refer [branch? head with-items]]
            [cljjev.validate :refer [run-script runtime verdict]]))

(def ^:private mark "@@batch")
;; ponytail: fn calls and loop turns per test; (fib 10) takes 177, (fib 20) 21891 would fail
;; here though run-tests passes it; raise this if specs grow
(def ^:private fuel 3000)
(def ^:private test-ms 300)
(def ^:private processes 8)
;; programs per process, so a small batch does not pay for eight start-ups
(def ^:private min-chunk 50)
;; ponytail: a timed-out process loses its whole chunk; rare, since fuel and test-ms stop runaways
(def ^:private process-timeout 60)

(def ^:private prelude
  (postwalk-replace
   {'MARK mark 'FUEL fuel 'MS test-ms}
   '[(ns cljjev.rt)
     (def ^:dynamic *fuel* nil)
     (defn tick [] (when (> (vswap! *fuel* inc) FUEL) (throw (ex-info "step limit" {}))))
     (defn- err [e] (str "ERROR " (or (ex-message e) (.getName (class e)))))
     ;; eval, not top-level forms: a program that fails to compile must not end the script
     (defn check [k forms tests]
       (let [ns *ns*
             loaded (try (binding [*ns* ns] (run! eval forms)) nil (catch Throwable e (err e)))]
         ;; once one test runs away the rest would too; they get its verdict unrun
         (reduce (fn [stop [i t]]
                   (let [v (or stop
                               (deref (future (binding [*fuel* (volatile! 0) *ns* ns]
                                                (try (eval t) (catch Throwable e (err e)))))
                                      MS "ERROR timeout"))]
                     (println MARK k i v)
                     (or stop (#{"ERROR timeout" "ERROR step limit"} v))))
                 loaded
                 (map-indexed vector tests))))]))

(defn- fueled
  "Returns node with a tick before every defn, fn and loop body: every call and every
  recur spends one step."
  [node]
  (if (branch? node)
    (let [node (with-items node (map fueled node))]
      (if (#{"defn" "fn" "loop"} (head node))
        (with-items node (concat (butlast node) [(list 'do '(cljjev.rt/tick) (last node))]))
        node))
    node))

(defn- script [chunk verdicts]
  ;; print-meta keeps ^:dynamic on *fuel*
  (str/join "\n" (concat (binding [*print-meta* true] (mapv pr-str prelude))
                         (for [[k tree] chunk]
                           (str (pr-str (list 'ns (symbol (str "c" k)))) "\n"
                                (pr-str (list 'cljjev.rt/check k
                                              (list 'quote [(fueled tree)])
                                              (list 'quote verdicts)))))
                         ["(System/exit 0)"])))

(defn- run-chunk
  "Returns {[k i] verdict} for the programs of chunk, [k tree] pairs."
  [command chunk verdicts]
  (let [done (run-script command (script chunk verdicts) process-timeout)]
    (reduce (fn [acc line]
              (let [[_ k i v] (str/split line #" " 4)
                    at [(parse-long k) (parse-long i)]]
                (cond-> acc (not (contains? acc at)) (assoc at v))))
            {}
            (filter #(str/starts-with? % (str mark " ")) (some-> done :out str/split-lines)))))

(defn run-batch
  "Runs every tree against tests. Returns one [{:test :ok :detail}] per tree, in order, or
  nil if no Clojure runtime is installed."
  ([trees tests] (run-batch trees tests (runtime)))
  ([trees tests command]
   (when command
     (let [trees (vec trees)
           size (max min-chunk (long (Math/ceil (/ (count trees) processes))))
           verdicts (mapv verdict tests)
           found (apply merge {} (map deref (mapv #(future (run-chunk command % verdicts))
                                                  (partition-all size (map-indexed vector trees)))))]
       (vec (for [k (range (count trees))]
              (vec (for [[i t] (map-indexed vector tests)
                         :let [v (get found [k i] "ERROR timeout")]]
                     {:test t :ok (= v "PASS") :detail v}))))))))
