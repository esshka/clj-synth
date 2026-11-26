# Benchmarks

Written by `clojure -M:bench report`, and after every benchmark run, from the files in `bench/results/`. Do not edit it by hand.

PASS: the program passes the spec's tests and hidden checks the search never sees. OVER: it passes only the spec's tests. FAIL: it fails a test of the spec. Model answers change between runs, so one run is a sample, not a score.

## Hard functions: after their parts and alone

Each main function is solved after its parts, which are then options for it (`split`, 2026-11-26 11:14, flow :score), and alone (`whole`, 2026-11-26 10:58, flow :score). The API calls of a main function include the calls of its parts.

| Function | Parts | After its parts | API calls | Alone | API calls |
| --- | --- | --- | --- | --- | --- |
| `prime-sum` | `prime?` | FAIL | 461 | FAIL | 136 |
| `lcm` | `gcd` | PASS | 21 | FAIL | 97 |
| `digital-root` | `digit-sum` | PASS | 27 | PASS | 62 |
| `palindrome?` | `rev` | PASS | 15 | PASS | 22 |
| `choose` | `factorial` | FAIL | 88 | FAIL | 82 |
| `fib-sum` | `fib` | PASS | 36 | PASS | 20 |
| `longest` | `collatz` | OVER | 35 | FAIL | 85 |
| `perfect?` | `divides?`, `divisor-sum` | PASS | 24 | PASS | 24 |

## Latest run of each set

### `split`, 2026-11-26 11:14, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `prime?` (part) | OVER in 2 searches | 370 | `(defn prime? [n] (cond (<= n 1) false (= 2 n) true :else (cond (even? n) false (loop [i 3] (cond (zero? (mod n i)) false i (cond false false false false :else true) :else true)) true :else false)))` |
| `prime-sum` | FAIL | 461 | `(defn prime-sum [n] (+ (reduce + 0 (filter prime? (range 2 n))) 3))` |
| `gcd` (part) | PASS | 10 | `(defn gcd [a b] (if (zero? b) a (gcd b (mod a b))))` |
| `lcm` | PASS | 21 | `(defn lcm [a b] (/ (* a b) (gcd a b)))` |
| `digit-sum` (part) | PASS | 18 | `(defn digit-sum [n] (loop [x n acc 0] (if (zero? x) acc (recur (quot x 10) (+ (mod x 10) acc)))))` |
| `digital-root` | PASS | 27 | `(defn digital-root [n] (if (< n 10) n (digital-root (digit-sum n))))` |
| `rev` (part) | PASS | 10 | `(defn rev [coll] (reduce (fn [acc x] (cons x acc)) [] coll))` |
| `palindrome?` | PASS | 15 | `(defn palindrome? [xs] (= (rev xs) xs))` |
| `factorial` (part) | PASS | 10 | `(defn factorial [n] (if (zero? n) 1 (* n (factorial (dec n)))))` |
| `choose` | FAIL | 88 | `(defn choose [n k] (if (zero? k) 1 (choose (dec n) (dec k))))` |
| `fib` (part) | PASS | 17 | `(defn fib [n] (if (< n 2) n (+ (fib (- n 1)) (fib (- n 2)))))` |
| `fib-sum` | PASS | 36 | `(defn fib-sum [n] (loop [sum 0 up 1 nth n] (if (zero? nth) sum (recur (+ sum up) (inc sum) (dec nth)))))` |
| `collatz` (part) | PASS | 24 | `(defn collatz [n] (loop [x n steps 0] (if (= 1 x) steps (recur (if (even? x) (/ x 2) (+ 1 (* x 3))) (+ 1 steps)))))` |
| `longest` | OVER | 35 | `(defn longest [n] (collatz n))` |
| `divides?` (part) | PASS | 5 | `(defn divides? [d n] (zero? (mod n d)))` |
| `divisor-sum` (part) | PASS | 14 | `(defn divisor-sum [n] (reduce + 0 (filter (fn [x] (divides? x n)) (range 1 n))))` |
| `perfect?` | PASS | 24 | `(defn perfect? [n] (= (divisor-sum n) n))` |

