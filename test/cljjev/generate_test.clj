(ns cljjev.generate-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.generate :as g]
            [cljjev.policy :refer [mock-policy]]
            [cljjev.test-support :refer [at attempt build fact-keys fact-pools fact-spec limits plan
                                         script short-keys wrap-keys]]
            [cljjev.tree :refer [node-at]]
            [cljjev.validate :refer [runtime]]))

(deftest scripted-choices-build-factorial
  (let [out (g/solve fact-spec (script fact-keys) :limits (assoc limits :tries 1))]
    (is (re-find #"\(if \(<= n 1\) 1 \(\* n \(factorial \(dec n\)\)\)\)" (g/source (g/best out))))
    (when (runtime)
      (is (g/passed? out) (:results (g/best out))))))

(deftest edit-rebuilds-only-its-subtree
  (let [[_ parent] (build fact-keys)
        ;; switch `if test` from <= to =; its two args are asked again, the rest is replayed
        [tree _] (build ["lit:0" "local:n"] (plan parent [((at parent) "if test") "="]))]
    (is (= '(= 0 n) (node-at tree [3 1])))
    (is (= '(* n (factorial (dec n))) (node-at tree [3 3])))))

(deftest swap-moves-whole-argument-subtrees
  (let [[_ parent] (build ["[n]" "-" "lit:1" "call:factorial" "dec" "local:n"])
        i (first (keep-indexed #(when (= "-" (:choice %2)) %1) parent))
        [tree _] (build [] (plan parent [i "swap"]))]
    (is (= '(- (factorial (dec n)) 1) (node-at tree [3])))))

(deftest wrap-keeps-the-old-subtree-as-first-argument
  (let [[_ parent] (build wrap-keys)
        ;; Jev fills only `* b`
        [tree _] (build ["local:n"] (plan parent [((at parent) "if else") "wrap:*"]))]
    (is (= '(* (factorial (dec n)) n) (node-at tree [3 3])))))

(deftest wrap-edits-come-from-the-keep-replace-wrap-choice
  (let [[tree parent] (build wrap-keys)
        i ((at parent) "if else")
        judge (fn [_ questions]
                (into {} (for [q (keys questions)] [q {"probabilities" {"*" 0.7 "replace" 0.3}}])))
        edits (g/wrap-edits (g/asker judge (atom 0)) fact-pools tree (attempt parent)
                            {i 0.2 (inc i) 2.9} 0 ["t" {}])]
    (is (some #{[i "wrap:*"]} (map rest edits)))))

(deftest pair-applies-two-disjoint-edits-and-skips-nested-ones
  (let [[_ parent] (build short-keys)
        at (at parent)
        [tree _] (build ["lit:2"] (plan parent [(at "if test") "swap"] [(at "* b") "wrap:+"]))
        singles [[-1.0 (at "if else") "+"] [-1.1 (at "* b") "lit:2"] [-1.2 (at "if test") "="]]]
    (is (= '(if (<= 1 n) 1 (* n (+ 1 2))) (node-at tree [3])))
    ;; `if else` holds `* b`: that pair would edit one subtree twice
    (is (= 2 (count (g/pair-edits singles (attempt parent)))))))

(deftest not-wrap-negates-a-condition-and-skips-number-slots
  (let [[_ parent] (build short-keys)
        at (at parent)
        [tree _] (build [] (plan parent [(at "if test") "wrap:not"]))]
    (is (= '(not (<= n 1)) (node-at tree [3 1])))
    (is (not (some #{"not"} (g/wrappers (nth parent (at "* b"))))))))

(deftest suspicious-nodes-are-edited-first
  (let [[_ parent] (build fact-keys)
        ;; every subtree is correct except `dec x`
        fits (assoc (zipmap (range (count parent)) (repeat 3.0)) (dec (count parent)) 0.1)
        best (reduce #(if (pos? (compare %2 %1)) %2 %1)
                     (g/single-edits (attempt parent) fits 0 limits))]
    (is (= "dec x" (get-in (nth parent (second best)) [:hole :hint])))))

(deftest mock-search-finds-passing-factorial
  (when (runtime)
    (let [out (g/solve fact-spec (mock-policy))]
      (is (g/passed? out) (g/source (g/best out))))))
