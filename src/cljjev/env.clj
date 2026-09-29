(ns cljjev.env
  "Pools read off a spec (tests, names, literals, parameter kinds), and the scope of a
  hole derived from its path."
  (:require [clojure.string :as str]
            [cljjev.catalog :refer [known]]
            [cljjev.tree :refer [branch? flat head read-all]]))

(def ^:private stop-words
  (set (str/split "a an the of to in on at and or is are be that which with for from by as it
                   its this write define implement create make function returns return given
                   compute computes calculate recursive recursively using use should takes take
                   clojure fn"
                  #"\s+")))

(def ^:private default-locals ["n" "x" "xs" "acc" "result" "i" "coll"])
(def ^:private default-numbers ["0" "1" "2" "3" "-1" "10"])
(def ^:private max-spec-locals 8)

(defn- top-forms
  "Returns the balanced top-level (...) chunks of text, as [start end] spans."
  [text]
  (loop [i 0 depth 0 start nil out []]
    (if (= i (count text))
      out
      (let [ch (.charAt ^String text i)]
        (cond
          (= ch \() (recur (inc i) (inc depth) (if (zero? depth) i start) out)
          (and (= ch \)) (pos? depth)) (recur (inc i) (dec depth) start
                                              (if (= depth 1) (conj out [start (inc i)]) out))
          :else (recur (inc i) depth start out))))))

(defn- calls
  "Returns [name argument-nodes] for every call to a function Clojure does not know."
  [node]
  (when (branch? node)
    (concat (when (and (head node) (not (known (head node))))
              [[(head node) (vec (rest node))]])
            (mapcat calls node))))

(defn- kind [node]
  (cond
    (or (vector? node) (= (head node) "quote")) "seq"
    (number? node) "num"
    (boolean? node) "bool"
    :else "any"))

(defn- param-kinds
  "Returns, per argument position, the one kind every test value has, else any."
  [args-seen]
  (vec (for [k (range (count (first args-seen)))
             :let [kinds (set (map #(kind (nth % k)) args-seen))]]
         (if (= 1 (count kinds)) (first kinds) "any"))))

(defn extract
  "Returns the pools for spec: its (= ...) forms plus extra-tests become tests, and
  the tests fix the function name, arity and parameter kinds. Names and numbers in
  the prose become the only identifiers and literals on offer."
  [spec extra-tests]
  (let [spec-tests (for [[a b] (top-forms spec)
                         :let [t (subs spec a b)]
                         :when (str/starts-with? t "(= ")]
                     t)
        prose (reduce #(str/replace %1 %2 " ") spec spec-tests)
        tests (vec (concat spec-tests extra-tests))
        seen-calls (for [t tests form (read-all t) c (calls form)] c)
        called (vec (distinct (map first seen-calls)))
        args-of (fn [n] (for [[m args] seen-calls :when (= m n)] args))
        arity (into {} (for [n called
                             :let [a (set (map count (args-of n)))]
                             :when (= 1 (count a))]
                         [n (first a)]))
        tokens (distinct (re-seq #"[a-z][a-z0-9-]*[?!]?" (str/lower-case prose)))
        words (remove #(or (stop-words %) (known %)) tokens)
        numbers (re-seq #"(?<![\w.])-?\d+(?![\w.])" prose)
        ;; single letters stay: "a and b" names params even though "a" is a stop word
        spec-locals (->> tokens
                         (filter #(and (or (= 1 (count %)) (not (stop-words %)))
                                       (not (known %))
                                       (not ((set called) %))))
                         (take max-spec-locals))]
    {:prose (str/join " " (str/split (str/trim prose) #"\s+"))
     :tests tests
     ;; function name candidates
     :names (vec (or (seq called) (seq words) ["f"]))
     ;; function name -> argument count, when every test call agrees
     :arity arity
     ;; function name -> kind of each argument (num seq bool any), from the test values
     :param-kinds (into {} (for [n (keys arity)] [n (param-kinds (args-of n))]))
     ;; function name -> [a test call, its argument texts]
     :examples (into {} (for [n (keys arity) :let [args (first (args-of n))]]
                          [n [(flat (apply list (symbol n) args)) (mapv flat args)]]))
     ;; param and binding name candidates
     :locals (vec (distinct (concat spec-locals default-locals)))
     :lits (into (mapv #(vector % "num") (distinct (concat default-numbers numbers)))
                 [["true" "bool"] ["false" "bool"] ["nil" "any"] ["[]" "seq"]])}))

(defn- syms [items]
  (mapv str (filter symbol? items)))

(defn- step
  "Returns the scope s after descending from its node into child i."
  [{:keys [node parent tail] :as s} i]
  (let [s (assoc s :defn-body false :node (nth node i))]
    (if (vector? node)
      (cond-> (assoc s :tail false)
        ;; an init sees only the names bound before it
        (#{"let" "loop" "if-let"} parent)
        (update :scope into (syms (take-nth 2 (take (max (dec i) 0) node)))))
      (let [parent (head node)
            last-i (dec (count node))
            s (-> s (update :depth inc) (assoc :parent parent))
            bound (fn [k] (when (vector? (nth node k nil)) (nth node k)))]
        (case parent
          ("defn" "fn")
          (let [p (if (= parent "defn") 2 1)
                params (when (> i p) (some-> (bound p) syms))]
            (if params
              (cond-> (-> s
                          (update :scope into params)
                          (assoc :arity (count params) :tail true :defn-body (= parent "defn")))
                (= parent "defn") (assoc :defn-params params))
              (assoc s :tail false)))

          ("let" "loop")
          (if-let [names (when (>= i 2) (some->> (bound 1) (take-nth 2) syms))]
            (cond-> (-> s (update :scope into names) (assoc :tail (and tail (= i last-i))))
              (= parent "loop") (assoc :arity (count names)))
            s)

          ;; the name exists only in the then branch
          "if-let" (cond-> (assoc s :tail (and tail (contains? #{2 3} i)))
                     (and (= i 2) (bound 1)) (update :scope into (syms (take-nth 2 (bound 1)))))
          "if" (assoc s :tail (and tail (contains? #{2 3} i)))
          "cond" (assoc s :tail (and tail (even? i)))
          ("do" "when") (assoc s :tail (and tail (= i last-i)))
          (assoc s :tail false))))))

(defn context
  "Returns the scope at path in tree: locals (innermost first), the function being
  written, tail position, recur arity, depth, and which defn params are unshadowed."
  [tree path]
  (let [[_ name-node params-node] (when (seq? tree) tree)
        fname (when (and (= (head tree) "defn") (symbol? name-node)) (str name-node))
        functions (if (and fname (vector? params-node)) {fname (count params-node)} {})
        {:keys [scope tail arity depth defn-body defn-params]}
        (reduce step {:scope [] :tail true :arity nil :depth 0 :defn-body false
                      :node tree :parent nil :defn-params []}
                path)
        ;; defn params come first in scope; a later entry with the same name shadows
        last-at (into {} (map-indexed (fn [k n] [n k]) scope))]
    {:locals (vec (distinct (rseq scope)))
     :functions functions ; name -> arity
     :fname fname
     :tail tail
     :recur-arity arity
     :depth depth ; enclosing lists
     :defn-body defn-body
     :params (into {} (for [[k n] (map-indexed vector defn-params) ; param -> position
                            :when (< (last-at n) (count defn-params))]
                        [n k]))}))