### `whole`, 2026-11-26 10:58, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `prime-sum` | FAIL | 136 | `(defn prime-sum [n] (reduce + 0 (filter (fn [x] (< x n)) (range 2 n))))` |
| `lcm` | FAIL | 97 | `(defn lcm [a b] (* a b))` |
| `digital-root` | PASS | 62 | `(defn digital-root [n] (loop [x n] (if (<= 10 x) (recur (+ (quot x 10) (mod x 10))) x)))` |
| `palindrome?` | PASS | 22 | `(defn palindrome? [xs] (= xs (reduce (fn [acc x] (cons x acc)) [] xs)))` |
| `choose` | FAIL | 82 | `(defn choose [n k] (if (zero? k) 1 (choose (- n 1) (dec k))))` |
| `fib-sum` | PASS | 20 | `(defn fib-sum [n] (loop [sum 0 up 1 nth n] (if (zero? nth) sum (recur (+ sum up) (inc sum) (dec nth)))))` |
| `longest` | FAIL | 85 | `(defn longest [n] (max 0 (loop [largest 0 x (+ 1 n)] (if (<= x n) (max largest (longest x)) largest))))` |
| `perfect?` | PASS | 24 | `(defn perfect? [n] (= n (reduce + 0 (filter (fn [x] (= (mod n x) 0)) (range 1 n)))))` |

### `basic`, 2026-10-21 22:20, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `factorial` | PASS, PASS | 10, 10 | `(defn factorial [n] (if (zero? n) 1 (* n (factorial (dec n)))))` |
| `max2` | PASS, PASS | 4, 4 | `(defn max2 [a b] (max a b))` |
| `sum-list` | PASS, PASS | 5, 5 | `(defn sum-list [xs] (reduce + 0 xs))` |
| `squares` | PASS, PASS | 8, 8 | `(defn squares [xs] (map (fn [x] (* x x)) xs))` |
| `size` | PASS, PASS | 9, 9 | `(defn size [coll] (if (empty? coll) 0 (+ (size (rest coll)) 1)))` |
| `fib` | PASS, PASS | 16, 20 | `(defn fib [n] (if (< 2 n) (+ (fib (- n 2)) (fib (- n 1))) (min 1 n)))` |
| `positives` | PASS, PASS | 4, 4 | `(defn positives [xs] (filter pos? xs))` |
| `sum-squares` | PASS, PASS | 11, 11 | `(defn sum-squares [xs] (reduce (fn [acc x] (+ acc (* x x))) 0 xs))` |
| `power` | OVER, OVER | 18, 18 | `(defn power [b e] (if (zero? e) 1 (* (if (even? e) (power b (quot e 2)) (power b (dec e))) b)))` |
| `abs-val` | PASS, PASS | 9, 9 | `(defn abs-val [x] (if (neg? x) (- 0 x) x))` |
| `rev` | PASS, PASS | 9, 10 | `(defn rev [coll] (reduce (fn [acc x] (cons x acc)) [] coll))` |
| `final` | PASS, PASS | 9, 9 | `(defn final [xs] (if (empty? xs) nil (or (final (rest xs)) (first xs))))` |
| `sign` | PASS, PASS | 9, 9 | `(defn sign [x] (cond (neg? x) -1 (zero? x) 0 :else 1))` |
| `first-or-zero` | PASS, PASS | 7, 7 | `(defn first-or-zero [xs] (if-let [x (first xs)] x 0))` |
| `clamp` | PASS, PASS | 12, 5 | `(defn clamp [x lo hi] (max (min hi x) lo))` |

