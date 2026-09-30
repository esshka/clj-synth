(ns cljjev.generate
  "Program search. Jev's Choice distributions and Score fits rank the edits; tests decide.

  A decision is {:path :hole :choice :probs}, probs mapping every offered option to its
  probability. An attempt is {:code :decisions :results :edit :depth}; results is nil
  when there are no tests or no Clojure runtime."
  (:require [clojure.string :as str]
            [cljjev.catalog :refer [arg-holes core-returns docs fits? legal ordered]]
            [cljjev.env :refer [context extract]]
            [cljjev.trace :refer [has-recur? instrument trace-values]]
            [cljjev.tree :refer [->hole flat head holes node-at put-at render]]
            [cljjev.validate :refer [run-tests structure]])
  (:import (clojure.lang ExceptionInfo)
           (java.util.concurrent ExecutionException)))

(def ^:private prod-question
  (str "Which production fills the hole marked __HOLE__ in `partial` (see `hole`), "
       "so the finished program implements `spec` and passes `tests`?"))

(def ^:private retry-note
  (str " `previous_attempt` is the program this one revises, with its `test_results`; "
       "`previous_attempt.edited` is the code being changed and what it computed on each "
       "test, and `previous_attempt.earlier`, when present, lists other programs that failed."))

(def ^:private fit-question
  (str "How correct is `subtree` in its place (`role`) inside `program`, "
       "given `spec` and `test_results`? "
       "`values`, when present, lists what `subtree` evaluated to during each test."))

(def ^:private act-question
  (str "What should happen to `subtree` (in its place `role` in `program`) "
       "so the program passes every test in `test_results`? "
       "`values`, when present, lists what `subtree` evaluated to during each test."))

(def ^:private fit-levels
  ["Wrong: this code causes the failing tests and must change"
   "Doubtful: probably wrong or incomplete for its role"
   "Plausible: could be right for its role"
   "Correct: exactly what the spec needs here"])

