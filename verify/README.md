# Formal verification (Lean 4)

This directory holds a Lean 4 model of cljjev's pure core, and machine-checked proofs of the
guarantees the project claims. It uses only Lean core (no Mathlib).

```bash
cd verify
lake build
```

A successful build means every proof has been checked. No file contains `sorry` or a custom
axiom; the main theorems depend only on Lean's standard axioms (`propext`, `Quot.sound` and, for
the negative examples, `Classical.choice`). You can confirm this with `#print axioms`.

## What is proved

| Theorem | File | Statement |
| --- | --- | --- |
| `gen_defn_wellFormed` | `Soundness.lean` | Every `defn` body the generator can build is well formed (see below). |
| `gen_wellFormed` | `Soundness.lean` | The same for any expression, in any scope the generator can reach. |
| `nodeAt_putAt` | `Tree.lean` | Writing at an existing path, then reading it, returns what was written. |
| `putAt_nodeAt` | `Tree.lean` | Writing back what is already at a path leaves the tree unchanged. |
| `fact_generated` | `Examples.lean` | The generator can build factorial, so the model is not empty. |
| `never_generated` | `Examples.lean` | The generator can never build an unbound name, a non-tail `recur`, or a `recur` of the wrong arity. |

"Well formed" is the independent specification `WellFormed` in `Spec.lean`. It states Clojure's
own static rules and knows nothing about kinds, options or how a program was built:

- every local name is bound by an enclosing `defn`, `fn`, `let`, `loop` or `if-let`; a binding
  init sees only the names bound before it, and an `if-let` name only its then branch;
- `recur` appears only in tail position, with as many arguments as the nearest enclosing `loop`
  or `fn` has names;
- a call to the function being written has one argument per parameter;
- a core function call has as many arguments as its signature.

## How the model maps to the code

| Lean | Clojure |
| --- | --- |
| `Kind`, `fits` | `catalog.clj:86` `fits?` |
| `Node`, `nodeAt`, `putAt` | `tree.clj:40` `node-at`, `tree.clj:45` `put-at` |
| `Gen` constructors `var`, `lit`, `ref`, `core`, `call` | `catalog.clj:206` `exprs` |
| `Gen` constructors `ifE`, `whenE`, `doE`, `letE`, `loopE`, `ifLet`, `fnE` | `catalog.clj:136` `specials` |
| `Gen.recur` (tail and arity premises) | `catalog.clj:155`, offered only when `:tail` and `:recur-arity` allow it |
| binding names and `namesBefore` | `catalog.clj:174` `vectors`; `env.clj:100` `step` (init sees earlier names) |
| `Ctx.inner`, `Ctx.bind`, recur arity in `loopE` and `fnE` | `env.clj:114` (`defn`/`fn`), `env.clj:124` (`let`/`loop`), `env.clj:131` (`if-let`) |
| `defnCtx` | `env.clj:138` `context` at the body of the root `defn` |

## Assumptions and limits

Read these before relying on the proofs. A proof is only as good as the model it is about.

1. **The model is written by hand.** It is not extracted from the Clojure source. The table above
   and the Clojure tests (`catalog_test.clj`, `env_test.clj`) are the link between the two; a
   difference between them would not be caught by Lean.
2. **Top-down derivation versus left-to-right filling.** `Gen` describes a program top-down: each
   child is built in the scope its parent gives it. The real generator fills holes left to right
   and computes each scope from the partial tree with `env/context`. The two agree because
   `context` reads only the ancestors of a hole and their earlier children (parameter and binding
   vectors), and those are always filled before the hole. This argument is not itself formalized.
3. **`Gen` allows more than the generator.** Depth limits, the rule against a bare parameter as the
   whole `defn` body, and parameter kinds only remove options, so the model leaves them out.
   This is safe: soundness of a larger set implies soundness of the real one.
4. **Typed syntax, not raw trees.** `Gen` and `WellFormed` work on `Expr`, one constructor per form,
   rather than on `Node` lists with a head symbol. The printer that turns one into the other is not
   modelled.
5. **Not covered:** `cond` (it follows the `if` rules), the search and its heuristics (priorities,
   Score, edits), the out-of-process test runner, the HTTP client and JSON. The search loop ends
   because it counts its pops against `4 × tries`; this is visible in the code but not proved here.
