(ns cljjev.local
  "Local repair: every small change to a failed program is run in one batch, with no Jev
  call. Jev's option probabilities rank the changes, tests decide which pass, and one
  Choice picks among those that do."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [cljjev.catalog :refer [ordered]]
            [cljjev.mutate :refer [neighbors]]
            [cljjev.tree :refer [flat node-at render]]))

;; new decisions per change, one entry per round; a round starts from the programs the
;; last one brought closest to passing. Only the first program gets more than one.
(def ^:private rounds [3 2])
;; per round; a looping program costs about 4 ms, so a round stays near 10 s at worst
(def ^:private max-programs 6000)
;; per search, so a spec no small change fixes costs about a minute, not ten
(def ^:private budget 30000)
(def ^:private beam 4)
;; each new decision beyond the first makes a change ten times less likely
(def ^:private size-cost (Math/log 0.1))
(def ^:private swap-p 0.3)
;; for a wrap, and an option Jev never rated
(def ^:private unknown-p 0.05)
;; passing programs Jev picks from
(def ^:private judge-top 6)

(def ^:private judge-question
  (str "Every program in the options passes `tests`. Which one implements `spec` for every "
       "input, not only for the inputs in `tests`?"))

(defn- held-out
  "Returns the index of the test kept out of ranking: the last of three or more."
  [tests]
  (when (>= (count tests) 3) (dec (count tests))))

(defn- miss
  "Returns how far a result is from passing: 0 for a pass, below 1 for a number, closer
  to 0 the nearer it is to the expected one, 1 for another wrong value, 2 for an error."
  [{:keys [ok detail]}]
  (cond
    ok 0.0
    (not (str/starts-with? detail "FAIL")) 2.0
    :else (let [[_ want got] (re-find #"^FAIL expected (.+), got (.+)$" detail)
                [w g] (try [(edn/read-string want) (edn/read-string got)]
                           (catch Exception _ nil))]
            (if (and (number? w) (number? g))
              (let [d (Math/abs (double (- w g)))] (/ d (inc d)))
              1.0))))

(defn fitness
  "Returns [passes -misses] over every result but the one at index skip; a larger value
  is closer to passing."
  [results skip]
  (let [rs (keep-indexed #(when (not= %1 skip) %2) results)]
    [(count (filter :ok rs)) (- (reduce + 0.0 (map miss rs)))]))

(defn- prior
  "Returns the log-likelihood of change: how unsure Jev was of the option it replaces,
  times the probability Jev gave the new one, less a cost per extra decision."
  [jev change]
  (let [site (:site change)
        d (get jev (:path site))
        probs (when (= (:hole d) (:hole site)) (:probs d))
        doubt (if (seq probs) (max 0.05 (- 1.0 (get probs (:choice d) 0.0))) 0.5)
        p (get probs (:key change) (if (= "swap" (:key change)) swap-p unknown-p))]
    (+ (Math/log doubt) (Math/log (max p 1e-4)) (* size-cost (dec (:size change))))))

(defn- edit-of
  "Returns what candidate c changed, after what its parent changed."
  [c]
  (let [{:keys [path hole]} (:site c)
        p (:parent c)
        own (str "`" (:hint hole) "`: " (flat (node-at (:tree p) path)) " -> "
                 (flat (node-at (:tree c) path)))]
    (if (:edit p) (str (:edit p) "; " own) own)))

(defn- judge
  "Returns the program of wins, likeliest first, that Jev picks as implementing the spec;
  nil when Jev says each one only fits the tests. Broad search finds programs like
  (cons (first xs) (filter even? xs)) for take-n."
  [ask pools wins]
  (let [top (vec (take judge-top wins))
        criteria (ordered (concat (map-indexed (fn [i w] [(str "p" i) (flat (:tree w))]) top)
                                  [["none" "none: each program only fits the tests"]]))
        choice (get-in (ask {:spec (:prose pools) :tests (:tests pools)}
                            {"judge" {:type "choice" :instructions judge-question
                                      :criteria criteria}})
                       ["judge" "choice"])]
    (when-not (contains? criteria choice)
      (throw (ex-info (str "answer " (pr-str choice) " is not one of the offered options")
                      {:type :cljjev.generate/generation})))
    (when (not= choice "none")
      (top (parse-long (subs choice 1))))))

(defn- candidates
  "Returns up to n untested changes to the frontier's programs, likeliest first, one per
  program. Programs are plain data, so the tree itself is the key."
  [pools limits jev frontier size tested n]
  (if-not (pos? n)
    []
    (->> (for [p frontier
               c (neighbors (:tree p) pools limits size)
               :when (not (tested (:tree c)))]
           {:tree (:tree c) :parent p :site (:site c) :prior (+ (:prior p) (prior jev c))})
         (reduce (fn [m c] (if (> (:prior c) (get-in m [(:tree c) :prior] ##-Inf))
                             (assoc m (:tree c) c)
                             m))
                 {})
         vals
         (sort-by :prior >)
         (take n)
         vec)))

(defn repair
  "Runs the small changes to attempt's tree through batch, for up to two rounds and
  within the search's budget of programs. Returns {:attempt :tested :hot}: attempt, when
  a change passes every test, is the program Jev picks among those that do; tested adds
  every program tree run to tested; hot holds the paths of attempt's decisions where one
  change brought it closer to passing, and is missing when the budget left nothing to run.
  The last of three or more tests is held out of ranking, so passing it is evidence a fix
  was not chosen to fit."
  [ask batch pools limits tree attempt tested]
  (if (>= (count tested) budget)
    {:tested tested}
    (let [tests (:tests pools)
          skip (held-out tests)
          jev (into {} (map (juxt :path identity)) (:decisions attempt))
          start {:tree tree :prior 0.0 :fit (fitness (:results attempt) skip)}]
      (loop [[size & more] (if (zero? (:depth attempt)) rounds (take 1 rounds))
             frontier [start]
             tested tested
             hot #{}]
        (let [cands (candidates pools limits jev frontier size tested
                                (min max-programs (- budget (count tested))))
              runs (map #(assoc %1 :results %2 :fit (fitness %2 skip))
                        cands (batch (map :tree cands) tests))
              tested (into tested (map :tree cands))
              pass? #(every? :ok (:results %))
              better (filter #(pos? (compare (:fit %) (:fit (:parent %)))) runs)
              hot (into hot (for [c better :when (= start (:parent c))] (get-in c [:site :path])))
              wins (filter pass? runs)
              win (when (seq wins) (judge ask pools wins))
            ;; a rejected program passes every test; changing it again finds more of its kind
              better (remove pass? better)]
          (cond
            win {:attempt {:code (render (:tree win)) :decisions [] :results (:results win)
                           :edit (str "local: " (edit-of win)) :depth (inc (:depth attempt))}
                 :tested tested
                 :hot hot}

            (or (empty? more) (empty? better)) {:tested tested :hot hot}

            :else (recur more
                         (->> better
                              (group-by #(map :detail (:results %)))
                              vals
                              (map first)
                              (sort-by (juxt :fit :prior) #(compare %2 %1))
                              (take beam)
                              (map #(assoc % :edit (edit-of %))))
                         tested
                         hot)))))))
