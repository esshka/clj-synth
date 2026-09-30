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
                            {i 0.2 (inc i) 2.9} 0 {})]
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

(defn- recorder
  "Returns [policy log]: the policy answers like the mock and logs every request."
  []
  (let [log (atom [])
        mock (mock-policy)]
    [(fn [state questions] (swap! log conj [state questions]) (mock state questions)) log]))

(deftest score-questions-see-values-of-every-test
  (let [[tree parent] (build short-keys)
        at (at parent)
        results [{:test "(= 1 (factorial 1))" :ok true :detail "PASS"}
                 {:test "(= 6 (factorial 3))" :ok false :detail "FAIL expected 6, got 3"}]
        values {(at "* b") {"(= 1 (factorial 1))" [] "(= 6 (factorial 3))" ["1"]}}
        [policy log] (recorder)]
    (#'g/score-fits (g/asker policy (atom 0)) fact-pools tree parent results values)
    (let [[state questions] (first @log)
          ins #(get-in questions [(str "fit" (at %)) :instructions])]
      (is (= [{:test "(= 1 (factorial 1))" :result "PASS"}
              {:test "(= 6 (factorial 3))" :result "FAIL expected 6, got 3"}]
             (:test_results state)))
      (is (= [{:test "(= 1 (factorial 1))" :result "PASS" :values "never evaluated"}
              {:test "(= 6 (factorial 3))" :result "FAIL expected 6, got 3" :values "1"}]
             (:values (ins "* b"))))
      ;; a params vector is not an expression: no values, not "never evaluated"
      (is (not (contains? (ins "defn params") :values))))))

(deftest a-rebuild-sees-the-edited-code-and-earlier-programs
  (let [[tree parent] (build short-keys)
        i ((at parent) "* b")
        results [{:test "(= 6 (factorial 3))" :ok false :detail "FAIL expected 6, got 3"}]
        attempt {:code "(parent)" :decisions parent :results results :depth 0
                 :tree tree :values {i {"(= 6 (factorial 3))" ["1"]}}}
        search {:attempts [{:code "(old)" :results results} {:code "(parent)" :results results}]}
        fb (#'g/feedback search attempt [[i "local:n"]])]
    (is (= [{:role "* b" :subtree "1"
             :values [{:test "(= 6 (factorial 3))" :result "FAIL expected 6, got 3" :values "1"}]}]
           (:edited fb)))
    (is (= [{:program "(old)" :passed "0 of 1 tests"}] (:earlier fb)))))

(deftest the-retry-note-appears-only-on-a-rebuild
  (let [[policy log] (recorder)
        ask (g/asker policy (atom 0))]
    (g/build ask fact-pools {} limits (fn [_]) nil)
    (g/build ask fact-pools {} limits (fn [_]) {:program "(p)"})
    (let [texts (map #(get-in % [1 "prod" :instructions]) @log)
          retry? #(re-find #"previous_attempt" %)]
      (is (some retry? texts))
      (is (some (complement retry?) texts)))))
