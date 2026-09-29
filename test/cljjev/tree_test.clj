(ns cljjev.tree-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.tree :refer [->hole flat holes node-at put-at read-all]]))

(deftest reader-keeps-quote
  (is (= ['(quote (1))] (read-all "'(1)"))))

(deftest paths-address-subtrees
  (let [tree (list 'defn 'f '[x] (list 'inc (->hole "num" "inc x")))]
    (is (= [[[3 1] (->hole "num" "inc x")]] (holes tree)))
    (is (= '(defn f [x] (inc x)) (put-at tree [3 1] 'x)))
    (is (= '(inc x) (node-at (put-at tree [3 1] 'x) [3])))
    (is (= "(defn f [x] (inc __HOLE__))" (flat tree [3 1])))))
