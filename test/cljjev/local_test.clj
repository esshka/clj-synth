(ns cljjev.local-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.batch :refer [run-batch]]
            [cljjev.generate :as g]
            [cljjev.local :refer [fitness repair]]
            [cljjev.test-support :refer [build fact-pools fact-spec limits script short-keys]]
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
