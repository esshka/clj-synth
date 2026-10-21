(ns cljjev.bench
  "Benchmark: clojure -M:bench [--mock] [pick] [--runs N] [basic|hard|held] runs a spec set
  N times and reports the pass rate and API calls of each run, their median and range when
  N > 1, and writes the runs to bench/results/. pick runs the :pick search flow. A program
  passes only if it also passes hidden checks the search never sees."
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [cljjev.cli :refer [api-key]]
            [cljjev.env :refer [extract]]
            [cljjev.generate :as g]
            [cljjev.jev :as jev]
            [cljjev.policy :refer [mock-policy]]
            [cljjev.validate :refer [run-tests]])
  (:import (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))

(def basic
  ["write a recursive factorial of n (= 120 (factorial 5)) (= 1 (factorial 0))"
   "return the larger of a and b (= 5 (max2 5 3)) (= 7 (max2 2 7))"
   "sum the numbers in xs (= 6 (sum-list [1 2 3])) (= 0 (sum-list []))"
   "square every number in xs (= [1 4 9] (squares [1 2 3])) (= [] (squares []))"
   "count the items of coll recursively, without count (= 3 (size [7 8 9])) (= 0 (size []))"
   "return the nth fibonacci number (= 0 (fib 0)) (= 1 (fib 1)) (= 8 (fib 6)) (= 55 (fib 10))"
   "keep only the positive numbers of xs (= [1 2] (positives [1 -3 2])) (= [] (positives [-1]))"
   "sum of the squares of xs (= 14 (sum-squares [1 2 3])) (= 0 (sum-squares []))"
   "b raised to the power e (= 8 (power 2 3)) (= 1 (power 5 0))"
   "absolute value of x (= 3 (abs-val -3)) (= 4 (abs-val 4))"
   "reverse the items of coll (= [3 2 1] (rev [1 2 3])) (= [] (rev []))"
   "last item of xs, recursively (= 3 (final [1 2 3])) (= 7 (final [7]))"
   "sign of x: -1, 0 or 1 (= -1 (sign -5)) (= 0 (sign 0)) (= 1 (sign 3))"
   (str "first item of xs, or 0 when xs is empty "
        "(= 4 (first-or-zero [4 5])) (= 0 (first-or-zero []))")
   (str "clamp x between lo and hi "
        "(= 5 (clamp 5 0 10)) (= 0 (clamp -3 0 10)) (= 10 (clamp 12 0 10))")])

(def hard
  ["greatest common divisor of a and b (= 6 (gcd 12 18)) (= 1 (gcd 7 5)) (= 5 (gcd 5 0))"
   "largest number in xs (= 9 (largest [3 9 2])) (= -1 (largest [-5 -1 -3]))"
   "count the even numbers in xs (= 2 (count-evens [1 2 3 4])) (= 0 (count-evens []))"
   "how many times x occurs in xs (= 2 (occurrences 3 [3 1 3])) (= 0 (occurrences 9 [1 2]))"
   "the numbers from 0 up to but not including n (= [0 1 2] (upto 3)) (= [] (upto 0))"
   (str "the first n items of xs "
        "(= [1 2] (take-n 2 [1 2 3])) (= [] (take-n 0 [1 2])) (= [1] (take-n 5 [1]))")
   "average of the numbers in xs (= 2 (average [1 2 3])) (= 5 (average [5]))"
   (str "how many numbers in xs are greater than t "
        "(= 2 (count-above 3 [1 4 5 2])) (= 0 (count-above 9 [1]))")
   "dot product of xs and ys (= 32 (dot [1 2 3] [4 5 6])) (= 0 (dot [] []))"
   (str "the item at index n of xs, without nth "
        "(= 30 (item-at [10 20 30] 2)) (= 10 (item-at [10] 0))")
   (str "sum of the decimal digits of n "
        "(= 6 (digit-sum 123)) (= 0 (digit-sum 0)) (= 9 (digit-sum 9))")
   (str "is n a prime number (= true (prime? 7)) (= false (prime? 8)) (= false (prime? 1)) "
        "(= true (prime? 2)) (= false (prime? 9)) (= false (prime? 15))")
   (str "number of collatz steps from n down to 1 (= 0 (collatz 1)) (= 1 (collatz 2)) "
        "(= 7 (collatz 3)) (= 5 (collatz 5)) (= 16 (collatz 7))")
   "sum of the odd numbers in xs (= 4 (sum-odd [1 2 3])) (= 0 (sum-odd [2]))"])

