(ns cljjev.policy
  "A deterministic stand-in for Jev: the same questions in, the same answer shapes out.
  A policy is a function (fn [state questions] answers), answers keyed like questions."
  (:require [clojure.string :as str]
            [cljjev.tree :refer [branch? read-all]]))

(def ^:private leaf-prefixes ["local:" "lit:" "ref:" "name:" "["])

;; ponytail: hand-tuned priors, enough to exercise the loop; not a model of good code
(def ^:private priors
  {"any" {"if" 6 "*" 3.5 "+" 2 "dec" 2 "call" 1.5 "local" 4 "lit:1" 3}
   "bool" {"<=" 5 "=" 4 "empty?" 3 "local" 1}
   "num" {"local" 4 "lit:1" 3 "*" 2.5 "+" 2 "dec" 2 "call" 2.5}
   "seq" {"local" 4 "rest" 3 "lit:[]" 2}
   "fn" {"ref:inc" 2 "fn" 2}})

(defn- words [state]
  (->> (str (:spec state) " " (str/join " " (:tests state)))
       str/lower-case
       (re-seq #"[a-z][a-z0-9-]*[?!]?")
       set))

(defn- siblings
  "Returns the printed leaves of the form around the hole."
  [state]
  (let [form (get-in state [:hole :form])]
    (try
      (let [node (when form (first (read-all form)))]
        (if (branch? node) (set (map #(when-not (branch? %) (pr-str %)) node)) #{}))
      (catch Exception _ #{}))))

(defn- starts? [key prefixes]
  (some #(str/starts-with? key %) prefixes))

(defn- after-colon [key]
  (if-let [i (str/last-index-of key ":")] (subs key (inc i)) key))

(defn- weight [key state words siblings]
  (cond
    (str/starts-with? key "name:")
    (let [n (subs key 5)]
      (cond (str/includes? (str/join " " (:tests state)) n) 3
            (words n) 2
            :else 1))

    (str/starts-with? key "[")
    (let [names (str/split (subs key 1 (dec (count key))) #"\s+")]
      ;; "a" is the article, not a name
      (* (reduce * 1.0 (for [n names] (if (and (words n) (not= n "a")) 3 1)))
         (if (> (count names) 1) 0.3 1)))

    :else
    (let [{kind :expected hint :hint depth :depth :or {kind "any" hint "" depth 0}} (:hole state)
          prior (get priors kind {})
          group (if (starts? key ["local:" "call:"]) (first (str/split key #":")) key)
          leaf (starts? key leaf-prefixes)
          hint-head (re-find #"\S+" hint)
          fnames (set (map #(first (str/split % #"/")) (get-in state [:env :functions])))]
      (cond-> (double (get prior key (get prior group 1)))
        (siblings (after-colon key)) (* 0.3)
        (and (str/starts-with? key "call:") (words "recursive")) (* 2)
        ;; base case: prefer a constant
        (str/ends-with? hint "then") (* (cond (str/starts-with? key "lit:") 4 leaf 2 :else 1))
        (str/ends-with? hint "else") (* (if leaf 1 2))
        (not leaf) (* (Math/pow 0.5 (max 0 (- depth 2))))
        ;; a recursive call should shrink its argument
        (and hint-head (fnames hint-head)) (* (if (#{"dec" "rest"} key) 10 1))))))

(defn- first-max [xs]
  (reduce (fn [a b] (if (> (second b) (second a)) b a)) xs))

(defn- answer [state q]
  (let [criteria (:criteria q)]
    (cond
      ;; fit: leaves rate lower than compound forms
      (= (:type q) "score")
      (let [ins (:instructions q)
            leaf (and (map? ins) (not (str/includes? (get ins :subtree "(") "(")))]
        {"type" "score" "score" (if leaf 1.0 2.0) "confidence" 0.5})

      ;; keep / replace / wrap: the mock cannot judge, so it never wraps
      (contains? criteria "replace")
      {"type" "choice" "choice" "replace" "confidence" 0.9
       "probabilities" (into {} (for [k (keys criteria)]
                                  [k (if (= k "replace") 0.9 (/ 0.1 (dec (count criteria))))]))}

      :else
      (let [w (words state)
            s (siblings state)
            sharp (for [k (keys criteria)] [k (Math/pow (weight k state w s) 3)])
            total (reduce + (map second sharp))
            probs (for [[k v] sharp] [k (/ v total)])
            [best p] (first-max probs)]
        {"type" "choice" "choice" best "probabilities" (into {} probs) "confidence" p}))))

(defn mock-policy
  "Returns the mock policy."
  []
  (fn [state questions]
    (into {} (for [[qid q] questions] [qid (answer state q)]))))
