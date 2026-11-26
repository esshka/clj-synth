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

(defn- about [spec]
  (let [pools (extract spec [])]
    {:function (first (:names pools)) :meaning (:prose pools) :examples (:tests pools)}))

(defn- call-of [test]
  (second (re-find #"^\(= \S+ (.+)\)$" test)))

(defn- failed-on-jev
  "Returns the failed results of outcome's best program when each is one of Jev's tests."
  [outcome asked]
  (let [failed (remove :ok (:results (g/best outcome)))]
    (when (every? (set asked) (map :test failed))
      failed)))

(defn- without
  "Returns outcome with tests out of its best program's results and in :dropped."
  [outcome tests]
  (if (empty? tests)
    outcome
    (let [at (.indexOf ^java.util.List (:attempts outcome) (g/best outcome))]
      (-> outcome
          (update :dropped (fnil into []) tests)
          (update-in [:attempts at :results] (fn [rs] (vec (remove #((set tests) (:test %)) rs))))))))

(defn- domain-look
  "Drops the tests on which the best program gives an error and whose input Jev says is not
  of the kind the spec's examples show: (= 0 (choose 4 6)) when every example has k at most n."
  [outcome spec asked policy]
  (let [errors (filter #(str/starts-with? (:detail %) "ERROR") (failed-on-jev outcome asked))]
    (if (empty? errors)
      outcome
      (let [answers (policy (about spec)
                            (ordered (for [{:keys [test]} errors]
                                       [test {:type "noul"
                                              :instructions (str "Is " (call-of test) " an input of the same "
                                                                 "kind as the calls in `examples`? Answer no "
                                                                 "when it breaks a condition that every "
                                                                 "example meets.")}])))]
        (without (update outcome :calls inc)
                 (for [{:keys [test]} errors :when (< (get-in answers [test "noul"] 1.0) backed)] test))))))

(defn- value-look
  "Drops the one number test the best program fails when Jev, shown the program's number
  beside its own, no longer backs its first answer, as with (= 35 (square-sum 5))."
  [outcome spec asked policy]
  (let [failed (failed-on-jev outcome asked)
        {:keys [test detail]} (first failed)
        [_ first-answer got] (some->> detail (re-find #"^FAIL expected (-?\d+), got (-?\d+)$"))]
    (if-not (and (= 1 (count failed)) got)
      outcome
      (let [options (ordered (concat (for [n (sort [(parse-long first-answer) (parse-long got)])]
                                       [(str n) (str n)])
                                     [["other" "another number"]]))
            answer (get (policy (about spec)
                                {"look" {:type "choice" :criteria options
                                         :instructions (str "Two answers were given for " (call-of test)
                                                            ". Which number is it?")}})
                        "look")]
        (without (update outcome :calls inc)
                 (when (< (get-in answer ["probabilities" first-answer] 0.0) backed) [test]))))))

(defn second-look
  "Returns outcome with the tests of asked, Jev's tests, that do not hold up taken out of the
  best program's results and listed in :dropped; one wrong test fails a right program. It
  looks only when the best program passes every test of the spec, and makes one request for
  each look it takes.
  ponytail: no look at a wrong yes or no, which has no second answer to show; and the
  search has spent its tries by now, so look inside the search if this fires often."
  [outcome spec asked policy]
  (-> outcome
      (domain-look spec asked policy)
      (value-look spec asked policy)))