;; ponytail: no documented per-request question limit; batching keeps requests small
(def ^:private fit-batch 16)
;; below this the "Wrong" level dominates
(def ^:private wrong-fit 0.5)
;; a node scores low just for holding a wrong child; its own choice is less suspect
(def ^:private holds-wrong 0.3)
(def ^:private swap-alt "swap")
(def ^:private swap-prior 0.3)
(def ^:private swappable #{"-" "/" "<" ">" "<=" ">=" "cons" "conj" "mod" "quot"})
(def ^:private wrap-prefix "wrap:")
;; keep a half-right subtree inside a new form
(def ^:private wrap-ops ["+" "-" "*" "and" "or" "not"])
;; the lowest-fit subtrees get one more question, all in one call: keep, replace, or wrap?
(def ^:private wrap-ask 6)
;; pairs are drawn from this many best single edits
(def ^:private pair-top 4)
;; per edit away from the first program, so one bad branch cannot starve the rest
(def ^:private depth-decay 0.5)
;; ponytail: more failed programs mostly distract Jev; raise only if a benchmark says so
(def ^:private earlier-shown 4)

(def default-limits
  {:max-depth 8 ; 6 left (+ (* 3 x) 1) unreachable inside collatz's loop/recur/if
   :max-steps 48
   :tries 12 ; programs built and tested
   :alternatives 3 ; per decision, offered to the search
   :min-p 0.01}) ; alternatives Jev rates below this are never tried

;;; Attempts and outcomes

(defn source
  "Returns the attempt's program as a file."
  [attempt]
  (str "(ns generated.core)\n\n" (:code attempt) "\n"))

(defn score
  "Returns the number of tests the attempt passed."
  [attempt]
  (count (filter :ok (:results attempt))))

(defn- first-max [f xs]
  (reduce #(if (> (f %2) (f %1)) %2 %1) xs))

(defn best
  "Returns the attempt of outcome that passed the most tests, the earliest on a tie."
  [outcome]
  (first-max score (:attempts outcome)))

(defn passed?
  "Returns true if the best attempt of outcome ran tests and passed all of them."
  [outcome]
  (let [results (:results (best outcome))]
    (and (some? results) (every? :ok results))))

(defn- fail [msg]
  (throw (ex-info msg {:type ::generation})))

(defn generation-error?
  "Returns true if e was thrown because no program could be built."
  [e]
  (= ::generation (:type (ex-data e))))

(defn asker
  "Returns policy with every real call counted in the calls atom. An identical
  request is answered from memory."
  [policy calls]
  (memoize (fn [state questions]
             (swap! calls inc)
             (policy state questions))))

(defn- test-results [results]
  (mapv (fn [r] {:test (:test r) :result (:detail r)}) results))

(defn- within? [path root]
  (and (>= (count path) (count root))
       (= (subvec path 0 (count root)) root)))

;;; Build

(defn- meanings
  "Returns what the enclosing forms mean, innermost first: `fn params` inside reduce
  needs reduce's contract."
  [tree path env]
  (take 3 (for [k (range (dec (count path)) -1 -1)
                :let [node (node-at tree (subvec path 0 k))
                      name (head node)
                      meaning (or (docs [name (dec (count node))])
                                  (docs name)
                                  (when (contains? (:functions env) name)
                                    (str "(" name " ...) a call to `" name "` itself")))]
                :when meaning]
            meaning)))

(defn- state-of [pools tree path hole env feedback]
  (let [inside (meanings tree path env)]
    (cond-> {:spec (:prose pools)
             :tests (:tests pools)
             :partial (render tree path)
             :env {:locals (:locals env)
                   :functions (vec (for [[n a] (:functions env)] (str n "/" a)))}
             :hole {:hint (:hint hole)
                    :expected (:kind hole)
                    :tail (:tail env)
                    :depth (:depth env)
                    :form (if (seq path)
                            (flat (node-at tree (pop path)) [(peek path)])
                            (flat hole []))}}
      (seq inside) (assoc-in [:hole :inside] (vec inside))
      feedback (assoc :previous_attempt feedback))))

(defn- decide [ask state options path hole]
  (if (= 1 (count options))
    {:path path :hole hole :choice (key (first options)) :probs {}}
    (let [criteria (ordered (for [[k [text _]] options] [k text]))
          question {:type "choice"
                    :instructions (cond-> prod-question
                                    (:previous_attempt state) (str retry-note))
                    :criteria criteria}
          answer (get (ask state {"prod" question}) "prod")
          choice (get answer "choice")]
      (when-not (contains? criteria choice)
        (fail (str "answer " (pr-str choice) " is not one of the offered options")))
      {:path path :hole hole :choice choice :probs (get answer "probabilities")})))

(defn build
  "Fills holes from the root. A decision in plan (a map of path to decision) is replayed
  while it is still legal; otherwise Jev is asked, and log sees the new decision.
  Returns [tree decisions]."
  [ask pools plan limits log feedback]
  (loop [tree (->hole "top" "top")
         made []]
    (if-let [[path hole] (first (holes tree))]
      (do
        (when (>= (count made) (:max-steps limits))
          (fail (str "max_steps=" (:max-steps limits) " reached")))
        (let [env (context tree path)
              ;; near the step budget only leaves stay legal, so the tree closes
              depth (if (>= (+ (count made) (count (holes tree))) (:max-steps limits))
                      -1
                      (:max-depth limits))
              options (legal hole env pools depth)
              planned (get plan path)
              decision (if (and planned
                                (= (:hole planned) hole)
                                (contains? options (:choice planned)))
                         planned
                         (doto (decide ask (state-of pools tree path hole env feedback)
                                       options path hole)
                           log))]
          (recur (put-at tree path (second (get options (:choice decision))))
                 (conj made decision))))
      [tree made])))

;;; Score

(defn- review [pools tree results]
  {:spec (:prose pools)
   :program (render tree)
   :test_results (test-results results)})

(defn- evaluations
  "Returns what a subtree computed on each traced test, beside that test's result."
  [results values]
  (vec (for [r results
             :when (contains? values (:test r))]
         {:test (:test r)
          :result (:detail r)
          :values (if-let [vs (seq (get values (:test r)))]
                    (str/join ", " vs)
                    "never evaluated")})))

(defn- about
  "Returns decision d's subtree and role; with the values it computed per test when it
  was traced. Params and names are not expressions, so they get no values."
  [tree d results values]
  (cond-> {:role (get-in d [:hole :hint])
           :subtree (flat (node-at tree (:path d)))}
    values (assoc :values (evaluations results values))))

(defn- in-parallel
  "Returns (mapv f xs), each call on its own thread. Rethrows a call's own exception,
  not the ExecutionException around it."
  [f xs]
  (mapv #(try @% (catch ExecutionException e (throw (ex-cause e))))
        (mapv #(future (f %)) xs)))

(defn traced
  "Returns {decision-index {test values}}: what each chosen subtree computed on every
  test, so passing and failing runs can be compared. Untraced subtrees, and tests whose
  run produced nothing (a timeout), are missing."
  [tree decisions results trace]
  (let [spots (into {} (for [[i d] (map-indexed vector decisions)
                             :when (and (seq (:path d))
                                        (seq (:probs d))
                                        (not (#{"params" "bindings" "fname" "fn"}
                                              (get-in d [:hole :kind])))
                                        ;; a spy around recur would move it out of tail position
                                        (not (has-recur? (node-at tree (:path d)))))]
                         [(:path d) i]))
        code (render (instrument tree spots))
        runs (in-parallel #(trace code (:test %)) results)]
    (into {} (for [i (vals spots)]
               [i (into {} (for [[r raw] (map vector results runs)
                                 :when (seq raw)]
                             [(:test r) (get raw (str i) [])]))]))))

(defn- score-fits
  "Returns {decision-index fit}: Jev's Score, 0 wrong .. 3 correct, of every subtree it
  chose. The batches of questions go out in parallel."
  [ask pools tree decisions results values]
  (let [state (review pools tree results)
        questions (for [[i d] (map-indexed vector decisions)
                        :when (and (seq (:path d)) (seq (:probs d)))]
                    [(str "fit" i) {:type "score"
                                    :criteria fit-levels
                                    :instructions (assoc (about tree d results (get values i))
                                                         :question fit-question)}])]
    (into {} (for [answers (in-parallel #(ask state (ordered %))
                                        (partition-all fit-batch questions))
                   [q a] answers]
               [(parse-long (subs q 3)) (get a "score")]))))

;;; Edits

(defn- suspicion
  "Returns how much decision i deserves to change: 1 for a wrong subtree, near 0 for a
  correct one."
  [i decisions fits]
  (if-not (contains? fits i)
    0.5
    ;; sharp: "wrong" (0) must beat "doubtful" (1) by far
    (cond-> (Math/exp (* -2 (fits i)))
      (some (fn [[j f]]
              (and (not= j i)
                   (< f wrong-fit)
                   (within? (:path (nth decisions j)) (:path (nth decisions i)))))
            fits)
      (* holds-wrong))))

(defn- decay [attempt]
  (* (:depth attempt) (Math/log depth-decay)))

(defn single-edits
  "Returns [priority index alternative] for the decisions of attempt from start on;
  earlier ones its parent already offered. Priority is p(alternative) x suspicion:
  Score says where the bug is, the Choice says what else fits."
  [attempt fits start limits]
  (let [decisions (:decisions attempt)]
    (for [i (range start (count decisions))
          :let [d (nth decisions i)
                alts (->> (for [[k p] (:probs d)
                                :when (and (not= k (:choice d)) (>= p (:min-p limits)))]
                            [p k])
                          (sort #(compare %2 %1))
                          (take (:alternatives limits)))
                alts (cond-> (vec alts)
                       (swappable (:choice d)) (conj [swap-prior swap-alt]))]
          [p alt] alts]
      [(+ (Math/log p) (Math/log (suspicion i decisions fits)) (decay attempt)) i alt])))

(defn wrappers
  "Returns the wrap ops whose result is legal where d sits: no (not x) in a number slot."
  [d]
  (filterv #(fits? (core-returns %) (get-in d [:hole :kind])) wrap-ops))

(defn- act
  "Returns the keep / replace / wrap question for d; each option shows the code it
  leads to: (+ 3 1) -> (+ (* 3 _) 1)."
  [tree d results values]
  (let [path (:path d)
        node (node-at tree path)
        parent (node-at tree (pop path))
        code (flat node)
        wrap-options (for [op (wrappers d)
                           :let [new (if (= op "not")
                                       (list (symbol op) node)
                                       (list (symbol op) node '_))]]
                       [op (str code " becomes " (flat new) ": "
                                (flat (put-at parent [(peek path)] new)))])]
    {:type "choice"
     :criteria (ordered (concat [["keep" (str "keep " code ": it is right")]
                                 ["replace" (str "replace " code " with different code")]]
                                wrap-options))
     :instructions (assoc (about tree d results values)
                          :inside (flat parent)
                          :question act-question)}))

(defn wrap-edits
  "Returns wrap edits for the lowest-fit subtrees: (fib (- n 1)) is half of
  (+ (fib (- n 1)) (fib (- n 2)))."
  [ask pools tree attempt fits start values]
  (let [decisions (:decisions attempt)
        worst (->> (for [[i f] fits
                         :when (and (>= i start) (seq (wrappers (nth decisions i))))]
                     [f i])
                   sort
                   (take wrap-ask))]
    (when (seq worst)
      (let [questions (ordered (for [[_ i] worst]
                                 [(str "act" i) (act tree (nth decisions i) (:results attempt)
                                                     (get values i))]))
            answers (ask (review pools tree (:results attempt)) questions)]
        (vec (for [[fit i] worst
                   op (wrappers (nth decisions i))
                   :let [p (get-in answers [(str "act" i) "probabilities" op] 0.0)]
                   :when (>= p 0.1)]
               [(+ (Math/log p) (* -2 fit) (decay attempt)) i (str wrap-prefix op)]))))))

(defn pair-edits
  "Returns two of the top single edits at once, when their subtrees do not overlap:
  (+ 3 1) needs both `3` and `1` fixed."
  ([singles attempt] (pair-edits singles attempt pair-top))
  ([singles attempt top]
   (let [top-edits (vec (take top (sort #(compare %2 %1) singles)))
         path-of #(:path (nth (:decisions attempt) %))]
     (for [a (range (count top-edits))
           b (range (inc a) (count top-edits))
           :let [[p1 i a1] (top-edits a)
                 [p2 j a2] (top-edits b)
                 x (path-of i)
                 y (path-of j)]
           :when (not (or (within? x y) (within? y x)))]
       [(+ p1 p2) [[i a1] [j a2]]]))))

;;; Plans

(defn- mirror
  "Returns decision d moved between the two argument subtrees of the form at root; it
  takes the other slot."
  [root slots d]
  (or (first (for [[src dst] [[(conj root 1) (conj root 2)] [(conj root 2) (conj root 1)]]
                   :when (within? (:path d) src)]
               (assoc d
                      :path (into dst (subvec (:path d) (count src)))
                      :hole (if (= (:path d) src) (get slots dst) (:hole d)))))
      d))

(defn- wrapped
  "Returns decision d moved one level down, into argument a of (op a b) at root."
  [op root d]
  (assoc d
         :path (into (conj root 1) (subvec (:path d) (count root)))
         :hole (if (= (:path d) root) (first (arg-holes op)) (:hole d))))

(defn- apply-edit
  "Returns plan, a map of path to decision, with decision d switched to alt, its
  arguments swapped, or wrapped."
  [plan d alt]
  (let [path (:path d)
        inside (for [[p x] plan :when (and (within? p path) (not= p path))] x)
        outside (into {} (remove #(within? (key %) path) plan))
        [moved own] (cond
                      (= alt swap-alt)
                      (let [slots (into {} (for [x inside
                                                 :when (#{(conj path 1) (conj path 2)} (:path x))]
                                             [(:path x) (:hole x)]))]
                        [(map #(mirror path slots %) inside) d])

                      (str/starts-with? alt wrap-prefix)
                      (let [op (subs alt (count wrap-prefix))]
                        [(map #(wrapped op path %) (cons d inside)) (assoc d :choice op)])

                      ;; the old subtree goes; Jev rebuilds it
                      :else [[] (assoc d :choice alt)])]
    (into (assoc outside path own) (map (juxt :path identity)) moved)))

(defn make-plan
  "Returns the decisions of parent with every change [index alt] applied, as a map of
  path to decision. The changes touch disjoint subtrees."
  [parent changes]
  (reduce (fn [plan [i alt]] (apply-edit plan (nth (:decisions parent) i) alt))
          (into {} (map (juxt :path identity)) (:decisions parent))
          changes))

(defn- describe [parent changes]
  (str/join " + " (for [[i alt] changes
                        :let [d (nth (:decisions parent) i)]]
                    (str "`" (get-in d [:hole :hint]) "`: "
                         (cond
                           (= alt swap-alt) (str "swap arguments of " (:choice d))
                           (str/starts-with? alt wrap-prefix)
                           (str "wrap " (:choice d) " in (" (subs alt (count wrap-prefix)) " ...)")
                           :else (str (:choice d) " -> " alt))))))

;;; Search
;;
;; The search state is one map: the plan to build next, the attempts so far, and two
;; heaps of edits, sorted maps keyed by [-priority tick].

(defn- run-plan
  "Builds and tests the program for the current plan. Returns [tree decisions attempt];
  attempt is nil for a program seen before. Returns nil for an edit that outgrew
  max-steps."
  [{:keys [ask pools limits run log]} {:keys [plan feedback seen edit depth attempts]}]
  (when-let [[tree decisions] (try
                                (build ask pools plan limits log feedback)
                                (catch ExceptionInfo e
                                  (when-not (and (generation-error? e) (seq attempts))
                                    (throw e))))]
    (let [code (render tree)]
      (when-let [errors (seq (structure code))]
        (fail (str "assembler produced a malformed tree: " (vec errors))))
      [tree decisions
       (when-not (seen code)
         (let [attempt {:code code :decisions decisions :results nil :edit edit :depth depth}
               results (when (seq (:tests pools)) (run (source attempt) (:tests pools)))]
           (assoc attempt :results (some-> results vec))))])))

(defn- finished? [limits search attempt]
  (or (nil? (:results attempt))
      (every? :ok (:results attempt))
      (>= (count (:attempts search)) (:tries limits))))

(defn- push [search heap attempt entries]
  (reduce (fn [s [priority changes]]
            (let [tick (inc (:tick s))]
              (-> s
                  (assoc :tick tick)
                  (assoc-in [heap [(- priority) tick]] [attempt changes]))))
          search
          entries))

(defn- enqueue
  "Returns search with the edits of the failed attempt added to its heaps."
  [{:keys [ask pools limits trace]} search tree decisions attempt]
  (let [start (:start search)
        results (:results attempt)
        values (traced tree decisions results trace)
        fit (score-fits ask pools tree decisions results values)
        found (vec (concat (single-edits attempt fit start limits)
                           (wrap-edits ask pools tree attempt fit start values)))
        ;; kept for the feedback a rebuild of this attempt gets
        attempt (assoc attempt :tree tree :values values)]
    (-> search
        (push :singles attempt (for [[p i alt] found] [p [[i alt]]]))
        (push :pairs attempt (pair-edits found attempt)))))

(defn- feedback
  "Returns what a rebuild is told about its parent: the test results, the code the
  edit changes with what it computed, and the latest other programs that failed."
  [search parent changes]
  (let [earlier (->> (:attempts search)
                     (remove #(= (:code %) (:code parent)))
                     (take-last earlier-shown)
                     (mapv (fn [a] {:program (:code a)
                                    :passed (str (score a) " of " (count (:results a)) " tests")})))]
    (cond-> {:program (:code parent)
             :test_results (test-results (:results parent))
             :edited (vec (for [[i _] changes]
                            (about (:tree parent) (nth (:decisions parent) i)
                                   (:results parent) (get (:values parent) i))))}
      (seq earlier) (assoc :earlier earlier))))

(defn- pop-edit
  "Returns search set to build the next edit, or nil when no edit is left. Pairs rank
  below singles (p1 x p2 < p1), so they get every other pop instead of their rank."
  [{:keys [pops singles pairs] :as search}]
  (let [heap (if (and (seq pairs) (or (odd? pops) (empty? singles))) :pairs :singles)]
    (when-let [[k [parent changes]] (first (get search heap))]
      (-> search
          (update heap dissoc k)
          (assoc :pops (inc pops)
                 :plan (make-plan parent changes)
                 :start (inc (apply min (map first changes)))
                 :edit (describe parent changes)
                 :feedback (feedback search parent changes)
                 :depth (inc (:depth parent)))))))

(defn solve
  "Searches for a program meeting spec. Returns {:attempts :calls}. A duplicate program
  costs no try, and a replayed decision costs no API call.

  Options: :tests (extra test forms), :limits, and the effects :run (tests),
  :trace (traced values), :log (each new decision), :log-attempt (each attempt)."
  [spec policy & {:keys [tests limits run log log-attempt trace]
                  :or {tests []
                       limits default-limits
                       run run-tests
                       log (fn [_])
                       log-attempt (fn [_])
                       trace trace-values}}]
  (let [calls (atom 0)
        ctx {:ask (asker policy calls)
             :pools (extract spec tests)
             :limits limits
             :run run
             :log log
             :trace trace}
        done (fn [search] {:attempts (:attempts search) :calls @calls})]
    (loop [search {:pops 0 :singles (sorted-map) :pairs (sorted-map) :tick 0
                   :attempts [] :seen #{}
                   :plan {} :start 0 :edit nil :feedback nil :depth 0}]
      (if (>= (:pops search) (* 4 (:tries limits)))
        (done search)
        (let [[tree decisions attempt] (run-plan ctx search)
              search (cond-> search
                       attempt (-> (update :attempts conj attempt)
                                   (update :seen conj (:code attempt))))]
          (when attempt
            (log-attempt attempt))
          (if (and attempt (finished? limits search attempt))
            (done search)
            (if-let [next-search (pop-edit (if attempt
                                             (enqueue ctx search tree decisions attempt)
                                             search))]
              (recur next-search)
              (done search))))))))
