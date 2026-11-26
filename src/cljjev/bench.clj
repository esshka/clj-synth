(ns cljjev.bench
  "Benchmark: clojure -M:bench [--mock|flat] [pick] [sure] [plan] [--runs N]
  [basic|hard|held|split|whole] runs
  a spec set N times and reports the pass rate and API calls of each run, their median and
  range when N > 1, and writes the runs to bench/results/. pick runs the :pick search flow.
  A program passes only if it also passes hidden checks the search never sees. split solves
  hard functions as parts and then a main function; whole solves the same mains alone.
  flat runs with no model: every option has the same probability, and the set is saved as
  <set>-flat. sure adds Jev's sure answers to each spec's tests and saves the set as <set>-sure.
  plan lets Jev pick the wording of the first step and saves the set as <set>-plan. Every run, and clojure -M:bench report alone, writes BENCHMARKS.md again."
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [cljjev.cli :refer [api-key]]
            [cljjev.env :refer [extract]]
            [cljjev.generate :as g]
            [cljjev.jev :as jev]
            [cljjev.library :as library]
            [cljjev.oracle :as oracle :refer [second-look shrink-look sure-tests]]
            [cljjev.policy :refer [flat-policy mock-policy]]
            [cljjev.report :as report]
            [cljjev.validate :refer [run-tests]]
            [cljjev.wording :as wording])
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

