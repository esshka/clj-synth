(ns cljjev.trace-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.generate :as g]
            [cljjev.test-support :refer [at build fact-keys short-keys]]
            [cljjev.trace :refer [has-recur? trace-values]]
            [cljjev.tree :refer [node-at render]]
            [cljjev.validate :refer [run-tests runtime]]))

(deftest trace-reports-values-of-every-test
  (when (runtime)
    (let [[tree decisions] (build short-keys)
          tests ["(= 6 (factorial 3))" "(= 1 (factorial 1))"]
          results (run-tests (render tree) tests)
          values (g/traced tree decisions results trace-values)
          at (at decisions)]
      (is (= ["1"] (get-in values [(at "* b") (first tests)])))
      (is (= [] (get-in values [(at "* b") (second tests)])))
      (is (= ["1"] (get-in values [(at "if then") (second tests)])))
      (is (not (contains? values (at "defn params")))))))

(deftest trace-keeps-the-last-value-of-a-recursion
  (when (runtime)
    (let [[tree decisions] (build fact-keys)
          test "(= 120 (factorial 5))"
          values (g/traced tree decisions (run-tests (render tree) [test]) trace-values)]
      (is (= ["false" "false" "false" "... 1 more" "true"]
             (get-in values [((at decisions) "if test") test]))))))

(deftest recur-is-found-only-where-it-is
  (let [loop-form '(defn f [n] (loop [i n] (if (zero? i) 0 (recur (dec i)))))]
    (is (has-recur? (node-at loop-form [3])))
    (is (not (has-recur? (node-at loop-form [3 1]))))))
