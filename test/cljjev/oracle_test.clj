(ns cljjev.oracle-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.oracle :refer [sure-tests]]))

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