### `hard`, 2026-10-21 22:14, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `gcd` | PASS, PASS | 10, 10 | `(defn gcd [a b] (if (zero? b) a (gcd b (mod a b))))` |
| `largest` | OVER, OVER | 24, 24 | `(defn largest [xs] (max (first (rest xs)) (reduce max (first xs) (rest xs))))` |
| `count-evens` | PASS, PASS | 12, 12 | `(defn count-evens [xs] (reduce (fn [acc x] (if (even? x) (inc acc) acc)) 0 xs))` |
| `occurrences` | PASS, PASS | 13, 13 | `(defn occurrences [x xs] (reduce (fn [acc n] (if (= x n) (inc acc) acc)) 0 xs))` |
| `upto` | PASS, PASS | 4, 4 | `(defn upto [n] (range 0 n))` |
| `take-n` | FAIL, PASS | 112, 15 | `(defn take-n [n xs] (if (and (> n 0) (not (empty? xs))) (cons (first xs) (take-n (dec n) (rest xs))) []))` |
| `average` | PASS, PASS | 8, 8 | `(defn average [xs] (/ (reduce + 0 xs) (count xs)))` |
| `count-above` | PASS, PASS | 23, 23 | `(defn count-above [t xs] (reduce (fn [acc x] (if (> x t) (+ 1 acc) acc)) 0 xs))` |
| `dot` | OVER, FAIL | 59, 65 | `(defn dot [xs ys] (reduce (fn [acc x] (+ acc (* x (first ys)))) 0 xs))` |
| `item-at` | PASS, PASS | 25, 24 | `(defn item-at [xs n] (if (zero? n) (first xs) (item-at (rest xs) (dec n))))` |
| `digit-sum` | PASS, PASS | 18, 18 | `(defn digit-sum [n] (loop [x n acc 0] (if (zero? x) acc (recur (quot x 10) (+ (mod x 10) acc)))))` |
| `prime?` | PASS, FAIL | 33, 136 | `(defn prime? [n] (if (<= n 1) false (if (or (even? n) (loop [a 3 i 3] (if (<= n a) true (loop [x a] (if x false true))))) false (if (zero? (mod n 3)) false true))))` |
| `collatz` | PASS, PASS | 24, 24 | `(defn collatz [n] (loop [x n steps 0] (if (= 1 x) steps (recur (if (even? x) (/ x 2) (+ (* 3 x) 1)) (+ steps 1)))))` |
| `sum-odd` | PASS, PASS | 13, 13 | `(defn sum-odd [xs] (reduce (fn [acc x] (if (odd? x) (+ acc x) acc)) 0 xs))` |

### `held`, 2026-10-21 22:18, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `product` | PASS, PASS | 5, 5 | `(defn product [xs] (reduce * 1 xs))` |
| `smallest` | PASS, PASS | 16, 42 | `(defn smallest [xs] (reduce min (first xs) (rest xs)))` |
| `count-neg` | PASS, PASS | 12, 12 | `(defn count-neg [xs] (reduce (fn [acc x] (if (neg? x) (inc acc) acc)) 0 xs))` |
| `sum-to` | PASS, PASS | 8, 8 | `(defn sum-to [n] (reduce + 0 (range 1 (inc n))))` |
| `doubled` | PASS, PASS | 8, 8 | `(defn doubled [xs] (map (fn [x] (* 2 x)) xs))` |
| `all-even?` | PASS, PASS | 4, 4 | `(defn all-even? [xs] (every? even? xs))` |
| `digits` | PASS, PASS | 5, 5 | `(defn digits [n] (count (str n nil)))` |
| `drop-n` | PASS, PASS | 30, 14 | `(defn drop-n [n xs] (if (zero? n) xs (drop-n (dec n) (rest xs))))` |
| `no-zeros` | PASS, PASS | 8, 8 | `(defn no-zeros [xs] (filter (fn [x] (not (zero? x))) xs))` |
| `square-sum` | PASS, PASS | 18, 18 | `(defn square-sum [n] (reduce (fn [acc x] (+ acc x)) 0 (map (fn [x] (* x x)) (range 1 (inc n)))))` |
| `second-item` | PASS, PASS | 4, 4 | `(defn second-item [xs] (first (rest xs)))` |
| `spread` | FAIL, FAIL | 257, 197 | `(defn spread [xs] (- (max (first xs) (max (max (max (max (max 0 0) (max 0 0)) (max (max 0 0) (max 0 0))) (max 0 (max (max 0 0) (max 0 0)))) (max (max 0 (max 0 (max 0 0))) (max 0 (max 0 0))))) (first xs)))` |

## All runs

| Date | Set | Flow | PASS | OVER | FAIL | API calls |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-11-26 11:14 | `split` | :score | 5 | 1 | 2 | 707 |
| 2026-11-26 10:58 | `whole` | :score | 4 | 0 | 4 | 528 |
| 2026-11-26 10:53 | `split` | :score | 4 | 1 | 3 | 558 |
| 2026-10-21 22:20 | `basic` | :score | 14 | 1 | 0 | 140 |
| 2026-10-21 22:20 | `basic` | :score | 14 | 1 | 0 | 138 |
| 2026-10-21 22:18 | `held` | :score | 11 | 0 | 1 | 375 |
| 2026-10-21 22:18 | `held` | :score | 11 | 0 | 1 | 325 |
| 2026-10-21 22:14 | `hard` | :score | 11 | 2 | 1 | 378 |
| 2026-10-21 22:14 | `hard` | :score | 11 | 1 | 2 | 389 |

