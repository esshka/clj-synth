(ns cljjev.local-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.batch :refer [run-batch]]
            [cljjev.env :refer [extract]]
            [cljjev.generate :as g]
            [cljjev.local :refer [fitness repair shrink]]
            [cljjev.test-support :refer [build fact-keys fact-pools fact-spec limits script
                                         short-keys]]
            [cljjev.tree :refer [render]]
            [cljjev.validate :refer [run-tests runtime]]))

(defn- judging
  "Returns [policy log]: the judge always answers choice, and log keeps its options."
  [choice]
  (let [log (atom [])]
    [(fn [_ questions]
       (swap! log conj (get-in questions ["judge" :criteria]))
       {"judge" {"type" "choice" "choice" choice "probabilities" {choice 1.0}}})
     log]))

(defn- passing [trees tests]
  (mapv (fn [_] (mapv #(hash-map :test % :ok true :detail "PASS") tests)) trees))

(deftest one-change-fixes-the-recursion
  (when (runtime)
    (let [[tree decisions] (build short-keys)
          results (run-tests (render tree) (:tests fact-pools))
          [policy _] (judging "p0")
          out (repair policy run-batch fact-pools limits tree
                      {:decisions decisions :results results :depth 0} #{})]
      (is (re-find #"\(\* n \(factorial \(dec n\)\)\)" (:code (:attempt out))))
      (is (contains? (:tested out) (read-string (:code (:attempt out))))))))

(deftest jev-picks-among-passing-programs-or-none
  (let [[tree decisions] (build short-keys)
        attempt {:decisions decisions :depth 0
                 :results [{:ok false :detail "FAIL expected 120, got 5"} {:ok true :detail "PASS"}]}
        [none log] (judging "none")
        [second-one _] (judging "p1")]
    (is (nil? (:attempt (repair none passing fact-pools limits tree attempt #{}))))
    (is (= 7 (count (first @log))) "six programs and none")
    (is (some? (:attempt (repair second-one passing fact-pools limits tree attempt #{}))))))

(deftest fitness-ranks-near-misses-and-skips-the-held-out-test
  (let [results [{:ok true :detail "PASS"}
                 {:ok false :detail "FAIL expected 4, got 2"}
                 {:ok false :detail "ERROR boom"}]]
    (is (= [1 (- (/ 2.0 3))] (fitness results 2)))
    (is (> 0 (compare (fitness [{:ok false :detail "FAIL expected 4, got 0"}] nil)
                      (fitness [{:ok false :detail "FAIL expected 4, got 3"}] nil))))))

(deftest search-ends-with-a-local-fix
  (when (runtime)
    (let [build-policy (script short-keys)
          policy (fn [state questions]
                   (if (contains? questions "judge")
                     {"judge" {"type" "choice" "choice" "p0" "probabilities" {"p0" 1.0}}}
                     (build-policy state questions)))
          out (g/solve fact-spec policy :limits (assoc limits :tries 1))]
      (is (g/passed? out))
      (is (re-find #"^local: " (:edit (g/best out))))
      (is (zero? (:tested (g/solve fact-spec (script short-keys) :batch nil
                                   :limits (assoc limits :tries 1))))))))

(deftest shrink-drops-what-the-tests-never-needed
  (when (runtime)
    (let [pools (extract "b raised to the power e (= 8 (power 2 3)) (= 1 (power 5 0))" [])
          attempt (fn [pools code]
                    {:code code :results (run-tests code (:tests pools)) :edit nil :depth 0})
          log (atom [])
          junk (attempt pools (str "(defn power [b e] (if (zero? e) 1 (* (if (even? e) "
                                   "(power b (quot e 2)) (power b (dec e))) b)))"))
          [kept out] (shrink run-batch pools limits #(swap! log conj %) [:earlier junk])
          exact [(attempt fact-pools "(defn factorial [n] (if (<= n 1) 1 (* n (factorial (dec n)))))")]
          failed [(attempt fact-pools "(defn factorial [n] (if (<= n 1) 1 (* n 1)))")]
          two (extract "largest number in xs (= 9 (largest [3 9 2])) (= -1 (largest [-5 -1 -3]))" [])
          both [(attempt two "(defn largest [xs] (max (first (rest xs)) (reduce max (first xs) (rest xs))))")]
          one (extract "write a recursive factorial of n (= 1 (factorial 0))" [])
          unrun [(attempt one "(defn factorial [n] (if (<= n 1) 1 (* n (factorial (dec n)))))")]]
      (is (= :earlier kept))
      (is (= '(defn power [b e] (if (zero? e) 1 (* (power b (dec e)) b))) (read-string (:code out))))
      (is (every? :ok (:results out)))
      (is (re-find #"^shrink of " (:edit out)))
      (is (= [out] @log))
      (is (= exact (shrink run-batch fact-pools limits (fn [_]) exact)))
      (is (= failed (shrink run-batch fact-pools limits (fn [_]) failed)))
      ;; both arguments of max pass alone; the larger one stays
      (is (= '(defn largest [xs] (reduce max (first xs) xs))
             (read-string (:code (peek (shrink run-batch two limits (fn [_]) both))))))
      ;; the one test never runs the else branch, so it stays
      (is (= unrun (shrink run-batch one limits (fn [_]) unrun))))))

(deftest search-ends-with-a-shrunk-program
  (when (runtime)
    (let [keys (into ["[n]" "max" "lit:1"] (rest fact-keys))
          out (g/solve fact-spec (script keys) :limits (assoc limits :tries 1))]
      (is (g/passed? out))
      (is (re-find #"\(if \(<= n 1\) 1 \(\* n \(factorial \(dec n\)\)\)\)\)\n$" (g/source (g/best out))))
      (is (re-find #"^shrink of \(defn factorial \[n\] \(max 1 " (:edit (g/best out)))))))
