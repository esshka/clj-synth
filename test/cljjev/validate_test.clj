(ns cljjev.validate-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.validate :refer [run-tests runtime structure]]))

(deftest shapes
  (is (= [] (structure "(defn f [x] (let [y x] y))")))
  (is (seq (structure "(defn f [x] (inc x)")))
  (is (seq (structure "(let (y 1) y)")))
  (is (seq (structure "(defn f (x) x)")))
  (is (seq (structure "(if-let [a 1 b 2] a b)")))
  (is (seq (structure "(cond a 1 b)"))))

(deftest cond-and-if-let-run
  (when (runtime)
    (let [source (str "(defn sign [x] (cond (< x 0) -1 (> x 0) 1 :else 0))\n"
                      "(defn f [xs] (if-let [x (first xs)] x 0))")
          results (run-tests source ["(= -1 (sign -5))" "(= 0 (sign 0))"
                                     "(= 0 (f []))" "(= 4 (f [4]))"])]
      (is (= [] (structure source)))
      (is (every? :ok results) results))))

(deftest failing-test-reports-both-values
  (when (runtime)
    (let [[r] (run-tests "(defn f [x] x)" ["(= 2 (f 1))"])]
      (is (not (:ok r)))
      (is (= "FAIL expected 2, got 1" (:detail r))))))
