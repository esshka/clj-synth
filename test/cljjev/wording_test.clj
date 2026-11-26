(ns cljjev.wording-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.generate :as g]
            [cljjev.policy :refer [mock-policy]]
            [cljjev.test-support :refer [fact-pools limits]]
            [cljjev.wording :refer [questions]]))

(deftest jev-picks-the-wording-of-the-first-step
  (let [seen (atom nil)
        saying (fn [kind] (fn [state qs]
                            (reset! seen [state (keys (get-in qs ["kind" :criteria]))])
                            {"kind" {"type" "choice" "choice" kind "probabilities" {kind 1.0}}}))
        spec "least common multiple of a and b (= 12 (lcm 4 6))"
        gcd {:doc "(gcd a b) greatest common divisor of a and b"}
        formula (questions spec (saying "formula") [gcd])]
    (is (re-find #"LAST step" (formula "defn body")))
    (is (= [{:spec "least common multiple of a and b" :tests ["(= 12 (lcm 4 6))"] :solved [(:doc gcd)]}
            ["formula" "repeat"]]
           @seen))
    (is (= {} (questions spec (saying "repeat") [])))))

(deftest a-question-replaces-the-wording-at-its-hole-only
  (let [asked (atom [])
        mock (mock-policy)
        policy (fn [state qs]
                 (swap! asked conj [(get-in state [:hole :hint]) (get-in qs ["prod" :instructions])])
                 (mock state qs))]
    (g/build (g/asker policy (atom 0)) (assoc fact-pools :questions {"defn body" "ASKED THIS"}) {}
             limits (fn [_]) nil)
    (is (= "ASKED THIS" (some (fn [[hint text]] (when (= hint "defn body") text)) @asked)))
    (is (every? #(re-find #"^Which production" (second %)) (remove #(= "defn body" (first %)) @asked)))))
