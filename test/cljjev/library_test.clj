(ns cljjev.library-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.catalog :refer [legal]]
            [cljjev.env :refer [context extract with-library]]
            [cljjev.generate :as g]
            [cljjev.library :refer [entry solve]]
            [cljjev.policy :refer [mock-policy]]
            [cljjev.test-support :refer [limits script]]
            [cljjev.trace :refer [trace-values]]
            [cljjev.tree :refer [->hole]]
            [cljjev.validate :refer [runtime]]))

(def ^:private sq (entry "square of x (= 9 (sq 3)) (= 0 (sq 0))" {:code "(defn sq [x] (* x x))"}))
(def ^:private spec "sum of the squares of a and b (= 25 (sum-sq 3 4)) (= 0 (sum-sq 0 0))")

(deftest a-solved-function-is-an-option-where-its-kind-fits
  (let [pools (with-library (extract (str spec " sq") []) [sq])
        env (context '(defn sum-sq [a b] _) [3])
        at #(legal (->hole % "t") env pools 8)]
    (is (= (list 'sq (->hole "num" "sq x")) (second (get (at "num") "call:sq"))))
    (is (= "(sq x) square of x" (first (get (at "any") "call:sq"))))
    (is (contains? (at "fn") "ref:sq"))
    ;; a number is no test
    (is (not (contains? (at "bool") "call:sq")))
    ;; its name is no longer free for a local
    (is (not (some #{"sq"} (:locals pools))))))

(deftest run-and-batch-load-the-library
  (when (runtime)
    (let [build (script ["[a b]" "+" "call:sq" "local:a" "call:sq" "local:a"])
          ;; the first program is (+ (sq a) (sq a)); local repair's batch must load sq to fix it
          policy (fn [state questions]
                   (if (contains? questions "judge")
                     {"judge" {"type" "choice" "choice" "p0" "probabilities" {"p0" 1.0}}}
                     (build state questions)))
          out (solve spec policy [sq] :limits (assoc limits :tries 1))]
      (is (g/passed? out) (:results (g/best out)))
      (is (re-find #"\(sq b\)" (:code (g/best out))))
      (is (re-find #"^local: " (:edit (g/best out)))))))

(deftest traces-load-the-library
  (when (runtime)
    (let [build (script ["[a b]" "+" "call:sq" "local:a" "call:sq" "local:a"])
          mock (mock-policy)
          asked (atom 0)
          ;; the scripted program fails, so its subtrees are traced; later questions go to the mock
          policy (fn [state questions]
                   (if (and (contains? questions "prod") (<= (swap! asked inc) 6))
                     (build state questions)
                     (mock state questions)))
          seen (atom [])
          trace (fn [code test]
                  (let [values (trace-values code test)]
                    (swap! seen conj [code values])
                    values))]
      (solve spec policy [sq] :batch nil :trace trace :limits (assoc limits :tries 2))
      (is (some #(re-find #"\(defn sq " (first %)) @seen))
      (is (some #(seq (second %)) @seen)))))
