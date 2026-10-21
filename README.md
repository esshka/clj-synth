# cljjev

Grammar-constrained synthesis of small Clojure functions, guided by a language model and judged by tests.

cljjev takes a one-line specification with example tests and returns a Clojure function that passes
them. A language model (TypeSafe's Jev) makes every decision, but it never emits source text: at each
unfilled slot in the program it selects one option from a list that contains only legal code. The
resulting program is executed against the tests. When it fails, thousands of small legal changes to
it are tested locally at once, and a guided search revises the model's decisions until the tests
pass or the budget is spent.

Project page: https://esshka.github.io/clj-synth/

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
decided by running the specification's tests in a real Clojure runtime. When several changed
programs pass, Jev only picks the one that implements the specification, or rejects them all.

**Programs are data.** A program is plain Clojure data (lists, vectors, symbols and numbers) that
is read by the Clojure reader and printed back. An unfilled slot is a map such as
`{:cljjev/hole true :kind "num" :hint "* b"}`, and a path such as `[3 1]` addresses a subtree.
Every transformation (fill, replace, swap, wrap) is a pure function from one tree to another, so
any intermediate program can be inspected, compared with `=`, or saved from the REPL.

**Pure core, effects at the edges.** Construction, scope analysis, scoring and search are pure
functions. The four effects are supplied as arguments: querying the model (`policy`), running
tests (`run`), running many programs at once (`batch`) and tracing values (`trace`). Tests
substitute a scripted or mock policy without any other change.

**A policy is a function.** Every policy has the shape `(fn [state questions] answers)`: the live
client (`jev/client`), the mock (`policy/mock-policy`) and the scripted policies used in tests.
`generate/asker` wraps a policy with `memoize` and a call counter, so an identical question is
never paid for twice.

**Proved, not only tested.** A Lean 4 model in [`verify/`](verify/README.md) proves that every
program the generator can build binds all its names, places `recur` only in tail position with the
right arity, and calls functions with the right number of arguments. Local repair's changes are
programs `build` could make too: a new subtree comes from `legal`, a moved one is checked against
`legal` where it lands, and a test rebuilds every change of a sample program with `build`.

**Validate at the boundary.** Every API answer is checked with `clojure.spec` against the type of
the question it answers. A malformed answer fails immediately, with an error that names the
question and the field.

**Generated code runs out of process.** A generated program may not terminate, and the JVM offers
no safe way to stop a runaway thread. Each test run is therefore a separate `bb` process with a
4-second timeout. Local repair runs thousands of programs in eight `bb` processes instead: each
program is loaded in its own namespace with `eval`, every call and `recur` spends one of 3000
steps, and each test has 300 ms. The scripts for those runs are built as Clojure forms and printed
with `pr-str`, never assembled from strings.

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
4. **Local repair.** A failed program is read back into its decisions, and every small change is
   built from the options `catalog/legal` offers at its place: a new subtree of up to three
   decisions, another operator over the same arguments (`<` to `<=`), swapped arguments, or a wrap
   such as `(and (> n 0) (not (empty? xs)))` around `(> n 0)`. The changes are ranked by Jev's
   probabilities from the build (how unsure Jev was of the old option, how likely it found the new
   one), and the first 6000 are run in one batch with no API call. If some pass every test, one
   `Choice` shows Jev the six likeliest and asks which implements the specification, or none:
   broad search also finds programs like `(cons (first xs) (filter even? xs))` that only fit the
   tests. Otherwise the four programs that came closest (more tests passed, then numbers nearer
   the expected ones) get a second round of smaller changes. The last of three or more tests never
   ranks a program, so passing it is evidence a fix was not chosen to fit the others. Only a
   program that passes at least as many tests as the best one so far is repaired; later ones get
   one round, and a search runs at most 30000 local programs.
5. **Score.** On failure, every test is re-run, each in its own process, with a spy around each
   chosen subtree. Jev then rates every subtree from 0 (wrong) to 3 (correct), given the values it
   produced on each test beside that test's result, so passing and failing runs can be compared.
   Score batches are sent in parallel.
6. **Edit.** Each decision proposes edits: its next-best options and, for operators such as `-`,
   `<` and `cons`, a swap of the two arguments. When local repair ran on the program, a leaf for a
   leaf (`n` for `1`) is left out, since it was already tested, and a subtree where one local change
   brought the program closer to passing is rated wrong whatever Score says. The six lowest-rated
   subtrees are also asked whether to keep, replace or wrap them. A wrap keeps the subtree as `a`
   in `(+ a b)`, `(* a b)` and similar forms, and asks only for `b`; this turns `(fib (- n 1))`
   into `(+ (fib (- n 1)) (fib (- n 2)))` in a single edit.
7. **Search.** An edit's priority is `p(option) × suspicion(score)`, halved for each edit away from
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
| `src/cljjev/mutate.clj` | Small legal changes to a finished program; `sites` reads a program back into its decisions |
| `src/cljjev/local.clj` | Local repair: ranks and runs the changes, and asks Jev to pick among those that pass |
| `src/cljjev/batch.clj` | Runs thousands of programs on the same tests in a few `bb` processes |
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
clojure -M:bench      # live benchmark; add --mock for an offline run, --runs N to repeat
cd verify && lake build   # check the Lean 4 proofs
```

Before committing, run the tests, the linter and the formatter check. Record every user-visible
change in [CHANGELOG.md](CHANGELOG.md) as described there. Planned work is tracked in
[TODO.md](TODO.md).

## Benchmark

`clojure -M:bench [--mock] [pick] [--runs N] [basic|hard|held]` runs 15 basic, 14 harder or 12
held-out specifications (never used for tuning) N times, reports the pass rate and the number of
API calls of each run with their median and range, and writes every run to `bench/results/`. A
program passes only if it also passes hidden checks (`checks` in `src/cljjev/bench.clj`) that the
search never sees; one that passes the spec's tests but not the checks is reported as OVER.

Two live runs per set on 2026-10-21 with `jev-latest`, before local repair and after, both scored
with the hidden checks:

| Set | Before: passed | Before: API calls | After: passed | After: API calls |
| --- | --- | --- | --- | --- |
| basic | 14 and 14 / 15 | 186 and 180 | 14 and 14 / 15 | 140 and 138 |
| hard | 10 and 13 / 14 | 558 and 460 | 11 and 11 / 14 | 378 and 389 |
| held | 11 and 11 / 12 | 330 and 334 | 11 and 11 / 12 | 375 and 325 |

The pass rate is the same within run-to-run noise: the old code alone scored 10 and 13 on the hard
set. Basic and hard need about a quarter fewer API calls. Before, the failures were `take-n`,
`dot`, `prime?` and `collatz` on hard, `spread` on held, and an OVER `power` on basic in both runs.
After, local repair found 18 of the passing programs across the six runs, `take-n`, `collatz` and
`prime?` among them, each when the first program had the right shape. The failures are `take-n`
(a `loop` first program) and `dot` and `prime?` once each on hard, `spread` on held,
and OVER programs: `power` in both basic runs and `largest` twice and `dot` once on hard, all on
specs with only two tests. The hard OVERs came from Jev's own edits, `power` from local repair.
Model answers vary between runs, so a single run is a sample rather than a score; compare changes
over several runs.

Flows compared on 2026-09-30, two runs each, before local repair and the hidden checks:

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

**A wrong first shape is the main bottleneck.** Local repair fixes a program a few decisions away
from correct: the missing empty-list check in `take-n` is one wrap, and `collatz`'s `3x` instead of
`3x + 1` is one `(+ ... 1)`. A first program with the wrong shape, such as `prime?` built from
nested `cond`s or `spread` from trees of `(max 0 0)`, is beyond it, and only Jev's restarts and
edits can change the shape.

**Local repair has fixed limits.** A change adds at most three decisions, the first program gets
two rounds and later ones one, and a search runs at most 30000 local programs. Each local test has
3000 steps and 300 ms, so a correct program that needs more (`(fib 20)` makes 21891 calls) fails
there, though the normal test run passes it.

**Results vary between runs.** For `fib`, Jev prefers `<=` over `<` with a probability of only about
0.5, so the same specification can pass in one run and fail in the next.

**Weak tests can be satisfied by wrong programs.** With only `(= 55 (fib 10))`, the search once
returned the sum 1 + 2 + … + n. With `(= 8 (power 2 3))` and `(= 1 (power 5 0))`, both the old and
the new search return `power` with `(* (power b (quot e 2)) b)` for even `e`, which is wrong for
`(power 2 4)`. Broad local search finds more such programs; Jev's pick among the passing ones
rejects many of them but not all. The benchmark runs hidden checks the search never sees and
reports such a program as OVER, not as a pass. Specifications should include tests that rule out
shortcuts.
