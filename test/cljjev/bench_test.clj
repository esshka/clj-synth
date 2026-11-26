(ns cljjev.bench-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.batch :refer [run-batch]]
            [cljjev.bench :as b]
            [cljjev.env :refer [extract with-library]]
            [cljjev.generate :as g]
            [cljjev.library :as library]
            [cljjev.mutate :refer [sites]]
            [cljjev.tree :refer [read-all]]
            [cljjev.validate :refer [run-tests runtime]]))

;; one known answer per function of the split set; the search never sees them
(def ^:private answers
  {"prime?" "(defn prime? [n] (if (< n 2) false (every? (fn [x] (not (zero? (mod n x)))) (range 2 n))))"
   "prime-sum" "(defn prime-sum [n] (reduce + 0 (filter prime? (range 2 n))))"
   "gcd" "(defn gcd [a b] (if (zero? b) a (gcd b (mod a b))))"
   "lcm" "(defn lcm [a b] (quot (* a b) (gcd a b)))"
   "digit-sum" "(defn digit-sum [n] (loop [x n acc 0] (if (zero? x) acc (recur (quot x 10) (+ (mod x 10) acc)))))"
   "digital-root" "(defn digital-root [n] (if (< n 10) n (digital-root (digit-sum n))))"
   "rev" "(defn rev [coll] (reduce (fn [acc x] (cons x acc)) [] coll))"
   "palindrome?" "(defn palindrome? [xs] (= xs (rev xs)))"
   "factorial" "(defn factorial [n] (if (zero? n) 1 (* n (factorial (dec n)))))"
   "choose" "(defn choose [n k] (quot (factorial n) (* (factorial k) (factorial (- n k)))))"
   "fib" "(defn fib [n] (if (<= n 1) n (+ (fib (- n 2)) (fib (- n 1)))))"
   "fib-sum" "(defn fib-sum [n] (reduce + 0 (map fib (range 0 (inc n)))))"
   "collatz" (str "(defn collatz [n] (loop [x n steps 0] (if (= x 1) steps "
                  "(recur (if (even? x) (/ x 2) (+ (* 3 x) 1)) (+ steps 1)))))")
   "longest" "(defn longest [n] (reduce max 0 (map collatz (range 1 (inc n)))))"
   "divides?" "(defn divides? [d n] (zero? (mod n d)))"
   "divisor-sum" "(defn divisor-sum [n] (reduce + 0 (filter (fn [x] (divides? x n)) (range 1 n))))"
   "perfect?" "(defn perfect? [n] (= n (divisor-sum n)))"})

(deftest every-split-problem-has-an-answer-the-search-can-reach
  (when (runtime)
    (doseq [chain b/split]
      (reduce (fn [solved spec]
                (let [pools (extract spec [])
                      name (first (:names pools))
                      code (answers name)
                      tree (first (read-all code))
                      tests (into (:tests pools) (b/checks name))
                      sources (apply str (map :source solved))]
                  (is (every? :ok (run-tests (str sources (g/source {:code code})) tests)) name)
                  ;; local repair's runner has step and time limits of its own
                  (is (every? :ok (first (run-batch [(apply list 'do (concat (map :tree solved) [tree]))]
                                                    tests)))
                      name)
                  ;; nil here means no sequence of options builds the answer
                  (is (sites tree (with-library pools solved) g/default-limits) name)
                  (conj solved (library/entry spec {:code code}))))
              []
              chain))))
