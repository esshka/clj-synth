(ns cljjev.wording
  "Jev picks how the first step is asked. Asked which production fills the body, it takes
  the form that looks most like the task: (gcd a b) for the least common multiple. Asked
  for the last step of the computation, it takes the division around it; but then fib
  starts with its recursive call. So Jev first says which kind of program this is."
  (:require [cljjev.catalog :refer [ordered]]
            [cljjev.env :refer [extract]]))

(def ^:private last-step
  (str "The value of the hole marked __HOLE__ in `partial` is computed in steps. Which option is "
       "the LAST step, the one that gives the final value, so the finished program implements "
       "`spec` and passes `tests`? The arguments of that step are computed before it."))

(def ^:private kind-question
  {:type "choice"
   :instructions "How does a correct program for `spec` get its result?"
   :criteria (ordered [["formula" (str "by a formula: one expression over the inputs and the "
                                       "functions on offer, with no loop and no call to itself")]
                       ["repeat" (str "by repetition: it calls itself, loops, or walks a "
                                      "collection item by item")]])})

(defn questions
  "Returns the :questions of g/solve for spec, a map of hole hint to question: the last-step
  wording at `defn body` when Jev says a formula gives the result, else {}. One request."
  [spec policy library]
  (let [pools (extract spec [])
        state (cond-> {:spec (:prose pools) :tests (:tests pools)}
                (seq library) (assoc :solved (mapv :doc library)))]
    (if (= "formula" (get-in (policy state {"kind" kind-question}) ["kind" "choice"]))
      {"defn body" last-step}
      {})))
