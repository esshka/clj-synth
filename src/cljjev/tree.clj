(ns cljjev.tree
  "Programs as plain Clojure forms (lists, vectors, symbols, literals) with hole maps in
  the unfilled slots. Paths address subtrees; a printer and a reader."
  (:require [clojure.string :as str])
  (:import (java.io PushbackReader StringReader)))

(defn ->hole
  "Returns an unfilled slot of kind (top fname params bindings | num bool seq fn any).
  A plain map, not a record, so it survives namespace reloads at the REPL."
  [kind hint]
  {:cljjev/hole true :kind kind :hint hint})

(defn hole?
  "Returns true if x is a hole."
  [x]
  (and (map? x) (:cljjev/hole x) true))

(defn branch?
  "Returns true if x is a form with children: a list or a vector."
  [x]
  (or (seq? x) (vector? x)))

(defn with-items
  "Returns a form of the same shape as node (list or vector) holding items."
  [node items]
  (if (vector? node) (vec items) (apply list items)))

(def focus-text "__HOLE__")
(def open-text "__")

(defn holes
  "Returns the open holes of node in left-to-right order, as [path hole] pairs."
  ([node] (holes node []))
  ([node path]
   (cond
     (hole? node) [[path node]]
     (branch? node) (mapcat (fn [i c] (holes c (conj path i))) (range) node)
     :else ())))

(defn node-at
  "Returns the subtree of node at path, a vector of child indexes."
  [node path]
  (reduce nth node path))

(defn put-at
  "Returns node with the subtree at path replaced by new."
  [node path new]
  (if (empty? path)
    new
    (let [i (first path)]
      (with-items node (assoc (vec node) i (put-at (nth node i) (rest path) new))))))

(defn head
  "Returns the name of the symbol a list starts with, else nil."
  [node]
  (when (and (seq? node) (symbol? (first node)))
    (str (first node))))

(defn flat
  "Returns node printed on one line. Holes print as __, and the hole at path focus
  as __HOLE__."
  ([node] (flat node nil []))
  ([node focus] (flat node focus []))
  ([node focus path]
   (cond
     (hole? node) (if (= path focus) focus-text open-text)
     (branch? node) (let [inner (str/join " " (map-indexed #(flat %2 focus (conj path %1)) node))]
                      (if (vector? node) (str "[" inner "]") (str "(" inner ")")))
     :else (pr-str node))))

;; items kept on the first line when a form breaks
(def ^:private header-items {"defn" 3 "fn" 2 "let" 2 "loop" 2 "when" 2})

(defn render
  "Returns node printed like flat, but a defn, or any list wider than width, breaks
  over indented lines."
  ([node] (render node nil [] 0 60))
  ([node focus] (render node focus [] 0 60))
  ([node focus path indent width]
   (let [text (flat node focus path)]
     (if (or (not (seq? node))
             (and (not= (head node) "defn") (<= (+ indent (count text)) width)))
       text
       (let [k (get header-items (head node) 1)
             first-line (str/join " " (map-indexed #(flat %2 focus (conj path %1)) (take k node)))
             pad (apply str (repeat (+ indent 2) " "))
             more (for [i (range k (count node))]
                    (str pad (render (nth node i) focus (conj path i) (+ indent 2) width)))]
         (str "(" first-line "\n" (str/join "\n" more) ")"))))))

(defn read-all
  "Returns every top-level form in text, read by the Clojure reader with *read-eval*
  off. Throws on broken brackets."
  [text]
  (binding [*read-eval* false]
    (let [r (PushbackReader. (StringReader. text))]
      (loop [out []]
        (let [x (read {:eof ::eof} r)]
          (if (= x ::eof) out (recur (conj out x))))))))
