(ns user
  "REPL helpers. Start with clj -M:dev, or clj -M:dev:nrepl for an editor."
  (:require [clojure.java.io :as io]
            [clojure.test :as test]
            [clojure.tools.namespace.find :as find]
            [clojure.tools.namespace.repl :as tn]))

(tn/set-refresh-dirs "src" "test")

(defn reset
  "Reloads every namespace whose file changed."
  []
  (tn/refresh))

(defn test-all
  "Runs every *-test namespace under test/."
  []
  (let [nss (filter #(re-find #"-test$" (name %)) (find/find-namespaces-in-dir (io/file "test")))]
    (apply require nss)
    (apply test/run-tests nss)))

;; requiring-resolve, not :require: a broken src file must not stop the REPL from starting
(defn solve
  "Searches for a program meeting spec, with the mock policy unless :live is true.
  Prints the best program and returns the outcome."
  [spec & {:keys [live]}]
  (let [policy (if live
                 ((requiring-resolve 'cljjev.jev/client) ((requiring-resolve 'cljjev.cli/api-key)))
                 ((requiring-resolve 'cljjev.policy/mock-policy)))
        outcome ((requiring-resolve 'cljjev.generate/solve) spec policy)
        best ((requiring-resolve 'cljjev.generate/best) outcome)]
    (println ((requiring-resolve 'cljjev.generate/source) best))
    outcome))

(comment
  (reset)
  (test-all)
  (def out (solve "write a recursive factorial of n (= 120 (factorial 5)) (= 1 (factorial 0))"))
  (map :edit (:attempts out)) ; what each try changed
  (solve "return the nth fibonacci number (= 0 (fib 0)) (= 8 (fib 6)) (= 55 (fib 10))" :live true))
