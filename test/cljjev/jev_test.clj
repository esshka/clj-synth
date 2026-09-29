(ns cljjev.jev-test
  (:require [clojure.test :refer [deftest is]]
            [cljjev.jev :as jev]))

(def ^:private check-answers #'jev/check-answers)

(def ^:private questions
  {"prod" {:type "choice" :criteria {"a" "..." "b" "..."}}
   "fit0" {:type "score" :criteria ["Wrong" "Right"]}})

(defn- error-message [answers]
  (try
    (check-answers questions answers)
    nil
    (catch clojure.lang.ExceptionInfo e
      (when (jev/jev-error? e) (ex-message e)))))

(deftest well-formed-answers-pass
  (let [answers {"prod" {"choice" "a" "probabilities" {"a" 0.8 "b" 0.2}}
                 "fit0" {"score" 2}}]
    (is (= answers (check-answers questions answers)))))

(deftest malformed-answers-fail-fast-and-name-the-field
  (is (re-find #"no answer to fit0"
               (error-message {"prod" {"choice" "a" "probabilities" {"a" 1.0}}})))
  (is (re-find #"answer to fit0, field \"score\""
               (error-message {"prod" {"choice" "a" "probabilities" {"a" 1.0}} "fit0" {}})))
  (is (re-find #"answer to prod, field \"probabilities\""
               (error-message {"prod" {"choice" "a"} "fit0" {"score" 1}}))))
