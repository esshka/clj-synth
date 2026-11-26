(ns cljjev.policy-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.generate :as g]
            [cljjev.policy :refer [flat-policy]]
            [cljjev.validate :refer [runtime]]))

(deftest the-flat-policy-knows-nothing
  (let [answers ((flat-policy) {} {"prod" {:type "choice" :criteria (array-map "a" "x" "b" "y")}
                                   "fit" {:type "score" :criteria ["w" "x" "y" "z"]}})]
    (is (= {"type" "choice" "choice" "a" "probabilities" {"a" 0.5 "b" 0.5}} (answers "prod")))
    (is (= 1.5 (get-in answers ["fit" "score"])))))

(deftest search-with-no-model-finds-a-small-program
  (when (runtime)
    (let [out (g/solve "return the larger of a and b (= 5 (max2 5 3)) (= 7 (max2 2 7))" (flat-policy))]
      (is (g/passed? out) (g/source (g/best out)))
      (is (re-find #"^local: " (:edit (g/best out)))))))
