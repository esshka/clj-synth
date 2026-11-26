(ns cljjev.oracle-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.generate :as g]
            [cljjev.oracle :refer [second-look sure-tests]]))

(deftest sure-answers-become-tests
  (let [asked (atom nil)
        yes-no (fn [state questions]
                 (reset! asked [state (keys questions)])
                 (into {} (for [[k _] questions]
                            [k {"type" "noul" "noul" (case k "2" 0.97 "4" 0.02 "9" 0.6 0.5)}])))
        number (fn [_ questions]
                 (into {} (for [[k _] questions]
                            [k {"type" "choice" "choice" (case k "[2 2]" "4" "[1]" "other" "7")
                                "probabilities" {"4" 0.95 "other" 0.99 "7" 0.4}}])))
        never (fn [_ _] (throw (ex-info "asked" {})))]
    ;; sure yes, sure no; 0.6 is no test
    (is (= ["(= true (prime? 2))" "(= false (prime? 4))"]
           (sure-tests "is n a prime number (= true (prime? 7)) (= false (prime? 8))" yes-no)))
    (is (= {:function "prime?" :meaning "is n a prime number"
            :examples ["(= true (prime? 7))" "(= false (prime? 8))"]}
           (first @asked)))
    ;; the spec already tests 7 and 8
    (is (not-any? #{"7" "8"} (second @asked)))
    ;; a number at 0.95 is a test; `other` and 0.4 are not
    (is (= ["(= 4 (sum-list [2 2]))"]
           (sure-tests "sum the numbers in xs (= 6 (sum-list [1 2 3])) (= 0 (sum-list []))" number)))
    ;; a collection is not one of a few options: no request
    (is (= [] (sure-tests "square every number in xs (= [1 4 9] (squares [1 2 3]))" never)))))

(deftest a-second-look-drops-a-test-jev-no-longer-backs
  (let [spec "sum of the squares from 1 to n (= 14 (square-sum 3)) (= 0 (square-sum 0))"
        jev-test "(= 35 (square-sum 5))"
        result (fn [test detail] {:test test :ok (= detail "PASS") :detail detail})
        outcome (fn [& results] {:calls 7 :attempts [{:code "(defn square-sum [n] 0)" :results (vec results)}]})
        one-off (outcome (result "(= 14 (square-sum 3))" "PASS") (result jev-test "FAIL expected 35, got 55"))
        looks (atom [])
        backing (fn [p] (fn [_ questions]
                          (swap! looks conj (get-in questions ["look" :criteria]))
                          {"look" {"type" "choice" "choice" "55" "probabilities" {"35" p "55" (- 1 p)}}}))
        never (fn [_ _] (throw (ex-info "asked" {})))
        looked (second-look one-off spec [jev-test] (backing 0.12))]
    (is (= [jev-test] (:dropped looked)))
    (is (g/passed? looked))
    (is (= 8 (:calls looked)))
    (is (= ["35" "55" "other"] (keys (first @looks))))
    ;; Jev stands by its answer: the program stays failed
    (is (not (g/passed? (second-look one-off spec [jev-test] (backing 0.97)))))
    ;; no second look at a test of the spec, or at two wrong numbers
    (is (= one-off (second-look one-off spec [] never)))
    (let [two (outcome (result jev-test "FAIL expected 35, got 55") (result "(= 1 (square-sum 1))" "FAIL expected 1, got 0"))]
      (is (= two (second-look two spec [jev-test "(= 1 (square-sum 1))"] never))))))

(deftest a-second-look-drops-tests-outside-the-domain
  (let [spec "number of ways to choose k items out of n (= 10 (choose 5 2)) (= 1 (choose 4 0))"
        outside "(= 0 (choose 4 6))"
        inside "(= 1 (choose 1 1))"
        result (fn [test detail] {:test test :ok (= detail "PASS") :detail detail})
        outcome {:calls 3 :attempts [{:code "(defn choose [n k] 0)"
                                      :results [(result "(= 10 (choose 5 2))" "PASS")
                                                (result outside "ERROR java.lang.StackOverflowError")
                                                (result inside "ERROR boom")]}]}
        asked (atom nil)
        ;; the first input is not of the examples' kind, the second is
        policy (fn [_ questions]
                 (reset! asked (keys questions))
                 {outside {"type" "noul" "noul" 0.18} inside {"type" "noul" "noul" 0.97}})
        looked (second-look outcome spec [outside inside] policy)]
    (is (= [outside inside] @asked))
    (is (= [outside] (:dropped looked)))
    (is (= [inside] (map :test (remove :ok (:results (g/best looked))))))
    (is (= 4 (:calls looked)))))
