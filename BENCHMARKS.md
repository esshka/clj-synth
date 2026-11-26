# Benchmarks

Written by `clojure -M:bench report`, and after every benchmark run, from the files in `bench/results/`. Do not edit it by hand.

PASS: the program passes the spec's tests and hidden checks the search never sees. OVER: it passes only the spec's tests. FAIL: it fails a test of the spec. Model answers change between runs, so one run is a sample, not a score. A set named `<set>-flat` ran with no model: every option had the same probability, and its API calls are questions that code answered.

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

### `basic-flat`, 2026-11-26 11:41, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `factorial` | FAIL | 40 | `(defn factorial [a] 0)` |
| `max2` | PASS | 3 | `(defn max2 [larger a] (max a larger))` |
| `sum-list` | OVER | 3 | `(defn sum-list [sum] (* (count sum) 2))` |
| `squares` | FAIL | 40 | `(defn squares [square] 0)` |
| `size` | PASS | 3 | `(defn size [items] (count items))` |
| `fib` | FAIL | 40 | `(defn fib [nth] (+ 0 nth))` |
| `positives` | PASS | 3 | `(defn positives [keep] (filter pos? keep))` |
| `sum-squares` | FAIL | 40 | `(defn sum-squares [sum] 0)` |
| `power` | FAIL | 40 | `(defn power [b raised] 0)` |
| `abs-val` | OVER | 3 | `(defn abs-val [absolute] (max 3 absolute))` |
| `rev` | FAIL | 40 | `(defn rev [reverse] 0)` |
| `final` | FAIL | 40 | `(defn final [last] 0)` |
| `sign` | OVER | 3 | `(defn sign [x] (quot x 3))` |
| `first-or-zero` | OVER | 3 | `(defn first-or-zero [item] (* (count item) 2))` |
| `clamp` | FAIL | 40 | `(defn clamp [x between lo] 0)` |

### `basic-sure`, 2026-11-26 12:29, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `factorial` | PASS | 11 | `(defn factorial [n] (if (zero? n) 1 (* (factorial (dec n)) n)))` |
| `max2` | PASS | 5 | `(defn max2 [a b] (max a b))` |
| `sum-list` | PASS | 6 | `(defn sum-list [xs] (reduce + 0 xs))` |
| `squares` | PASS | 8 | `(defn squares [xs] (map (fn [x] (* x x)) xs))` |
| `size` | PASS | 11 | `(defn size [coll] (if (empty? coll) 0 (+ 1 (size (rest coll)))))` |
| `fib` | PASS | 16 | `(defn fib [n] (if (<= n 1) n (+ (fib (- n 2)) (fib (dec n)))))` |
| `positives` | PASS | 4 | `(defn positives [xs] (filter pos? xs))` |
| `sum-squares` | PASS | 16 | `(defn sum-squares [xs] (reduce (fn [acc x] (+ acc x)) 0 (map (fn [x] (* x x)) xs)))` |
| `power` | PASS | 21 | `(defn power [b e] (loop [i e acc 1] (if (zero? i) acc (recur (dec i) (* b acc)))))` |
| `abs-val` | PASS | 9 | `(defn abs-val [x] (if (neg? x) (- 0 x) x))` |
| `rev` | PASS | 10 | `(defn rev [coll] (reduce (fn [acc x] (cons x acc)) [] coll))` |
| `final` | PASS | 10 | `(defn final [xs] (if (empty? xs) nil (or (final (rest xs)) (first xs))))` |
| `sign` | PASS | 10 | `(defn sign [x] (cond (neg? x) -1 (zero? x) 0 :else 1))` |
| `first-or-zero` | PASS | 8 | `(defn first-or-zero [xs] (if-let [x (first xs)] x 0))` |
| `clamp` | PASS | 7 | `(defn clamp [x lo hi] (min (max x lo) hi))` |