(defn- part [name]
  (first (filter #(str/includes? % (str "(" name " ")) (concat basic hard))))

;; a hard function divided by hand: each spec may call the ones before it, the last is the main
(def split
  [[(part "prime?")
    (str "sum of the prime numbers below n "
         "(= 17 (prime-sum 10)) (= 0 (prime-sum 2)) (= 5 (prime-sum 5)) (= 28 (prime-sum 12))")]
   [(part "gcd")
    "least common multiple of a and b (= 12 (lcm 4 6)) (= 15 (lcm 3 5)) (= 6 (lcm 6 6))"]
   [(part "digit-sum")
    (str "add the digits of n again and again until one digit is left (= 2 (digital-root 38)) "
         "(= 7 (digital-root 7)) (= 9 (digital-root 99)) (= 1 (digital-root 10))")]
   [(part "rev")
    (str "is xs the same when read backwards (= true (palindrome? [1 2 1])) "
         "(= false (palindrome? [1 2])) (= true (palindrome? [])) (= false (palindrome? [1 2 2]))")]
   [(part "factorial")
    (str "number of ways to choose k items out of n "
         "(= 10 (choose 5 2)) (= 1 (choose 4 0)) (= 20 (choose 6 3)) (= 4 (choose 4 3))")]
   [(part "fib")
    (str "sum of the fibonacci numbers from the 0th up to the nth "
         "(= 20 (fib-sum 6)) (= 0 (fib-sum 0)) (= 1 (fib-sum 1)) (= 7 (fib-sum 4))")]
   [(part "collatz")
    (str "largest number of collatz steps for any start from 1 up to n "
         "(= 16 (longest 7)) (= 0 (longest 1)) (= 7 (longest 3)) (= 8 (longest 6))")]
   [(str "does d divide n with no remainder (= true (divides? 3 12)) (= false (divides? 5 12)) "
         "(= true (divides? 1 7)) (= false (divides? 4 6))")
    (str "sum of the numbers below n that divide n (= 6 (divisor-sum 6)) (= 1 (divisor-sum 7)) "
         "(= 16 (divisor-sum 12)) (= 0 (divisor-sum 1))")
    (str "is n equal to the sum of its smaller divisors (= true (perfect? 6)) "
         "(= false (perfect? 12)) (= true (perfect? 28)) (= false (perfect? 7))")]])

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
   "spread" ["(= 9 (spread [10 1]))" "(= 6 (spread [-3 3 0]))"]
   "prime-sum" ["(= 77 (prime-sum 20))" "(= 2 (prime-sum 3))" "(= 0 (prime-sum 0))"]
   "lcm" ["(= 30 (lcm 10 15))" "(= 7 (lcm 1 7))" "(= 24 (lcm 8 12))"]
   "digital-root" ["(= 6 (digital-root 123))" "(= 0 (digital-root 0))" "(= 1 (digital-root 991))"]
   "palindrome?" ["(= true (palindrome? [7]))" "(= true (palindrome? [1 2 2 1]))"
                  "(= false (palindrome? [1 2 3 1]))"]
   "choose" ["(= 1 (choose 3 3))" "(= 6 (choose 4 2))" "(= 7 (choose 7 1))"]
   "fib-sum" ["(= 2 (fib-sum 2))" "(= 12 (fib-sum 5))" "(= 33 (fib-sum 7))"]
   "longest" ["(= 1 (longest 2))" "(= 7 (longest 5))" "(= 19 (longest 9))"]
   "divides?" ["(= true (divides? 2 2))" "(= false (divides? 2 9))" "(= true (divides? 7 21))"]
   "divisor-sum" ["(= 28 (divisor-sum 28))" "(= 7 (divisor-sum 8))" "(= 1 (divisor-sum 2))"]
   "perfect?" ["(= false (perfect? 8))" "(= false (perfect? 1))" "(= true (perfect? 496))"]})

(defn checked?
  "Returns true if source, a program file for spec, passes spec's hidden checks."
  [spec source]
  (every? :ok (run-tests source (get checks (first (:names (extract spec [])))))))

(defn- hidden-call?
  "Returns a predicate: is this call of spec's function in one of its hidden checks?"
  [spec]
  (let [hidden (str/join " " (get checks (first (:names (extract spec [])))))]
    #(str/includes? hidden %)))

(defn without-checks
  "Returns the tests of asked, Jev's sure answers for spec, whose call is in no hidden check.
  Jev's fixed inputs hold check inputs, and a check the search has seen is not hidden."
  [spec asked]
  (vec (remove #((hidden-call? spec) (second (re-find #"^\(= \S+ (.+)\)$" %))) asked)))

(defn- run-spec
  "Solves spec with a fresh policy and the functions of solved on offer, and prints one
  line, in lower case for a part. sure is nil, :ask to add Jev's sure answers to the tests,
  or the answers of an earlier search. Returns {:spec :passed :checked :calls :seconds
  :tested :edit :program :asked}: passed on the tests, checked on the hidden ones too;
  tested counts the programs local repair ran, asked holds the tests from Jev."
  [spec make-policy flow sure plan? solved part?]
  (let [calls (atom 0)
        inner (make-policy)
        policy (fn [state questions]
                 (swap! calls inc)
                 (inner state questions))
        start (System/nanoTime)
        ;; a crash is a failed spec; keep benchmarking
        out (try
              (let [asked (cond (vector? sure) sure
                                sure (without-checks spec (sure-tests spec policy))
                                :else [])
                    questions (if plan? (wording/questions spec policy solved) {})
                    sources (apply str (map :source solved))
                    out (cond-> (second-look (library/solve spec policy solved :flow flow :tests asked
                                                            :questions questions)
                                             spec asked policy)
                          sure (shrink-look spec policy sources
                                            (remove (hidden-call? spec) (oracle/calls spec))))
                    source (g/source (g/best out))
                    passed (g/passed? out)]
                {:passed passed :asked asked :dropped (:dropped out)
                 :wording (when (seq questions) "last step")
                 :uncut (:uncut out)
                 :checked (and passed (checked? spec (str sources source)))
                 :tested (:tested out) :edit (:edit (g/best out))
                 :program (str/join " " (drop 2 (str/split (str/trim source) #"\s+")))})
              (catch Exception e
                {:passed false :tested 0 :program (str "crash: " (ex-message e))}))
        seconds (/ (- (System/nanoTime) start) 1e9)
        ;; OVER: passes the spec's tests only
        status (cond (:checked out) "PASS" (:passed out) "OVER" :else "FAIL")]
    (println (format "%s %3d calls %5.1fs %6d local  %s"
                     (cond-> status part? str/lower-case) @calls seconds (:tested out)
                     (:program out)))
    (flush)
    (assoc out :spec spec :calls @calls :seconds seconds)))

(defn- run-chain
  "Solves the specs of chain in turn; a part joins the options of the later ones when it
  passes its own tests, and a part that fails is searched once more, both as on the command
  line. Returns the main's result with the calls, seconds and local programs of the whole
  chain, and its parts' results in :parts; :attempts 2 marks a part that was searched twice."
  [chain make-policy flow sure? plan?]
  (let [done (reduce (fn [done spec]
                       (let [part? (not= spec (peek chain))
                             solve #(run-spec spec make-policy flow % plan?
                                              (for [r done :when (:passed r)]
                                                (library/entry (:spec r) {:code (:program r)}))
                                              part?)
                             r (solve (when sure? :ask))]
                         (conj done (if (and part? (not (:passed r)))
                                      (assoc (merge-with + (solve (when sure? (or (:asked r) :ask)))
                                                         (select-keys r [:calls :seconds :tested]))
                                             :attempts 2)
                                      r))))
                     []
                     chain)
        total (fn [k] (reduce + (map k done)))]
    (assoc (peek done) :parts (pop done)
           :calls (total :calls) :seconds (total :seconds) :tested (total :tested))))

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

(defn- run-set [args]
  (let [argv (set args)
        runs (or (some (fn [[a b]] (when (= a "--runs") (parse-long b))) (partition 2 1 args)) 1)
        set-name (or (some argv ["hard" "basic" "held" "split" "whole"]) "basic+hard")
        specs (case set-name
                "split" split
                "whole" (map (comp vector peek) split)
                (map vector (case set-name
                              "hard" hard "basic" basic "held" held (concat basic hard))))
        flow (if (argv "pick") :pick :score)
        make-policy (cond (argv "--mock") mock-policy
                          (argv "flat") flat-policy
                          :else #(jev/client (api-key)))
        results (vec (for [r (range runs)]
                       (do (when (> runs 1) (println (str "\nrun " (inc r) " of " runs)))
                           (let [rs (mapv #(run-chain % make-policy flow (argv "sure") (argv "plan")) specs)]
                             (println (str "\n" (count (filter :checked rs)) "/" (count specs) " passed, "
                                           (count (remove :checked (filter :passed rs)))
                                           " more only fit the spec's tests, "
                                           (reduce + (map :calls rs)) " API calls"))
                             rs))))]
    (when (> runs 1)
      (println (stats "passed:" (map #(count (filter :checked %)) results)))
      (println (stats "API calls:" (map #(reduce + (map :calls %)) results))))
    (save! (str set-name (when (argv "flat") "-flat") (when (argv "sure") "-sure")
                (when (argv "plan") "-plan"))
           flow
           (argv "--mock") results)))

(defn -main [& args]
  (when-not (some #{"report"} args)
    (run-set args))
  (report/write!)
  (shutdown-agents))
