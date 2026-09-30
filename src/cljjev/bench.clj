(ns cljjev.bench
  "Benchmark: clojure -M:bench [--mock] [pick] [basic|hard|held] runs a spec set and reports
  the pass rate and API calls. pick runs the :pick search flow."
  (:require [clojure.string :as str]
            [cljjev.cli :refer [api-key]]
            [cljjev.generate :as g]
            [cljjev.jev :as jev]
            [cljjev.policy :refer [mock-policy]]))

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

(defn- run-spec
  "Solves spec with a fresh policy and prints one line. Returns [passed? api-calls]."
  [spec mock flow]
  (let [calls (atom 0)
        inner (if mock (mock-policy) (jev/client (api-key)))
        policy (fn [state questions]
                 (swap! calls inc)
                 (inner state questions))
        start (System/nanoTime)
        ;; a crash is a failed spec; keep benchmarking
        [ok code] (try
                    (let [out (g/solve spec policy :flow flow)
                          code (str/split (str/trim (g/source (g/best out))) #"\s+")]
                      [(g/passed? out) (str/join " " (drop 2 code))])
                    (catch Exception e
                      [false (str "crash: " (ex-message e))]))]
    (println (format "%s %3d calls %5.1fs  %s" (if ok "PASS" "FAIL") @calls
                     (/ (- (System/nanoTime) start) 1e9) code))
    (flush)
    [ok @calls]))

(defn -main [& args]
  (let [argv (set args)
        specs (cond (argv "hard") hard
                    (argv "basic") basic
                    (argv "held") held
                    :else (concat basic hard))
        flow (if (argv "pick") :pick :score)
        results (mapv #(run-spec % (argv "--mock") flow) specs)]
    (println (str "\n" (count (filter first results)) "/" (count specs) " passed, "
                  (reduce + (map second results)) " API calls"))
    (shutdown-agents)))