;; never tuned on: a change that helps only basic and hard is fitting those sets
(def held
  ["product of the numbers in xs (= 24 (product [1 2 3 4])) (= 1 (product []))"
   "smallest number in xs (= 1 (smallest [3 1 2])) (= -4 (smallest [-4 0 5]))"
   "count the negative numbers in xs (= 2 (count-neg [-1 2 -3])) (= 0 (count-neg [1]))"
   "sum of the numbers from 1 to n (= 15 (sum-to 5)) (= 0 (sum-to 0)) (= 1 (sum-to 1))"
   "double every number in xs (= [2 4] (doubled [1 2])) (= [] (doubled []))"
   (str "is every number in xs even "
        "(= true (all-even? [2 4])) (= false (all-even? [2 3])) (= true (all-even? []))")
   "number of decimal digits of n (= 3 (digits 123)) (= 1 (digits 7)) (= 2 (digits 10))"
   (str "drop the first n items of xs "
        "(= [3] (drop-n 2 [1 2 3])) (= [1 2] (drop-n 0 [1 2])) (= [] (drop-n 5 [1]))")
   "remove the zeros from xs (= [1 2] (no-zeros [0 1 0 2])) (= [] (no-zeros [0]))"
   "sum of the squares from 1 to n (= 14 (square-sum 3)) (= 0 (square-sum 0))"
   "the second item of xs (= 2 (second-item [1 2 3])) (= 9 (second-item [8 9]))"
   "largest minus smallest number in xs (= 4 (spread [3 1 5])) (= 0 (spread [7]))"])

;; tests the search never sees: a program that passes the spec's tests but not these only
;; fits them, like (cons (first xs) (filter even? xs)) for take-n
(def checks
  {"factorial" ["(= 6 (factorial 3))" "(= 24 (factorial 4))"]
   "max2" ["(= 4 (max2 4 4))" "(= -1 (max2 -1 -3))"]
   "sum-list" ["(= 10 (sum-list [1 2 3 4]))" "(= -2 (sum-list [-2]))"]
   "squares" ["(= [16 25] (squares [4 5]))" "(= [4] (squares [-2]))"]
   "size" ["(= 1 (size [5]))" "(= 5 (size [1 1 1 1 1]))"]
   "fib" ["(= 2 (fib 3))" "(= 13 (fib 7))"]
   "positives" ["(= [5] (positives [0 5 -2]))" "(= [] (positives []))"]
   "sum-squares" ["(= 25 (sum-squares [3 4]))" "(= 4 (sum-squares [-2]))"]
   "power" ["(= 16 (power 2 4))" "(= 27 (power 3 3))" "(= 5 (power 5 1))"]
   "abs-val" ["(= 0 (abs-val 0))" "(= 10 (abs-val -10))"]
   "rev" ["(= [2 1] (rev [1 2]))" "(= [9] (rev [9]))"]
   "final" ["(= 5 (final [4 5]))" "(= 1 (final [3 2 1]))"]
   "sign" ["(= -1 (sign -1))" "(= 1 (sign 100))"]
   "first-or-zero" ["(= 9 (first-or-zero [9]))" "(= -1 (first-or-zero [-1 2]))"]
   "clamp" ["(= 3 (clamp 3 1 4))" "(= 1 (clamp 0 1 4))" "(= 4 (clamp 9 1 4))"]
   "gcd" ["(= 4 (gcd 8 12))" "(= 7 (gcd 0 7))" "(= 3 (gcd 9 6))"]
   "largest" ["(= 4 (largest [4]))" "(= 8 (largest [1 8 8 2]))"]
   "count-evens" ["(= 3 (count-evens [2 4 6]))" "(= 1 (count-evens [0 1]))"]
   "occurrences" ["(= 3 (occurrences 1 [1 1 1]))" "(= 1 (occurrences 2 [1 2 3]))"]
   "upto" ["(= [0] (upto 1))" "(= [0 1 2 3 4] (upto 5))"]
   "take-n" ["(= [1 2 3] (take-n 3 [1 2 3 4]))" "(= [] (take-n 2 []))" "(= [5 6] (take-n 2 [5 6 7]))"]
   "average" ["(= 3 (average [2 4]))" "(= 0 (average [-1 1]))"]
   "count-above" ["(= 1 (count-above 0 [-1 1]))" "(= 3 (count-above 1 [2 3 4]))"]
   "dot" ["(= 11 (dot [1 2] [3 4]))" "(= 6 (dot [2] [3]))"]
   "item-at" ["(= 2 (item-at [1 2 3] 1))" "(= 7 (item-at [5 6 7] 2))"]
   "digit-sum" ["(= 10 (digit-sum 55))" "(= 1 (digit-sum 100))" "(= 15 (digit-sum 12345))"]
   "prime?" ["(= true (prime? 3))" "(= true (prime? 13))" "(= false (prime? 21))" "(= false (prime? 4))"]
   "collatz" ["(= 2 (collatz 4))" "(= 19 (collatz 9))"]
   "sum-odd" ["(= 9 (sum-odd [1 3 5]))" "(= 0 (sum-odd []))" "(= 5 (sum-odd [2 5]))"]
   "product" ["(= 6 (product [2 3]))" "(= 0 (product [5 0]))"]
   "smallest" ["(= 7 (smallest [7]))" "(= -9 (smallest [1 -9 3]))"]
   "count-neg" ["(= 0 (count-neg []))" "(= 3 (count-neg [-1 -2 -3]))"]
   "sum-to" ["(= 3 (sum-to 2))" "(= 55 (sum-to 10))"]
   "doubled" ["(= [0 -2] (doubled [0 -1]))" "(= [10] (doubled [5]))"]
   "all-even?" ["(= false (all-even? [1]))" "(= true (all-even? [0 8]))"]
   "digits" ["(= 4 (digits 1000))" "(= 2 (digits 99))"]
   "drop-n" ["(= [] (drop-n 1 []))" "(= [2 3] (drop-n 1 [1 2 3]))"]
   "no-zeros" ["(= [] (no-zeros []))" "(= [3 4] (no-zeros [3 0 4]))"]
   "square-sum" ["(= 1 (square-sum 1))" "(= 30 (square-sum 4))"]
   "second-item" ["(= 5 (second-item [4 5 6 7]))" "(= 0 (second-item [1 0]))"]
   "spread" ["(= 9 (spread [10 1]))" "(= 6 (spread [-3 3 0]))"]})

