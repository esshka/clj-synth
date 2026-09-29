(ns cljjev.catalog
  "The options (productions) for each slot, their meanings and slot types. legal is the
  whole type-safety layer: Jev can only pick what it returns."
  (:require [clojure.string :as str]
            [cljjev.tree :refer [->hole]]))

(def ^:private max-options 255)
(def ^:private max-names 15) ; 15 singles + 210 ordered pairs fit in one Choice

;; [key returns arg-kinds doc]. The doc's head is the Clojure fn (map2 prints as map);
;; hole labels are the doc's argument names, so `<= a` means the a in (<= a b).
(def ^:private core-fns
  [["+" "num" ["num" "num"] "(+ a b) a plus b"]
   ["-" "num" ["num" "num"] "(- a b) a minus b, e.g. (- n 2) is two less than n"]
   ["*" "num" ["num" "num"] "(* a b) a times b"]
   ["/" "num" ["num" "num"] "(/ a b) a divided by b"]
   ["=" "bool" ["any" "any"] "(= a b) true when a equals b"]
   ["<" "bool" ["num" "num"]
    "(< a b) true when a is less than b, e.g. (< n 2) means n < 2"]
   [">" "bool" ["num" "num"]
    "(> a b) true when a is greater than b, e.g. (> n 0) means n > 0"]
   ["<=" "bool" ["num" "num"]
    "(<= a b) true when a is at most b, e.g. (<= n 1) means n <= 1"]
   [">=" "bool" ["num" "num"]
    "(>= a b) true when a is at least b, e.g. (>= n 1) means n >= 1"]
   ["inc" "num" ["num"] "(inc x) x plus one"]
   ["dec" "num" ["num"] "(dec x) x minus one"]
   ["not" "bool" ["any"] "(not x) true when x is false or nil"]
   ["nil?" "bool" ["any"] "(nil? x) true when x is nil"]
   ["empty?" "bool" ["seq"] "(empty? coll) true when coll has no items"]
   ["first" "any" ["seq"] "(first coll) the first item of coll"]
   ["rest" "seq" ["seq"] "(rest coll) coll without its first item"]
   ["cons" "seq" ["any" "seq"] "(cons x coll) coll with x added at the front"]
   ["conj" "seq" ["seq" "any"]
    "(conj coll x) coll with x added (end of a vector, front of a list)"]
   ["count" "num" ["seq"] "(count coll) the number of items in coll"]
   ["map" "seq" ["fn" "seq"] "(map f coll) f applied to every item of coll"]
   ["filter" "seq" ["fn" "seq"] "(filter pred coll) the items of coll where pred is true"]
   ["reduce" "any" ["fn" "any" "seq"]
    "(reduce f init coll) fold coll with (f acc item), starting from init"]
   ["mod" "num" ["num" "num"]
    "(mod a b) the remainder of a divided by b, e.g. (mod 7 2) is 1"]
   ["quot" "num" ["num" "num"]
    "(quot a b) a divided by b without the remainder, e.g. (quot 7 2) is 3"]
   ["max" "num" ["num" "num"] "(max a b) the larger of a and b"]
   ["min" "num" ["num" "num"] "(min a b) the smaller of a and b"]
   ["zero?" "bool" ["num"] "(zero? x) true when x is 0"]
   ["pos?" "bool" ["num"] "(pos? x) true when x is greater than 0"]
   ["neg?" "bool" ["num"] "(neg? x) true when x is less than 0"]
   ["even?" "bool" ["num"] "(even? x) true when x is even"]
   ["odd?" "bool" ["num"] "(odd? x) true when x is odd"]
   ["str" "any" ["any" "any"] "(str a b) a and b joined as text"]
   ["map2" "seq" ["fn" "seq" "seq"]
    "(map f a b) f applied to the items of a and b in pairs, e.g. (map * [1 2] [3 4]) is (3 8)"]
   ["range" "seq" ["num" "num"]
    "(range a b) the numbers from a up to but not including b, e.g. (range 2 5) is (2 3 4)"]
   ["every?" "bool" ["fn" "seq"]
    "(every? pred coll) true when pred is true for every item of coll"]
   ["and" "bool" ["bool" "bool"] "(and a b) true when both a and b are true"]
   ["or" "bool" ["bool" "bool"] "(or a b) true when a or b is true"]])

;; core fns that may be passed by name into a fn slot, e.g. (map inc xs)
(def ^:private refs
  ["inc" "dec" "not" "nil?" "empty?" "first" "rest" "count" "str" "+" "*" "max" "min"
   "zero?" "pos?" "neg?" "even?" "odd?"])

