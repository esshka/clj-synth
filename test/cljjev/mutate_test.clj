(ns cljjev.mutate-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.env :refer [extract]]
            [cljjev.generate :as g]
            [cljjev.mutate :refer [neighbors redundant? sites]]
            [cljjev.test-support :refer [build fact-keys fact-pools limits short-keys]]
            [cljjev.tree :refer [flat node-at]]
            [cljjev.validate :refer [structure]]))

(deftest sites-are-build-run-backwards
  (let [[tree decisions] (build fact-keys)]
    (is (= (map #(dissoc % :probs) decisions) (sites tree fact-pools limits)))))

(deftest neighbors-change-one-subtree-with-legal-options
  (let [[tree _] (build short-keys)
        changed (map :tree (neighbors tree fact-pools limits 3))
        texts (set (map flat changed))]
    (is (contains? texts "(defn factorial [n] (if (<= n 1) 1 (* n (factorial (dec n)))))"))
    ;; another operator over the same arguments, and swapped arguments
    (is (contains? texts "(defn factorial [n] (if (< n 1) 1 (* n 1)))"))
    (is (contains? texts "(defn factorial [n] (if (<= 1 n) 1 (* n 1)))"))
    (is (every? #(empty? (structure (flat %))) changed))
    (is (not-any? #(re-find #"\((fn|let|loop|do) " %) texts))))

(deftest every-change-is-a-program-build-could-make
  ;; so the Lean proofs about build cover local repair too
  (let [[tree _] (build short-keys)
        never (g/asker (fn [_ _] (throw (ex-info "asked" {}))) (atom 0))]
    (doseq [c (neighbors tree fact-pools limits 2)
            :let [plan (into {} (map (juxt :path identity)) (sites (:tree c) fact-pools limits))]]
      (is (= (:tree c) (first (g/build never fact-pools plan limits (fn [_]) nil)))
          (flat (:tree c))))))

(deftest and-wrap-adds-a-whole-test
  (let [pools (extract "the first n items of xs (= [1 2] (take-n 2 [1 2 3])) (= [] (take-n 0 [1 2]))" [])
        tree '(defn take-n [n xs] (if (> n 0) (cons (first xs) (take-n (dec n) (rest xs))) []))
        tests (set (for [c (neighbors tree pools limits 3)
                         :when (= [3 1] (get-in c [:site :path]))]
                     (node-at (:tree c) [3 1])))]
    (is (contains? tests '(and (> n 0) (not (empty? xs)))))))

(deftest redundant-forms-repeat-a-simpler-option
  (is (redundant? '(+ 1 2)))
  (is (redundant? '(- n n)))
  (is (redundant? '(+ n 1)))
  (is (redundant? '(inc (empty? (quote ())))))
  (is (not (redundant? '(+ 1 n))))
  (is (not (redundant? '(recur 1)))))