(defn checked?
  "Returns true if source, a program file for spec, passes spec's hidden checks."
  [spec source]
  (every? :ok (run-tests source (get checks (first (:names (extract spec [])))))))

(defn- run-spec
  "Solves spec with a fresh policy and prints one line. Returns {:spec :passed :checked
  :calls :seconds :tested :edit :program}: passed on the spec's tests, checked on the
  hidden ones too; tested counts the programs local repair ran."
  [spec mock flow]
  (let [calls (atom 0)
        inner (if mock (mock-policy) (jev/client (api-key)))
        policy (fn [state questions]
                 (swap! calls inc)
                 (inner state questions))
        start (System/nanoTime)
        ;; a crash is a failed spec; keep benchmarking
        out (try
              (let [out (g/solve spec policy :flow flow)
                    source (g/source (g/best out))
                    passed (g/passed? out)]
                {:passed passed :checked (and passed (checked? spec source))
                 :tested (:tested out) :edit (:edit (g/best out))
                 :program (str/join " " (drop 2 (str/split (str/trim source) #"\s+")))})
              (catch Exception e
                {:passed false :tested 0 :program (str "crash: " (ex-message e))}))
        seconds (/ (- (System/nanoTime) start) 1e9)]
    ;; OVER: passes the spec's tests only
    (println (format "%s %3d calls %5.1fs %6d local  %s"
                     (cond (:checked out) "PASS" (:passed out) "OVER" :else "FAIL") @calls
                     seconds (:tested out) (:program out)))
    (flush)
    (assoc out :spec spec :calls @calls :seconds seconds)))

(defn- median [xs]
  (let [v (vec (sort xs))
        n (count v)]
    (/ (+ (v (quot (dec n) 2)) (v (quot n 2))) 2.0)))

(defn- stats [label xs]
  (format "%s median %.1f, range %d-%d" label (median xs) (apply min xs) (apply max xs)))

(defn- save!
  "Writes the runs to bench/results/<time>.edn, so later changes compare without a rerun."
  [set-name flow mock runs]
  (let [now (LocalDateTime/now)
        f (io/file "bench" "results" (str (.format now (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH-mm-ss"))
                                          "-" set-name ".edn"))]
    (io/make-parents f)
    (spit f (with-out-str (pprint {:date (str now) :set set-name :flow flow :mock (boolean mock)
                                   :runs runs})))
    (println "saved" (.getPath f))))

(defn -main [& args]
  (let [argv (set args)
        runs (or (some (fn [[a b]] (when (= a "--runs") (parse-long b))) (partition 2 1 args)) 1)
        set-name (or (some argv ["hard" "basic" "held"]) "basic+hard")
        specs (case set-name "hard" hard "basic" basic "held" held (concat basic hard))
        flow (if (argv "pick") :pick :score)
        results (vec (for [r (range runs)]
                       (do (when (> runs 1) (println (str "\nrun " (inc r) " of " runs)))
                           (let [rs (mapv #(run-spec % (argv "--mock") flow) specs)]
                             (println (str "\n" (count (filter :checked rs)) "/" (count specs) " passed, "
                                           (count (remove :checked (filter :passed rs)))
                                           " more only fit the spec's tests, "
                                           (reduce + (map :calls rs)) " API calls"))
                             rs))))]
    (when (> runs 1)
      (println (stats "passed:" (map #(count (filter :checked %)) results)))
      (println (stats "API calls:" (map #(reduce + (map :calls %)) results))))
    (save! set-name flow (argv "--mock") results)
    (shutdown-agents)))