### `hard-flat`, 2026-11-26 11:41, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `gcd` | FAIL | 40 | `(defn gcd [greatest common] 0)` |
| `largest` | OVER | 3 | `(defn largest [number] (first (rest number)))` |
| `count-evens` | OVER | 3 | `(defn count-evens [even] (count (rest (rest even))))` |
| `occurrences` | OVER | 3 | `(defn occurrences [how many] (dec (first many)))` |
| `upto` | PASS | 3 | `(defn upto [numbers] (range 0 numbers))` |
| `take-n` | FAIL | 40 | `(defn take-n [n items] 0)` |
| `average` | FAIL | 40 | `(defn average [numbers] 0)` |
| `count-above` | OVER | 3 | `(defn count-above [how many] (* (quot 3 how) 2))` |
| `dot` | FAIL | 40 | `(defn dot [product xs] 0)` |
| `item-at` | OVER | 3 | `(defn item-at [item index] (+ 10 (* index 10)))` |
| `digit-sum` | OVER | 3 | `(defn digit-sum [sum] (mod sum (+ 10 3)))` |
| `prime?` | FAIL | 40 | `(defn prime? [n] (and true true))` |
| `collatz` | FAIL | 40 | `(defn collatz [number] 0)` |
| `sum-odd` | OVER | 3 | `(defn sum-odd [sum] (* (dec (count sum)) 2))` |

### `hard-sure`, 2026-11-26 12:32, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `gcd` | PASS | 11 | `(defn gcd [a b] (if (zero? b) a (gcd b (mod a b))))` |
| `largest` | PASS | 15 | `(defn largest [xs] (reduce max (first xs) xs))` |
| `count-evens` | PASS | 13 | `(defn count-evens [xs] (reduce (fn [acc x] (if (even? x) (inc acc) acc)) 0 xs))` |
| `occurrences` | PASS | 14 | `(defn occurrences [x xs] (reduce (fn [acc i] (if (= x i) (inc acc) acc)) 0 xs))` |
| `upto` | PASS | 4 | `(defn upto [n] (range 0 n))` |
| `take-n` | PASS | 41 | `(defn take-n [n xs] (if (and (> n 0) (not (empty? xs))) (cons (first xs) (take-n (dec n) (rest xs))) []))` |
| `average` | PASS | 9 | `(defn average [xs] (/ (reduce + 0 xs) (count xs)))` |
| `count-above` | PASS | 15 | `(defn count-above [t xs] (reduce (fn [acc x] (if (> x t) (+ 1 acc) acc)) 0 xs))` |
| `dot` | PASS | 63 | `(defn dot [xs ys] (reduce (fn [acc x] (+ acc x)) 0 (map * xs ys)))` |
| `item-at` | PASS | 37 | `(defn item-at [xs n] (if (zero? n) (first xs) (item-at (rest xs) (dec n))))` |
| `digit-sum` | PASS | 19 | `(defn digit-sum [n] (loop [x n acc 0] (if (zero? x) acc (recur (quot x 10) (+ (mod x 10) acc)))))` |
| `prime?` | FAIL | 231 | `(defn prime? [n] (cond (<= n 1) false (even? n) (= 2 n) :else (cond (< n 2) true (< n 2) true :else true)))` |
| `collatz` | PASS | 25 | `(defn collatz [n] (loop [x n steps 0] (if (= 1 x) steps (recur (if (even? x) (/ x 2) (+ (* 3 x) 1)) (+ 1 steps)))))` |
| `sum-odd` | PASS | 14 | `(defn sum-odd [xs] (reduce (fn [acc x] (if (odd? x) (+ acc x) acc)) 0 xs))` |

### `held-flat`, 2026-11-26 11:42, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `product` | FAIL | 40 | `(defn product [numbers] 0)` |
| `smallest` | FAIL | 40 | `(defn smallest [number] 0)` |
| `count-neg` | OVER | 3 | `(defn count-neg [negative] (count (rest negative)))` |
| `sum-to` | FAIL | 40 | `(defn sum-to [sum] (+ 0 sum))` |
| `doubled` | FAIL | 40 | `(defn doubled [double] 0)` |
| `all-even?` | PASS | 3 | `(defn all-even? [every] (every? even? every))` |
| `digits` | FAIL | 40 | `(defn digits [number] 0)` |
| `drop-n` | FAIL | 40 | `(defn drop-n [drop n] 0)` |
| `no-zeros` | PASS | 3 | `(defn no-zeros [remove] (filter pos? remove))` |
| `square-sum` | FAIL | 40 | `(defn square-sum [sum] 0)` |
| `second-item` | OVER | 3 | `(defn second-item [second] (inc (first second)))` |
| `spread` | OVER | 3 | `(defn spread [largest] (* (count (rest largest)) 2))` |

