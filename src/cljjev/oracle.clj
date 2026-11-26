(ns cljjev.oracle
  "Jev's sure answers as more tests. One request asks what the function of a spec returns
  for fixed inputs, and an answer Jev is sure of becomes a test. Two tests judge poorly:
  (first (rest xs)) fits both tests of the largest number in xs."
  (:require [clojure.string :as str]
            [cljjev.catalog :refer [ordered]]
            [cljjev.env :refer [extract]]
            [cljjev.generate :as g]))

;; an answer below this is no test
(def ^:private sure 0.9)
;; in a second look, Jev's first answer stands at this or more
(def ^:private backed 0.5)
;; values a number question offers, beside the numbers of the spec's own tests
(def ^:private offered (range -1 41))

;; argument texts by the kinds of the parameters, which the spec's tests fix.
;; ponytail: a fixed list for each signature seen so far; other signatures get no questions
(def ^:private inputs
  {["num"] (mapv str (range 13))
   ["seq"] ["[]" "[1]" "[1 2 3]" "[3 1 2]" "[2 2]" "[4 0 5]" "[-1 2]"]
   ["num" "num"] ["1 1" "2 3" "3 2" "4 6" "6 4" "5 0" "0 5" "7 7" "2 8" "9 3"]
   ["num" "seq"] ["2 [1 2 3]" "0 [1 2]" "1 [3 1 2]" "3 [2 2]" "2 [4 0 5]" "5 [1]"]
   ["seq" "num"] ["[1 2 3] 0" "[1 2 3] 2" "[4 0 5] 1" "[7] 0"]
   ["seq" "seq"] ["[1 2] [3 4]" "[2] [3]" "[] []" "[1 1 1] [2 2 2]" "[0 5] [5 0]"]
   ["num" "num" "num"] ["5 0 10" "-3 0 10" "12 0 10" "3 1 4" "0 1 4" "9 1 4"]})

(defn- value
  "Returns the text of the value Jev is sure of, or nil."
  [returns answer]
  (if (= returns "bool")
    (let [p (get answer "noul" 0.5)]
      (cond (>= p sure) "true" (<= p (- 1 sure)) "false"))
    (let [choice (get answer "choice")]
      (when (and (not= choice "other") (>= (get-in answer ["probabilities" choice] 0) sure))
        choice))))

(defn sure-tests
  "Returns test forms for the function of spec, one for each fixed input whose value Jev is
  sure of. It makes one request to policy, and none for a function it cannot ask about.
  ponytail: a function that returns a collection gets no tests, since its value is not one
  of a few options."
  [spec policy]
  (let [pools (extract spec [])
        name (first (:names pools))
        returns (get-in pools [:returns name])
        call #(str "(" name " " % ")")
        tests (str/join " " (:tests pools))
        ;; an input the spec already tests needs no question
        asked (remove #(str/includes? tests (call %)) (inputs (get-in pools [:param-kinds name])))]
    (if-not (and (seq asked) (#{"bool" "num"} returns))
      []
      (let [numbers (map parse-long (re-seq #"-?\d+" tests))
            options (ordered (concat (for [n (sort (distinct (concat offered numbers)))] [(str n) (str n)])
                                     [["other" "another number"]]))
            answers (policy {:function name :meaning (:prose pools) :examples (:tests pools)}
                            (ordered (for [input asked]
                                       [input (if (= returns "bool")
                                                {:type "noul" :instructions (str "Is " (call input) " true?")}
                                                {:type "choice" :criteria options
                                                 :instructions (str "What number is " (call input) "?")})])))]
        (vec (for [input asked
                   :let [v (value returns (get answers input))]
                   :when v]
               (str "(= " v " " (call input) ")")))))))

(defn second-look
  "Returns outcome with :dropped, one of Jev's tests in asked, taken out of the best
  program's results when that program passes every other test and Jev, shown the program's
  number beside its own, no longer backs its first answer: one wrong test fails a right
  program, as (= 35 (square-sum 5)) did. Else returns outcome; one request when it asks.
  ponytail: numbers only, a yes or no has no second answer to show; and the search has
  spent its tries by now, so look inside the search if this fires often."
  [outcome spec asked policy]
  (let [best (g/best outcome)
        failed (remove :ok (:results best))
        {:keys [test detail]} (first failed)
        [_ first-answer got] (some->> detail (re-find #"^FAIL expected (-?\d+), got (-?\d+)$"))]
    (if-not (and (= 1 (count failed)) (some #{test} asked) got)
      outcome
      (let [pools (extract spec [])
            call (second (re-find #"^\(= \S+ (.+)\)$" test))
            options (ordered (concat (for [n (sort [(parse-long first-answer) (parse-long got)])]
                                       [(str n) (str n)])
                                     [["other" "another number"]]))
            answer (get (policy {:function (first (:names pools)) :meaning (:prose pools)
                                 :examples (:tests pools)}
                                {"look" {:type "choice" :criteria options
                                         :instructions (str "Two answers were given for " call
                                                            ". Which number is it?")}})
                        "look")
            at (.indexOf ^java.util.List (:attempts outcome) best)]
        (cond-> (update outcome :calls inc)
          (< (get-in answer ["probabilities" first-answer] 0.0) backed)
          (-> (assoc :dropped test)
              (update-in [:attempts at :results] (fn [rs] (vec (remove #(= test (:test %)) rs))))))))))