(def ^:private kind-words {"num" "a number" "seq" "a collection" "bool" "true or false"})

(def ^:private special-forms
  ["defn" "fn" "let" "if" "do" "loop" "recur" "quote" "when" "cond" "if-let"])

(defn- doc-head [doc]
  (first (str/split (subs doc 1) #"\s+")))

(def known
  "Names Clojure already defines; a call to any other name is a call to the function
  being written."
  (into (set special-forms) (mapcat (fn [[k _ _ doc]] [k (doc-head doc)]) core-fns)))

(defn ordered
  "Returns an insertion-ordered map of [k v] entries. Option order reaches Jev, and a
  later duplicate key keeps the first one's place."
  [entries]
  (apply array-map (mapcat identity entries)))

(defn fits?
  "Returns true if a form returning kind returns can fill a slot of kind expected."
  [returns expected]
  (or (= expected "any") (contains? #{"any" expected} returns)))

(defn- call-node [name args]
  (apply list (symbol name) (for [[kind label] args] (->hole kind (str name " " label)))))

(defn- labels [doc]
  (rest (str/split (subs doc 1 (str/index-of doc ")")) #"\s+")))

(defn- core-call [kinds doc]
  (call-node (doc-head doc) (map vector kinds (labels doc))))

(defn- core-entry [name]
  (first (filter #(= name (first %)) core-fns)))

(defn arg-holes
  "Returns the argument holes of core fn name, as its option would open them."
  [name]
  (let [[_ _ kinds doc] (core-entry name)]
    (vec (rest (core-call kinds doc)))))

(defn core-returns
  "Returns the kind core fn name returns."
  [name]
  (second (core-entry name)))

(def ^:private special-docs
  {"if" "(if test then else) the value of then when test is true, else the value of else"
   "when" "(when test body) the value of body when test is true, else nil"
   "do" "(do a b) evaluate a only for side effects and discard it, return b"
   "let" "(let [name expr ...] body) bind local names, then evaluate body"
   "loop" "(loop [name init ...] body) bind names and start a target for recur"
   "fn" "(fn [params] body) anonymous function"
   "quote" "(quote ()) the empty list"
   "cond" (str "(cond test1 then1 test2 then2 :else other) then1 if test1, "
               "else then2 if test2, else other")
   "if-let" (str "(if-let [name expr] then else) when expr is not nil or false, "
                 "then with name bound to it; else else")
   "recur" "(recur ...) jump back to the enclosing loop/fn with new values for its names"})

(def docs
  "Meaning of each form: core fns keyed by [head arg-count], special forms by name."
  (merge (into {} (for [[_ _ kinds doc] core-fns] [[(doc-head doc) (count kinds)] doc]))
         special-docs))

(defn- blanks [n]
  (str/join " " (repeat n "_")))

(defn- specials
  "[key [returns doc node]] for each special form; recur only in tail position."
  [e env]
  (let [h ->hole
        forms [["if" "any" [(h "bool" "if test") (h e "if then") (h e "if else")]]
               ["when" "any" [(h "bool" "when test") (h e "when body")]]
               ["do" "any" [(h "any" "do a") (h e "do b")]]
               ["let" "any" [(h "bindings" "let bindings") (h e "let body")]]
               ["loop" "any" [(h "bindings" "loop bindings") (h e "loop body")]]
               ["fn" "fn" [(h "params" "fn params") (h "any" "fn body")]]
               ["quote" "seq" [()]]
               ["cond" "any" [(h "bool" "cond test1") (h e "cond then1") (h "bool" "cond test2")
                              (h e "cond then2") :else (h e "cond other")]]
               ["if-let" "any" [(h "bindings" "if-let binding") (h e "if-let then")
                                (h e "if-let else")]]]
        n (:recur-arity env)]
    (cond-> (vec (for [[k returns args] forms]
                   [k [returns (special-docs k) (apply list (symbol k) args)]]))
      (and (:tail env) n)
      (conj ["recur" ["any"
                      (str "(recur " (blanks n) ") jump back to the enclosing loop/fn with "
                           n " new value(s)")
                      (call-node "recur" (for [i (range n)] ["any" (str "arg " (inc i))]))]]))))

(defn- combinations [xs k]
  (cond
    (zero? k) [[]]
    (empty? xs) []
    :else (concat (map #(into [(first xs)] %) (combinations (rest xs) (dec k)))
                  (combinations (rest xs) k))))

(defn- permutations [xs k]
  (if (zero? k)
    [[]]
    (for [[i x] (map-indexed vector xs)
          p (permutations (concat (take i xs) (drop (inc i) xs)) (dec k))]
      (into [x] p))))

(defn- vectors
  "Options for a params or bindings vector: which names, in which order."
  [hole env pools]
  (let [bindings? (= (:kind hole) "bindings")
        taken (into #{(:fname env)} (when bindings? (:locals env)))
        names (vec (remove taken (:locals pools)))
        form (first (str/split (:hint hole) #"\s+"))
        size (cond (= (:hint hole) "defn params") (get (:arity pools) (:fname env))
                   (= form "if-let") 1)
        size (when (and size (pos? size)) size)
        ;; order matters ((fn [acc x] ...) for reduce), so pairs are permutations; the tests
        ;; fix the defn arity, and 3+ names draw from fewer candidates in one order to stay
        ;; under the option cap
        groups (if (and size (> size 2))
                 (combinations (take 10 names) size)
                 (for [n (if size [size] [1 2]) g (permutations (take max-names names) n)] g))
        ;; a loop may carry three values, e.g. [i 0 a 0 b 1]
        groups (if (and bindings? (not size))
                 (concat groups (take (- max-options (count groups))
                                      (combinations (filter #(<= (count %) 3) names) 3)))
                 groups)
        example (when (= (:hint hole) "defn params") (get (:examples pools) (:fname env)))]
    (ordered
     (for [group groups
           :let [key (str "[" (str/join " " group) "]")]]
       (if (= (:kind hole) "params")
         (let [binds (str/join ", " (map #(str %1 " = " %2) group (second example)))
               where (if example (str ": in " (first example) ", " binds) "")]
           [key [(str "parameters " key where) (mapv symbol group)]])
         [key [(str "bind " (str/join " and " group))
               (vec (mapcat #(vector (symbol %) (->hole "any" (str form " init " %))) group))]])))))

(defn- exprs
  "Options for an expression slot: locals, literals, fn refs, then (below max-depth)
  core calls, special forms and calls to the function being written."
  [hole env pools max-depth]
  (let [e (:kind hole)
        kinds (get (:param-kinds pools) (:fname env) [])
        ;; a bare param as the whole defn body is never the answer
        locals (when-not (:defn-body env)
                 (for [x (:locals env)
                       :let [pos (get (:params env) x)
                             kind (if (and pos (< pos (count kinds))) (nth kinds pos) "any")]
                       ;; tests passed a vector for xs: xs never fills a number slot
                       :when (fits? kind e)]
                   [(str "local:" x)
                    [(str "the local `" x "`" (when-let [w (kind-words kind)] (str ", " w)))
                     (symbol x)]]))
        lits (for [[text kind] (:lits pools) :when (fits? kind e)]
               [(str "lit:" text) [(str "the literal " text) (read-string text)]])
        ref-opts (when (= e "fn")
                   (for [name (concat (keys (:functions env)) refs)]
                     [(str "ref:" name)
                      [(str "the function `" name "` passed by name") (symbol name)]]))
        compound (when (< (:depth env) max-depth)
                   (concat
                    (for [[name returns kinds doc] core-fns :when (fits? returns e)]
                      [name [doc (core-call kinds doc)]])
                    (for [[name [returns doc node]] (specials e env) :when (fits? returns e)]
                      [name [doc node]])
                    (for [[name arity] (:functions env)]
                      [(str "call:" name)
                       [(str "(" name " " (blanks arity) ") call `" name
                             "`, the function being defined")
                        (call-node name (for [i (range arity)] ["any" (str "arg " (inc i))]))]])))]
    (ordered (concat locals lits ref-opts compound))))

(defn legal
  "Returns the options for one hole, an ordered map {key [criteria-text node]}.
  Throws if there are none, or more than a Choice allows."
  [hole env pools max-depth]
  (let [opts (case (:kind hole)
               "top" (ordered [["defn" ["(defn name [params] body) define the function"
                                        (list 'defn (->hole "fname" "defn name")
                                              (->hole "params" "defn params")
                                              (->hole "any" "defn body"))]]])
               "fname" (ordered (for [n (:names pools)]
                                  [(str "name:" n) [(str "name the function `" n "`") (symbol n)]]))
               ("params" "bindings") (vectors hole env pools)
               (exprs hole env pools max-depth))]
    (when-not (< 0 (count opts) (inc max-options))
      (throw (ex-info (str (:hint hole) ": " (count opts) " options, Choice allows 1.." max-options)
                      {:hint (:hint hole) :count (count opts)})))
    opts))