### `held-sure`, 2026-11-26 12:33, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `product` | PASS | 6 | `(defn product [xs] (reduce * 1 xs))` |
| `smallest` | PASS | 34 | `(defn smallest [xs] (reduce min (first xs) xs))` |
| `count-neg` | PASS | 13 | `(defn count-neg [xs] (reduce (fn [acc x] (if (neg? x) (inc acc) acc)) 0 xs))` |
| `sum-to` | PASS | 9 | `(defn sum-to [n] (reduce + 0 (range 1 (inc n))))` |
| `doubled` | PASS | 8 | `(defn doubled [xs] (map (fn [x] (* 2 x)) xs))` |
| `all-even?` | PASS | 5 | `(defn all-even? [xs] (every? even? xs))` |
| `digits` | PASS | 6 | `(defn digits [n] (count (str n nil)))` |
| `drop-n` | PASS | 31 | `(defn drop-n [n xs] (if (> n 0) (rest (drop-n (dec n) xs)) xs))` |
| `no-zeros` | PASS | 8 | `(defn no-zeros [xs] (filter (fn [x] (not (zero? x))) xs))` |
| `square-sum` | PASS | 118 | `(defn square-sum [n] (reduce + 0 (map (fn [x] (* x x)) (range 1 (inc n)))))` |
| `second-item` | PASS | 5 | `(defn second-item [xs] (first (rest xs)))` |
| `spread` | FAIL | 183 | `(defn spread [xs] (- (max (first xs) (max (max (max (max (first xs) (first xs)) (first (rest xs))) (first (rest xs))) (first (rest xs)))) (min (first xs) (first (rest xs)))))` |

### `split-sure`, 2026-11-26 12:37, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `prime?` (part) | FAIL in 2 searches | 364 | `(defn prime? [n] (cond (<= n 1) false (even? n) false :else (cond (< 2 n) (cond (zero? (mod n 3)) false (zero? (mod n 3)) false :else true) (< n 2) false :else true)))` |
| `prime-sum` | FAIL | 504 | `(defn prime-sum [n] (reduce + 0 (filter (fn [x] (< x n)) (range 2 n))))` |
| `gcd` (part) | PASS | 11 | `(defn gcd [a b] (if (zero? b) a (gcd b (mod a b))))` |
| `lcm` | PASS | 17 | `(defn lcm [a b] (quot (* a b) (gcd a b)))` |
| `digit-sum` (part) | PASS | 19 | `(defn digit-sum [n] (loop [x n acc 0] (if (zero? x) acc (recur (quot x 10) (+ (mod x 10) acc)))))` |
| `digital-root` | PASS | 30 | `(defn digital-root [n] (if (< n 10) n (digital-root (digit-sum n))))` |
| `rev` (part) | PASS | 10 | `(defn rev [coll] (reduce (fn [acc x] (cons x acc)) [] coll))` |
| `palindrome?` | PASS | 16 | `(defn palindrome? [xs] (= (rev xs) xs))` |
| `factorial` (part) | PASS | 11 | `(defn factorial [n] (if (zero? n) 1 (* (factorial (dec n)) n)))` |
| `choose` | FAIL | 94 | `(defn choose [n k] (if (< n k) 0 (choose (dec n) k)))` |
| `fib` (part) | PASS | 18 | `(defn fib [n] (if (<= n 1) n (+ (fib (- n 1)) (fib (- n 2)))))` |
| `fib-sum` | PASS | 38 | `(defn fib-sum [n] (loop [sum 0 up 1 nth n] (if (zero? nth) sum (recur (+ sum up) (inc sum) (dec nth)))))` |
| `collatz` (part) | PASS | 25 | `(defn collatz [n] (loop [x n steps 0] (if (<= x 1) steps (recur (if (even? x) (/ x 2) (+ (* 3 x) 1)) (+ 1 steps)))))` |
| `longest` | OVER | 37 | `(defn longest [n] (collatz n))` |
| `divides?` (part) | PASS | 6 | `(defn divides? [d n] (zero? (mod n d)))` |
| `divisor-sum` (part) | PASS | 15 | `(defn divisor-sum [n] (reduce + 0 (filter (fn [x] (divides? x n)) (range 1 n))))` |
| `perfect?` | PASS | 26 | `(defn perfect? [n] (= (divisor-sum n) n))` |

### `split-sure-plan`, 2026-11-26 14:39, flow :score

