(ns cljjev.generate-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.generate :as g]
            [cljjev.policy :refer [mock-policy]]
            [cljjev.test-support :refer [at attempt build fact-keys fact-pools fact-spec limits plan
                                         script short-keys wrap-keys]]
            [cljjev.tree :refer [holes node-at]]
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

(deftest restart-asks-every-later-hole-again
  (let [[_ parent] (build fact-keys)
        ;; `if then` was 1; a plain edit would replay it, a restart asks it again
        [tree _] (build ["local:n" "lit:0" "lit:2" "*" "local:n" "call:factorial" "dec" "local:n"]
                        (plan parent [((at parent) "if test") "restart:="]))]
    (is (= '(if (= n 0) 2 (* n (factorial (dec n)))) (node-at tree [3])))))

(deftest restarts-come-from-close-runners-up-first-decisions-first
  (let [d (fn [path choice probs] {:path path :hole {:hint "h"} :choice choice :probs probs})
        decisions [(d [3] "if" {"if" 0.3 "cond" 0.25 "let" 0.01})
                   (d [3 1] "<=" {"<=" 0.9 "<" 0.1})
                   (d [3 2] "lit:1" {"lit:1" 0.5 "lit:0" 0.45})]
        restarts (g/restart-edits (attempt decisions))]
    ;; `let` and `<` are far behind their winners
    (is (= #{[0 "restart:cond"] [2 "restart:lit:0"]} (set (map rest restarts))))
    ;; the same ratio early rebuilds more, so it ranks first
    (is (= [0 "restart:cond"] (rest (first restarts))))))

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

(deftest pick-candidates-show-the-code-each-change-leads-to
  (let [[tree parent] (build short-keys)
        at (at parent)
        texts (into {} (g/pick-candidates tree (attempt parent) fact-pools limits))]
    (is (= "`if test`: (<= n 1) becomes (<= 1 n) in (if (<= 1 n) 1 (* n 1))"
           (texts [(at "if test") "swap"])))
    (is (= "`* b`: 1 becomes 0 in (* n 0)" (texts [(at "* b") "lit:0"])))
    (is (= "`* b`: 1 becomes (+ 1 __) in (* n (+ 1 __))" (texts [(at "* b") "wrap:+"])))))

(deftest every-step-budget-closes-the-program
  ;; a compound form chosen just before the budget used to open more holes than it had left
  (doseq [n (range 4 21)]
    (let [[tree made] (g/build (g/asker (mock-policy) (atom 0)) fact-pools {}
                               (assoc limits :max-steps n) (fn [_]) nil)]
      (is (<= (count made) n))
      (is (empty? (holes tree)) (str "max-steps " n)))))

(deftest mock-pick-search-finds-passing-factorial
  (when (runtime)
    (let [out (g/solve fact-spec (mock-policy) :flow :pick)]
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
