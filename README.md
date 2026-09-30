# cljjev

Grammar-constrained synthesis of small Clojure functions, guided by a language model and judged by tests.

cljjev takes a one-line specification with example tests and returns a Clojure function that passes
them. A language model (TypeSafe's Jev) makes every decision, but it never emits source text: at each
unfilled slot in the program it selects one option from a list that contains only legal code. The
resulting program is executed against the tests, and a guided search revises the model's decisions
until the tests pass or the budget is spent.

```bash
clojure -M:run "write a recursive factorial of n (= 120 (factorial 5)) (= 1 (factorial 0))"
```

```clojure
(ns generated.core)

(defn factorial [n]
  (if (<= n 1) 1 (* n (factorial (dec n)))))
```

## Contents

- [Requirements](#requirements)
- [Usage](#usage)
- [Design principles](#design-principles)
- [How a program is found](#how-a-program-is-found)
- [Project layout](#project-layout)
- [Development](#development)
- [Benchmark](#benchmark)
- [Scope and limitations](#scope-and-limitations)

## Requirements

- Clojure CLI 1.12 or later
- JDK 11 or later
- [Babashka](https://babashka.org) (`bb`) or `clojure` on `PATH`, to run the generated tests
- A TypeSafe API key in `TYPESAFE_API_KEY` or `JEV_API_KEY`, read from the environment first and
  then from `./.env`. Without a key, cljjev runs the same search against a deterministic mock policy.

## Usage

### Command line

```bash
clojure -M:run [options] "SPEC"
```

The `(= ...)` forms inside `SPEC` become the tests; the remaining text is the description.

| Option | Default | Description |
| --- | --- | --- |
| `--test FORM` | | Add a test form. Repeatable. |
| `--mock` | | Use the deterministic mock policy instead of the API. |
| `--trace` | | Print every decision to stderr: the hole, the chosen option and its probability. |
| `--pick` | | Experimental repair flow: Jev picks each repair among concrete changes shown as code. |
| `--model MODEL` | `jev-latest` | Jev model to query. |
| `--max-depth N` | `8` | Deepest nesting at which compound forms are offered. |
| `--max-steps N` | `48` | Maximum decisions per program. |
| `--tries N` | `12` | Maximum programs built and tested. |
| `--out FILE` | | Also write the program to `FILE`. |

The program is printed to stdout; progress and test results go to stderr. The exit status is `0`
when all tests pass (or no tests were run), `1` when tests fail, and `2` on error.

### REPL

```bash
clj -M:dev          # plain REPL
clj -M:dev:nrepl    # nREPL server for CIDER, Calva, Cursive and similar
```

`dev/user.clj` provides:

```clojure
(solve "sum the numbers in xs (= 6 (sum-list [1 2 3])) (= 0 (sum-list []))")  ; mock policy
(solve "..." :live true)       ; live API; prints the best program, returns the outcome
(map :edit (:attempts *1))     ; the edit each attempt applied
(test-all)                     ; run every test namespace
(reset)                        ; reload changed namespaces
```

## Design principles

**The model chooses; code constructs.** Jev answers one multiple-choice question (a TypeSafe
`Choice`) per slot. The options are produced by `catalog/legal`, so a generated program cannot
contain unbalanced brackets, unbound names, wrong arities, `recur` outside tail position, or a
collection in a numeric slot. These guarantees live in ordinary, testable code rather than in the
model.

**Tests are the only judge.** Model confidence never marks a program as correct. Correctness is
decided by running the specification's tests in a real Clojure runtime.

**Programs are data.** A program is plain Clojure data (lists, vectors, symbols and numbers) that
is read by the Clojure reader and printed back. An unfilled slot is a map such as
`{:cljjev/hole true :kind "num" :hint "* b"}`, and a path such as `[3 1]` addresses a subtree.
Every transformation (fill, replace, swap, wrap) is a pure function from one tree to another, so
any intermediate program can be inspected, compared with `=`, or saved from the REPL.

**Pure core, effects at the edges.** Construction, scope analysis, scoring and search are pure
functions. The three effects are supplied as arguments: querying the model (`policy`), running
tests (`run`) and tracing values (`trace`). Tests substitute a scripted or mock policy without any
other change.

**A policy is a function.** Every policy has the shape `(fn [state questions] answers)`: the live
client (`jev/client`), the mock (`policy/mock-policy`) and the scripted policies used in tests.
`generate/asker` wraps a policy with `memoize` and a call counter, so an identical question is
never paid for twice.

**Proved, not only tested.** A Lean 4 model in [`verify/`](verify/README.md) proves that every
program the generator can build binds all its names, places `recur` only in tail position with the
right arity, and calls functions with the right number of arguments.

**Validate at the boundary.** Every API answer is checked with `clojure.spec` against the type of
the question it answers. A malformed answer fails immediately, with an error that names the
question and the field.

**Generated code runs out of process.** A generated program may not terminate, and the JVM offers
no safe way to stop a runaway thread. Each test run is therefore a separate `bb` process with a
4-second timeout. The scripts for those runs are built as Clojure forms and printed with `pr-str`,
never assembled from strings.

**REPL first.** No namespace holds hidden state, and holes are maps rather than records, so
`(reset)` reloads changed code while previously built trees remain valid.

## How a program is found

1. **Pools.** The `(= ...)` forms in the specification become tests. The tests also fix the
   function's name, its arity and the kind of each parameter: `(dot [1 2] [3 4])` marks both
   parameters as collections, so neither is ever offered for a numeric slot. Names and numbers in
   the description are the only identifiers and literals available.
2. **Build.** Holes are filled left to right, one `Choice` per hole. Each question includes the
   partial program, the names in scope and the meaning of the enclosing forms (a `fn params` slot
   inside `reduce` is told about `(f acc item)`).
3. **Test.** The completed tree is printed and executed with the tests.
4. **Score.** On failure, every test is re-run, each in its own process, with a spy around each
   chosen subtree. Jev then rates every subtree from 0 (wrong) to 3 (correct), given the values it
   produced on each test beside that test's result, so passing and failing runs can be compared.
   Score batches are sent in parallel.
5. **Edit.** Each decision proposes edits: its next-best options and, for operators such as `-`,
   `<` and `cons`, a swap of the two arguments. The six lowest-rated subtrees are also asked
   whether to keep, replace or wrap them. A wrap keeps the subtree as `a` in `(+ a b)`, `(* a b)`
   and similar forms, and asks only for `b`; this turns `(fib (- n 1))` into
   `(+ (fib (- n 1)) (fib (- n 2)))` in a single edit.
6. **Search.** An edit's priority is `p(option) × suspicion(score)`, halved for each edit away from
   the first program. Every second attempt applies the best pair of non-overlapping edits instead
   of a single one. An edit rebuilds only its own subtree; all other decisions are replayed while
   they remain legal. A rebuilt hole is also shown the parent's test results, the code the edit
   changes with its values, and the last four programs that failed. The first program also gets up
   to four restarts: one decision switched to a close runner-up (at least a fifth of the winner's
   probability), with every later hole asked again, so the search can leave the first program's
   shape. Singles, pairs and restarts take turns. Up to `--tries` programs are tested, and
   duplicates are not counted.

## Project layout

| Path | Responsibility |
| --- | --- |
| `src/cljjev/tree.clj` | Hole maps, paths into forms, printer and reader |
| `src/cljjev/catalog.clj` | Options for each slot, their meanings and slot kinds; `legal` |
| `src/cljjev/env.clj` | Pools from the specification; scope, tail position and recur arity at a path |
| `src/cljjev/generate.clj` | Construction, scoring, edits and search |
| `src/cljjev/trace.clj` | Re-runs each test with spies to record subtree values |
| `src/cljjev/validate.clj` | Shape checks and out-of-process test runs |
| `src/cljjev/jev.clj` | HTTP client for the System One API, with retries and answer validation |
| `src/cljjev/policy.clj` | Deterministic mock policy |
| `src/cljjev/cli.clj`, `src/cljjev/bench.clj` | Command-line and benchmark entry points |
| `dev/user.clj` | REPL helpers |
| `verify/` | Lean 4 model of the core and proofs of its guarantees |
| `docs/` | Static landing page for GitHub Pages |
| `test/cljjev/*_test.clj` | One test namespace per source namespace; shared fixtures in `test_support.clj` |

Runtime dependencies are `org.clojure/data.json` and `org.clojure/tools.cli`; HTTP uses
`java.net.http`.

## Development

```bash
clojure -M:test       # run every *-test namespace
clojure -M:lint       # clj-kondo
clojure -M:fmt        # cljfmt check (clojure -M:fmt/fix to rewrite)
clojure -M:bench      # live benchmark; add --mock for an offline run
cd verify && lake build   # check the Lean 4 proofs
```

Before committing, run the tests, the linter and the formatter check. Record every user-visible
change in [CHANGELOG.md](CHANGELOG.md) as described there. Planned work is tracked in
[TODO.md](TODO.md).

## Benchmark

`clojure -M:bench [--mock] [pick] [basic|hard|held]` runs 15 basic, 14 harder or 12 held-out
specifications (never used for tuning) and reports the
pass rate and the number of API calls. Two live runs on 2026-09-30 with `jev-latest`:

| Set | Passed | API calls |
| --- | --- | --- |
| basic | 15 / 15 and 15 / 15 | 218 and 142 |
| hard | 11 / 14 and 11 / 14 | 580 and 443 |

Failures: `dot` and `prime?` in both runs, `collatz` in one and `take-n` in the other. Model answers
vary between runs, so a single run is a sample rather than a score; compare changes over several runs.

Flows compared on 2026-09-30, two runs each:

| Flow | hard | held | basic |
| --- | --- | --- | --- |
| default (Score and formula) | 12 and 12 / 14 (524, 674 calls) | 11 and 11 / 12 (424, 353) | above |
| `pick` | 10 and 11 / 14 (455, 614 calls) | 11 and 11 / 12 (144, 144) | 15 and 15 / 15 (150, 221) |

The pick flow matches the default on the held-out set with far fewer calls, but trails it on the hard
set, so it stays behind a flag. Every held-out failure was `spread`. In the pick runs its first
program outgrew `--max-steps` and ended the search; that is now fixed, and two later default-flow
runs scored hard 12 and 11 / 14 (466, 468 calls) and held 11 and 11 / 12 (298, 234 calls).

## Scope and limitations

**Supported forms:** `defn fn let loop recur if when cond if-let do quote` and 36 core functions.
`let` and `loop` bind at most three names.

**Not generated:** destructuring, Java interop, protocols, macros and map literals.

**Fault localization is the main bottleneck.** For `take-n`, the search keeps
`(if (> n 0) (cons (first xs) ...) [])` and never adds the missing empty-list check: Jev rates the
stop condition as plausible even though `(first xs)` returns `nil` once `xs` is empty, so no edit
to it ranks highly.

**Results vary between runs.** For `fib`, Jev prefers `<=` over `<` with a probability of only about
0.5, so the same specification can pass in one run and fail in the next.

**Weak tests can be satisfied by wrong programs.** With only `(= 55 (fib 10))`, the search once
returned the sum 1 + 2 + … + n. In one run, `power` passed with `(* (power b (quot e 2)) b)`
for even `e`, which is correct for `(power 2 3)` but not for `(power 2 4)`. Specifications should
include tests that rule out such shortcuts.
