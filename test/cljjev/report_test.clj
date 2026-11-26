(ns cljjev.report-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [cljjev.report :refer [render]]))

(defn- result [spec status calls]
  {:spec spec :passed (not= status :fail) :checked (= status :pass) :calls calls
   :program "(defn f [x] x)"})

(deftest tables-come-from-the-saved-runs
  (let [gcd "greatest common divisor of a and b (= 6 (gcd 12 18))"
        lcm "least common multiple of a and b (= 12 (lcm 4 6))"
        file (fn [date set runs] {:date date :set set :flow :score :runs runs})
        text (render [(file "2026-10-21T22:14:42.1" "hard" [[(result gcd :pass 10)] [(result gcd :over 12)]])
                      (file "2026-11-26T10:53:32.5" "split"
                            [[(assoc (result lcm :pass 30) :parts [(result gcd :pass 10)])]])
                      (file "2026-11-26T11:00:00.0" "whole" [[(result lcm :fail 140)]])])]
    (is (str/includes? text "| `lcm` | `gcd` | PASS | 30 | FAIL | 140 |"))
    (is (str/includes? text "| `gcd` (part) | PASS | 10 |"))
    ;; a file saved before parts existed, with two runs
    (is (str/includes? text "| `gcd` | PASS, OVER | 10, 12 |"))
    (is (str/includes? text "| 2026-10-21 22:14 | `hard` | :score | 0 | 1 | 0 | 12 |"))))
