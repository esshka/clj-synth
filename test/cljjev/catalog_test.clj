(ns cljjev.catalog-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.catalog :refer [legal]]
            [cljjev.env :refer [context]]
            [cljjev.test-support :refer [fact fact-pools legal-at]]
            [cljjev.tree :refer [->hole]]))

(deftest defn-only-at-root
  (is (= ["defn"] (keys (legal-at (->hole "top" "top") [] (->hole "top" "top")))))
  (is (not (contains? (legal-at (fact (->hole "any" "b")) [3]) "defn"))))

(deftest no-bare-local-as-defn-body
  (let [body (legal-at (fact (->hole "any" "b")) [3])]
    (is (not (contains? body "local:n")))
    (is (contains? body "call:factorial"))))

(deftest recur-only-in-tail-with-matching-arity
  (let [tree (fact (list 'if (->hole "bool" "t") (->hole "any" "a") (list '+ (->hole "num" "x") 1)))
        loop-tree (fact (list 'loop '[i 0 acc 1] (->hole "any" "b")))]
    (is (not (contains? (legal-at tree [3 1] (->hole "bool" "t")) "recur")))
    (is (contains? (legal-at tree [3 2]) "recur"))
    (is (not (contains? (legal-at tree [3 3 1] (->hole "num" "t")) "recur")))
    (is (= 2 (:recur-arity (context loop-tree [3 2]))))
    (is (= [(->hole "any" "recur arg 1") (->hole "any" "recur arg 2")]
           (rest (second (get (legal-at loop-tree [3 2]) "recur")))))))

(deftest bool-slot-rejects-seq-producers
  (let [bool-opts (legal-at (fact (->hole "bool" "b")) [3] (->hole "bool" "t"))]
    (is (contains? bool-opts "<="))
    (doseq [k ["cons" "conj" "rest" "map" "lit:[]" "lit:1" "+"]]
      (is (not (contains? bool-opts k)) k))))

(deftest depth-limit-leaves-only
  (let [deep (legal (->hole "num" "t") (context (fact (->hole "num" "b")) [3]) fact-pools 1)]
    (is (every? #(re-find #"^(local:|lit:)" %) (keys deep)) (keys deep))))

(deftest two-list-map-prints-as-map
  (let [tree '(defn dot [xs ys] (reduce f 0 x))
        options (legal (->hole "seq" "reduce coll") (context tree [3 3]) fact-pools 8)]
    (is (= (list 'map (->hole "fn" "map f") (->hole "seq" "map a") (->hole "seq" "map b"))
           (second (get options "map2"))))))

(deftest bindings-offer-three-names-within-the-cap
  (let [tree (fact (list 'loop (->hole "bindings" "loop bindings") (->hole "any" "b")))
        options (legal-at tree [3 1] (->hole "bindings" "loop bindings"))]
    (is (some #(= 2 (count (re-seq #" " %))) (keys options)))
    (is (<= (count options) 255))))
