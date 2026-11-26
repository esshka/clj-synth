(ns cljjev.mutate
  "Small changes to a finished program, each one a program build could make: a new small
  subtree, another operator over the same arguments, swapped arguments, a wrap in a new
  form, or a part in place of the whole. Local repair and shrink test them without asking
  Jev."
  (:require [cljjev.catalog :refer [known legal wrap-ops]]
            [cljjev.env :refer [context]]
            [cljjev.tree :refer [->hole branch? head hole? holes node-at put-at]]))

(def ^:private expr-kinds #{"num" "bool" "seq" "fn" "any"})
(def ^:private commutative #{"+" "*" "=" "max" "min" "and" "or"})

(defn- unify
  "Returns the [path hole] pairs of template's holes, paths relative, if node has the
  template's shape; else nil."
  [template node]
  (cond
    (hole? template) [[[] template]]

    (and (branch? template) (branch? node)
         (= (vector? template) (vector? node)) (= (count template) (count node)))
    (reduce (fn [found [i t n]]
              (if-let [m (unify t n)]
                (into found (for [[p h] m] [(into [i] p) h]))
                (reduced nil)))
            []
            (map vector (range) template node))

    (= template node) []))

(defn- options
  "Returns (fn [tree path hole] options), legal remembered per hole and scope: a search
  asks the same few slots thousands of times."
  [pools limits]
  (let [at (memoize (fn [hole env] (legal hole env pools (:max-depth limits))))]
    (fn [tree path hole] (at hole (context tree path)))))

(defn- parse
  "Returns the decisions that build the subtree of tree at path in hole, in build order,
  or nil if some part of it is not an option legal where it sits. An open hole needs none."
  [opts tree path hole]
  (let [node (node-at tree path)]
    (if (hole? node)
      []
      (when-let [[k m] (first (keep (fn [[k [_ t]]] (some->> (unify t node) (vector k)))
                                    (opts tree path hole)))]
        (reduce (fn [found [p h]]
                  (if-let [ds (parse opts tree (into path p) h)]
                    (into found ds)
                    (reduced nil)))
                [{:path path :hole hole :choice k}]
                m)))))

(defn- decisions [opts tree]
  (parse opts tree [] (->hole "top" "top")))

(defn sites
  "Returns the decisions that build tree, in build order, as {:path :hole :choice}: build
  run backwards. Returns nil if build could not make tree."
  [tree pools limits]
  (decisions (options pools limits) tree))

(defn- completions
  "Returns [tree keys] for every way to close the holes under root with at most budget
  decisions, keys the options in build order. Forms that bind names (fn, let, loop) are
  left out, and so is do, which discards a value."
  [opts tree root budget]
  (let [open (holes (node-at tree root) root)]
    (if-let [[path hole] (first open)]
      (for [[k [_ t]] (opts tree path hole)
            :let [inner (holes t)]
            :when (and (not= k "do")
                       (every? #(expr-kinds (:kind (second %))) inner)
                       ;; every hole still open needs one decision
                       (<= (+ (count open) (count inner)) budget))
            [done ks] (completions opts (put-at tree path t) root (dec budget))]
        [done (cons k ks)])
      [[tree ()]])))

(defn- leaves [node]
  (cond (seq? node) (mapcat leaves (rest node))
        (vector? node) (mapcat leaves node)
        :else [node]))

(defn redundant?
  "Returns true if node holds a core call that only repeats a simpler option: one on
  literals only, a commutative one with its arguments out of order, or one on the same
  argument twice."
  [node]
  (boolean
   (and (seq? node)
        (or (some redundant? (rest node))
            (let [h (head node)
                  args (rest node)]
              (and (known h) (not (#{"quote" "recur"} h))
                   (or (not-any? symbol? (leaves node))
                       (and (= 2 (count args))
                            (or (= (first args) (second args))
                                (and (commutative h)
                                     (pos? (compare (pr-str (first args))
                                                    (pr-str (second args))))))))))))))

(defn- changes-at
  "Returns the changes at decision d of tree as {:tree :size :key}: size counts the new
  decisions, key names the change the way Jev's probabilities do."
  [opts tree d size]
  (let [{:keys [path hole choice]} d
        node (node-at tree path)
        opts-here (opts tree path hole)
        own (second (get opts-here choice))
        args (when (seq? own) (rest own))
        same-args? (fn [t] (and (seq? t) (= (count t) (count own))
                                (every? hole? args) (every? hole? (rest t))
                                (= (map :kind args) (map :kind (rest t)))))
        ;; a moved subtree must still be legal where it lands: no recur out of tail
        ;; position, no (if c 1 2) as a test
        legal? (fn [t] (parse opts t path hole))]
    (concat
     (for [[t ks] (completions opts (put-at tree path hole) path size)
           :let [new (node-at t path)]
           :when (and (not= new node) (not (redundant? new)))]
       {:tree t :size (count ks) :key (first ks)})
     ;; (< n 2) -> (<= n 2): the arguments stay
     (when (seq? own)
       (for [[k [_ t]] opts-here
             :when (and (not= k choice) (same-args? t))
             :let [new (put-at tree path (apply list (first t) (rest node)))]
             :when (legal? new)]
         {:tree new :size 1 :key k}))
     (when (and (= 2 (count args)) (every? hole? args) (apply = (map :kind args))
                (not (commutative (head node))) (not= (second node) (nth node 2)))
       (let [new (put-at tree path (list (first node) (nth node 2) (second node)))]
         (when (legal? new)
           [{:tree new :size 1 :key "swap"}])))
     (for [op wrap-ops
           :let [t (second (get opts-here op))
                 base (when t (put-at tree path (apply list (first t) node (drop 2 t))))]
           :when (and t (legal? base))
           ;; and / or take a whole test as b: (and (> n 0) (not (empty? xs)))
           [w ks] (completions opts base path (if (#{"and" "or"} op) size (dec size)))
           :when (or (= 2 (count t)) (not (redundant? (node-at w (conj path 2)))))]
       {:tree w :size (inc (count ks)) :key (str "wrap:" op)}))))

(defn neighbors
  "Returns every change to an expression of tree with at most size new decisions, as
  {:tree :site :size :key}, site the decision it changes. Sites run in parallel."
  [tree pools limits size]
  (let [opts (options pools limits)]
    (apply concat (pmap (fn [d] (mapv #(assoc % :site d) (changes-at opts tree d size)))
                        (filter #(expr-kinds (get-in % [:hole :kind])) (decisions opts tree))))))

(defn- parts
  "Returns [path part] for every expression inside node, paths relative, outermost first."
  [node]
  (when (branch? node)
    (mapcat (fn [[i c]] (cons [[i] c] (for [[p x] (parts c)] [(into [i] p) x])))
            (drop (if (seq? node) 1 0) (map-indexed vector node)))))

(defn hoists
  "Returns {:tree :dropped} for every way to replace one expression of tree by a part of
  itself, where build could make the result: (max (first xs) (reduce max a b)) ->
  (reduce max a b). dropped holds the paths in tree of the expressions that go."
  [tree pools limits]
  (let [opts (options pools limits)
        exprs (filter #(expr-kinds (get-in % [:hole :kind])) (decisions opts tree))
        under? (fn [root path] (= root (take (count root) path)))]
    (for [{:keys [path hole]} exprs
          [q part] (parts (node-at tree path))
          :let [new (put-at tree path part)
                kept (into path q)]
          :when (parse opts new path hole)]
      {:tree new
       :dropped (for [p (map :path exprs) :when (and (under? path p) (not (under? kept p)))] p)})))