| Function | Result | API calls | Program of the last run |
| --- | --- | --- | --- |
| `prime?` (part) | PASS | 113 | `(defn prime? [n] (cond (<= n 1) false (= 2 n) true :else (every? (fn [i] (not (zero? (mod n i)))) (range 2 n))))` |
| `prime-sum` | PASS | 124 | `(defn prime-sum [n] (reduce + 0 (filter prime? (range 2 n))))` |
| `gcd` (part) | PASS | 12 | `(defn gcd [a b] (if (zero? b) a (gcd b (mod a b))))` |
| `lcm` | PASS | 57 | `(defn lcm [a b] (/ (* a b) (gcd a b)))` |
| `digit-sum` (part) | PASS | 20 | `(defn digit-sum [n] (loop [x n acc 0] (if (zero? x) acc (recur (quot x 10) (+ (mod x 10) acc)))))` |
| `digital-root` | PASS | 31 | `(defn digital-root [n] (if (< n 10) n (digital-root (digit-sum n))))` |
| `rev` (part) | PASS | 11 | `(defn rev [coll] (reduce (fn [acc x] (cons x acc)) [] coll))` |
| `palindrome?` | PASS | 18 | `(defn palindrome? [xs] (= (rev xs) xs))` |
| `factorial` (part) | PASS | 12 | `(defn factorial [n] (if (zero? n) 1 (* (factorial (dec n)) n)))` |
| `choose` | FAIL | 87 | `(defn choose [n k] (/ (factorial n) (/ (factorial k) (factorial (- n k)))))` |
| `fib` (part) | PASS | 64 | `(defn fib [n] (if (< n 2) n (+ (fib (- n 2)) (fib (dec n)))))` |
| `fib-sum` | PASS | 86 | `(defn fib-sum [n] (loop [sum 0 up 1 nth n] (if (zero? nth) sum (recur (+ sum up) (inc sum) (dec nth)))))` |
| `collatz` (part) | PASS | 26 | `(defn collatz [n] (loop [x n steps 0] (if (<= x 1) steps (recur (if (even? x) (/ x 2) (+ (* 3 x) 1)) (+ 1 steps)))))` |
| `longest` | OVER | 39 | `(defn longest [n] (collatz n))` |
| `divides?` (part) | PASS | 7 | `(defn divides? [d n] (zero? (mod n d)))` |
| `divisor-sum` (part) | PASS | 16 | `(defn divisor-sum [n] (reduce + 0 (filter (fn [x] (divides? x n)) (range 1 n))))` |
| `perfect?` | PASS | 30 | `(defn perfect? [n] (= (divisor-sum n) n))` |

## All runs

| Date | Set | Flow | PASS | OVER | FAIL | API calls |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-11-26 14:39 | `split-sure-plan` | :score | 6 | 1 | 1 | 472 |
| 2026-11-26 12:37 | `split-sure` | :score | 5 | 1 | 2 | 762 |
| 2026-11-26 12:33 | `held-sure` | :score | 11 | 0 | 1 | 426 |
| 2026-11-26 12:32 | `hard-sure` | :score | 13 | 0 | 1 | 511 |
| 2026-11-26 12:29 | `basic-sure` | :score | 15 | 0 | 0 | 152 |
| 2026-11-26 12:01 | `split-sure` | :score | 5 | 1 | 2 | 875 |
| 2026-11-26 11:56 | `held-sure` | :score | 10 | 0 | 2 | 426 |
| 2026-11-26 11:54 | `hard-sure` | :score | 13 | 0 | 1 | 426 |
| 2026-11-26 11:52 | `basic-sure` | :score | 15 | 0 | 0 | 221 |
| 2026-11-26 11:42 | `held-flat` | :score | 2 | 3 | 7 | 295 |
| 2026-11-26 11:41 | `hard-flat` | :score | 1 | 7 | 6 | 264 |
| 2026-11-26 11:41 | `basic-flat` | :score | 3 | 4 | 8 | 341 |
| 2026-11-26 11:14 | `split` | :score | 5 | 1 | 2 | 707 |
| 2026-11-26 10:58 | `whole` | :score | 4 | 0 | 4 | 528 |
| 2026-11-26 10:53 | `split` | :score | 4 | 1 | 3 | 558 |
| 2026-10-21 22:20 | `basic` | :score | 14 | 1 | 0 | 140 |
| 2026-10-21 22:20 | `basic` | :score | 14 | 1 | 0 | 138 |
| 2026-10-21 22:18 | `held` | :score | 11 | 0 | 1 | 375 |
| 2026-10-21 22:18 | `held` | :score | 11 | 0 | 1 | 325 |
| 2026-10-21 22:14 | `hard` | :score | 11 | 2 | 1 | 378 |
| 2026-10-21 22:14 | `hard` | :score | 11 | 1 | 2 | 389 |

