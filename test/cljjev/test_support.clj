(ns cljjev.test-support
  "Shared fixtures: the factorial spec, scripted policies and short builders."
  (:require [cljjev.catalog :refer [legal]]
            [cljjev.env :refer [context extract]]
            [cljjev.generate :as g]
            [cljjev.tree :refer [->hole]]))

(def fact-spec "write a recursive factorial of n (= 120 (factorial 5)) (= 1 (factorial 0))")
(def fact-pools (extract fact-spec []))
(def limits g/default-limits)

(def fact-keys
  ["[n]" "if" "<=" "local:n" "lit:1" "lit:1" "*" "local:n" "call:factorial" "dec" "local:n"])
(def short-keys ["[n]" "if" "<=" "local:n" "lit:1" "lit:1" "*" "local:n" "lit:1"])
(def wrap-keys ["[n]" "if" "<=" "local:n" "lit:1" "lit:1" "call:factorial" "dec" "local:n"])

(defn script
  "Returns a policy that answers each Choice with the next of keys; fails if that key
  was not offered."
  [keys]
  (let [left (atom keys)]
    (fn [state questions]
      (let [k (first @left)]
        (swap! left rest)
        (assert (contains? (get-in questions ["prod" :criteria]) k)
                (str k " not legal at " (:hole state)))
        {"prod" {"type" "choice" "choice" k "probabilities" (assoc {k 0.9} "lit:0" 0.1)}}))))

(defn build
  "Builds factorial answering with keys; decisions in plan are replayed."
  ([keys] (build keys {}))
  ([keys plan] (g/build (g/asker (script keys) (atom 0)) fact-pools plan limits (fn [_]) nil)))

(defn fact [body]
  (list 'defn 'factorial '[n] body))

(defn legal-at
  "Returns the legal options for hole at path in tree."
  ([tree path] (legal-at tree path (->hole "any" "t")))
  ([tree path hole] (legal-at tree path hole fact-pools))
  ([tree path hole pools] (legal hole (context tree path) pools 6)))

(defn attempt [decisions]
  {:code "" :decisions decisions :results [] :edit nil :depth 0})

(defn plan [decisions & changes]
  (g/make-plan (attempt decisions) changes))

(defn at
  "Returns {hole-hint decision-index}."
  [decisions]
  (into {} (map-indexed (fn [i d] [(get-in d [:hole :hint]) i]) decisions)))
