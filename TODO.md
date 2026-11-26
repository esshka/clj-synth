# TODO

Planned work, highest priority first. Move an item to [CHANGELOG.md](CHANGELOG.md) when it ships.

## Hard problems

- [ ] **Make the search use its parts.** Two live runs of `clojure -M:bench split`: 4 and 5 of 8,
  against 4 of 8 for the main functions alone. What is left:
  - `choose` never calls `factorial`: its first program is a recursion, and local repair grows a
    call only around a part that is already in the program.
  - A part that only fits its tests poisons the main function: the second search of `prime?`
    returned such a program, and `prime-sum` built on it failed. A part needs a stronger check
    than its own tests before it joins the options.
  - `longest` returns `(collatz n)`, which fits its four tests. Its spec needs a test where the
    longest start is not the last one.
  - The call around a part passed offline for `lcm` and has no live pass yet.
- [ ] **Let Jev divide: probed, not built.** Several `SPEC` arguments let a person split a hard
  function into parts (`src/cljjev/library.clj`). On 2026-11-26 a split by Jev was probed on the
  mains of the `split` set. Jev picked words of the spec and a shape for a helper (for example
  `prime` and number to true or false), then one question per input asked what the helper returns,
  and an answer at 0.9 or more became a test of the helper.
  - The pick worked: a usable helper for 5 of the 5 mains that have one, none for `lcm` and
    `choose`, whose helper is not named in the prose, and none for 5 of 6 basic specs.
  - The answers gave no wrong test, but tests for only 2 of 5 helpers: `prime?` (13 of 13 sure)
    and the digit sum (13 of 13, ten of them single digits). For the fibonacci numbers and the
    collatz steps Jev was sure of nothing, and its best guesses were wrong (`(fib 3)` as 8 at 0.16).
  - So the open problem is the tests of a helper that returns a number. Other limits seen: a
    helper named by more than three words (`sum of its smaller divisors`), and a helper that
    returns a collection (`palindrome?` needs a reverse).
- [ ] **Push tests down to holes.** After `(if (zero? n) __ __)`, the test `(= 1 (factorial 0))`
  says the first hole is 1. Derive each hole's expected values from the forms above it, so a hole
  is solved and checked alone.
- [ ] **A held-out set of split problems.** The `split` set is the only one, so a change tuned on it
  has no independent check. Several runs are also needed: one run of it is a sample.

## Search quality

- [ ] **Shapes as one-pick options: probed, not built.** On 2026-11-26 one Choice per basic and
  hard spec was asked with and without five templates (fold, list recursion, number recursion,
  loop, every?). A fitting recursion shape sharpened the first pick (`size` 0.27 to 0.59), but it
  also pulled `rev`, `count-evens` and `count-above` off `reduce`, and the fold shape got under
  0.1. Do not add shapes without a rule for when to offer them.
- [ ] **Guard every passing program on two-test specs.** All OVER programs so far (`power`,
  `largest`, `dot`) came from specs with two tests, where no test can be held out; Jev's own edits
  made three of them and local repair one. Showing Jev each program's outputs on new inputs did
  not help: it still picked `power` with `(power 2 4)` = 8, at p = 0.93 against 0.06 for none.
  Generating extra inputs whose answer follows from the spec is the open problem. Shrink now
  removes code the tests ran but never needed (`power` and `largest` in a replay, no live run
  yet). It does not help `dot`, where no part is right, and when two parts each pass alone it
  keeps the larger one by rule, not by evidence.
- [ ] **Repair bugs that need two distant changes.** Local repair fixes a program one small change
  away, or two when the first already brings it closer to passing. Two changes far apart that only
  help together are left to Jev's pair edits, which are few.
- [ ] **Explore near-tied first decisions.** When the top two options of an early decision are within
  a small margin, queue the runner-up as an edit before any Score round, so one unlucky pick cannot
  sink the run. Local repair already covers this for leaves (`<=` vs `<` in `fib`), not for shapes.
- [ ] **Fix the remaining failures:** `take-n` from a `loop` first program, `prime?` and `dot` when
  the first program has the wrong shape, and `spread` on the held-out set.

## Model

- [ ] **A model trained on Clojure code.** Fine-tune or train a model on a Clojure corpus (open
  source libraries, clojure.core, the programs this project finds) so its option probabilities
  reflect idiomatic Clojure. It must keep the policy contract `(fn [state questions] answers)`, so
  it drops in beside `jev/client` and the mock, and be compared with Jev on `clojure -M:bench`
  over several runs.

## Measurement

- [ ] **More hidden checks.** `checks` in `src/cljjev/bench.clj` has two to four tests per spec; a
  program that fits them by chance still counts as a pass.

## Verification

- [ ] **Check the Lean model against the code.** Generate random programs with the Clojure
  generator and check each one against an executable version of `WellFormed`, so a difference
  between `verify/` and `catalog.clj` / `env.clj` is caught by a test.
- [ ] **Formalize left-to-right filling.** Prove that filling holes left to right with
  `env/context` scopes gives the same programs as the top-down `Gen` relation.

## Engineering

- [ ] **Continuous integration.** Run `clojure -M:test`, `clojure -M:lint` and `clojure -M:fmt` on
  every push once the repository has a remote.
- [ ] **Faster test runs.** Local repair runs programs in batches (`batch/run-batch`), but each
  Jev-built program and each trace still starts its own `bb` process, about 0.2 s each.

## Scope

- [ ] Map literals and `get`/`assoc` in the catalog.
- [ ] Destructuring in `let` and `fn` parameters.
