(ns cljjev.env-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.env :refer [context extract]]
            [cljjev.test-support :refer [fact legal-at]]
            [cljjev.tree :refer [->hole]]))

(deftest tests-fix-name-and-arity
  (let [pools (extract "larger of a and b (= 5 (max2 5 3))" [])
        tree (list 'defn 'max2 (->hole "params" "defn params") (->hole "any" "b"))
        params (legal-at tree [2] (->hole "params" "defn params") pools)]
    (is (= [["max2"] {"max2" 2}] [(:names pools) (:arity pools)]))
    (is (contains? params "[a b]"))
    (is (every? #(= 2 (count (re-seq #"\S+" (subs % 1 (dec (count %)))))) (keys params)))))

(deftest test-values-type-the-params
  (let [pools (extract "dot product of xs and ys (= 32 (dot [1 2 3] [4 5 6])) (= 0 (dot [] []))" [])
        tree '(defn dot [xs ys] (* x (let [ys 1] y)))]
    (is (= ["seq" "seq"] (get-in pools [:param-kinds "dot"])))
    ;; a vector never fills a number slot
    (is (not (contains? (legal-at tree [3 1] (->hole "num" "* b") pools) "local:xs")))
    ;; the let rebinds ys, so its test type no longer applies
    (is (contains? (legal-at tree [3 2 2] (->hole "num" "t") pools) "local:ys"))))

(deftest let-init-does-not-see-its-own-name
  (let [tree (fact (list 'let ['x (->hole "any" "i") 'y (->hole "any" "j")] 'y))]
    (is (not (some #{"x"} (:locals (context tree [3 1 1])))))
    (is (some #{"x"} (:locals (context tree [3 1 3]))))))

(deftest if-let-name-only-in-then
  (let [tree (fact (list 'if-let ['x (->hole "any" "i")] (->hole "any" "t") (->hole "any" "e")))
        binding (legal-at tree [3 1] (->hole "bindings" "if-let binding"))]
    (is (not (some #{"x"} (:locals (context tree [3 1 1])))))
    (is (some #{"x"} (:locals (context tree [3 2]))))
    (is (not (some #{"x"} (:locals (context tree [3 3])))))
    (is (every? #(not (re-find #" " %)) (keys binding)))))

(deftest cond-branches-are-tail-tests-are-not
  (let [tree (fact '(cond t1 v1 t2 v2 :else v3))]
    (is (= [false true false true false true]
           (for [i (range 1 7)] (:tail (context tree [3 i])))))))
