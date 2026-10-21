(ns cljjev.batch-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.batch :refer [run-batch]]
            [cljjev.validate :refer [runtime]]))

(deftest each-program-fails-alone
  (when (runtime)
    (let [tests ["(= 6 (f 3))" "(= 1 (f 0))"]
          programs ['(defn f [n] (if (<= n 1) 1 (* n (f (dec n)))))
                    '(defn f [n] (loop [x n] (recur x)))
                    '(defn f [n] (+ 1 (recur n)))
                    '(defn f [n] (f n))
                    '(defn f [n] n)]
          [ok loops bad-recur deep wrong] (map #(map :detail %) (run-batch programs tests))]
      (is (= ["PASS" "PASS"] ok))
      (is (= ["ERROR step limit" "ERROR step limit"] loops))
      ;; recur outside tail position: an error for this program, and the next one still runs
      (is (every? #(re-find #"^ERROR " %) bad-recur) bad-recur)
      (is (re-find #"^ERROR " (first deep)))
      (is (= ["FAIL expected 6, got 3" "FAIL expected 1, got 0"] wrong)))))
