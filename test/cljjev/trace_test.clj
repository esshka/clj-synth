(ns cljjev.trace-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.generate :as g]
            [cljjev.test-support :refer [at build short-keys]]
            [cljjev.trace :refer [has-recur? trace-values]]
            [cljjev.tree :refer [node-at render]]
            [cljjev.validate :refer [run-tests runtime]]))

(deftest trace-reports-values
  (when (runtime)
    (let [[tree decisions] (build short-keys)
          results (run-tests (render tree) ["(= 6 (factorial 3))"])
          [test values] (g/traced tree decisions results trace-values)
          at (at decisions)]
      (is (= "(= 6 (factorial 3))" test))
      (is (= ["1"] (values (at "* b"))))
      (is (= [] (values (at "if then")))))))

(deftest recur-is-found-only-where-it-is
  (let [loop-form '(defn f [n] (loop [i n] (if (zero? i) 0 (recur (dec i)))))]
    (is (has-recur? (node-at loop-form [3])))
    (is (not (has-recur? (node-at loop-form [3 1]))))))
