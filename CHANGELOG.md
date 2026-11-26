# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Protocol

1. **Record changes as they land.** Every commit with a user-visible effect adds an entry under
   `[Unreleased]` in the same commit. Internal refactors, test-only changes and formatting do not
   need an entry.
2. **Use the standard sections**, in this order, and only those that apply: `Added`, `Changed`,
   `Deprecated`, `Removed`, `Fixed`, `Security`.
3. **Write for users.** One line per change, stating what changed and why it matters. Name the
   affected option, function or file in backticks. Do not paste commit messages.
4. **Report search changes with numbers.** An entry that can change which programs are found states
   the `clojure -M:bench` pass rate and API calls before and after, over at least two runs.
5. **Mark breaking changes.** Prefix the line with **BREAKING** when it changes a command-line
   option, the policy contract `(fn [state questions] answers)`, or a public function signature.
6. **Release.** Rename `[Unreleased]` to `[X.Y.Z] - YYYY-MM-DD`, add a new empty `[Unreleased]`
   section above it, and update the version in the `User-Agent` header in `src/cljjev/jev.clj`.
   Increment MAJOR for breaking changes, MINOR for new capabilities (forms, edits, options), and
   PATCH for fixes.

## [Unreleased]

### Added

- The second look also checks the domain of Jev's tests (`domain-look` in
  `src/cljjev/oracle.clj`): when the best program passes the spec's tests and gives an error on
  one of Jev's, Jev is asked if that input is of the kind the spec's examples show, and a test
  outside them is dropped. In a probe Jev said no for all three `choose` tests with k above n and
  yes for 173 of 176 tests inside the domain. It has not fired in a live run: `choose`, run twice
  with `--sure --plan`, passed both times with a guard for k above n (48 and 45 API calls; it
  failed in all five earlier `split` runs).

- `--plan` and `clojure -M:bench plan <set>` (`src/cljjev/wording.clj`): one request asks Jev if a
  formula or a repetition gives the result, and for a formula the first step of the build is
  asked as "the last step, the one that gives the final value". `lcm` and `choose` then start
  with a division, not with `(gcd a b)` or a call to themselves. One live run of `sure plan
  split`: 6 of 8 (472 API calls) against 5 and 5 of 8 for `sure split` (875 and 762 calls); the
  new pass, `prime-sum`, came from its part passing in that run. `g/solve` takes `:questions`, a
  map of a hole's hint to the question asked there.

- A second look at one of Jev's tests (`second-look` in `src/cljjev/oracle.clj`): when the best
  program passes every test but one of Jev's number tests, Jev sees its number beside the
  program's, and a first answer it no longer backs is dropped. In the second live run of the
  `sure` sets it fired once and dropped the wrong `(= 35 (square-sum 5))`: held 10 of 12 without
  it, 11 with it, the other sets the same.

- `--sure` and `clojure -M:bench sure <set>` (`src/cljjev/oracle.clj`): one more request asks Jev
  what the function returns for fixed inputs, and each answer at 0.9 or more becomes a test, so
  two weak tests no longer judge alone. Two live runs of each set against the earlier scores:
  basic 15 and 15 of 15 (was 14 and 14, 15 with shrink replayed), hard 13 and 13 of 14 (was 11 and
  11, 12 replayed), held 10 and 11 of 12 (was 11 and 11), split 5 and 5 of 8 (was 5), with no
  program that only fits its tests on basic, hard and held. API calls 221 and 152, 426 and 511,
  426 and 426, 875 and 762 (were about 140, 385, 350 and 707). Of Jev's 257 tests one is wrong,
  `(= 35 (square-sum 5))`; it failed a correct program on the held set in the first run. The
  option is off by default. A function that returns a collection gets no tests.

- `clojure -M:bench flat <set>` (`flat-policy` in `src/cljjev/policy.clj`): the same search with
  no model, every option at the same probability, saved as `<set>-flat`. It measures what Jev
  adds. One run of each set: basic 3 of 15, hard 1 of 14, held 2 of 12, against 14, 11 and 11 with
  Jev; 14 more of the no-model programs only fit their spec's tests.

- Local repair grows a call around a solved part (`grown` in `src/cljjev/mutate.clj`): the part
  may be any argument, and the other arguments are built from locals, so `(gcd a b)` becomes
  `(quot (* a b) (gcd a b))` with no API call. It is active only when parts are on offer, so
  `basic`, `hard` and `held` search as before. A part that fails its tests is searched once more,
  on the command line and in `clojure -M:bench split`. One live `split` run before, 4 of 8 (558
  calls), and one after, 5 of 8 (707 calls); `lcm` is the new pass, but in that run an older
  change found it, and the second search of `prime?` gained nothing. The new change found `lcm`
  offline from the first program of another call.

- Benchmark sets `split` and `whole` (`src/cljjev/bench.clj`): `clojure -M:bench split` solves
  eight hard functions as their parts and then a main function with the solved parts on offer, and
  `whole` solves the same eight main functions alone. One live run of each: 4 of 8 passed after
  their parts (558 API calls, parts included) and 4 of 8 alone (528 calls), the same four.
- `BENCHMARKS.md` (`src/cljjev/report.clj`): tables of every saved run, with each function's
  result, API calls and program, and the `split` set beside `whole`. Every benchmark run writes it
  again, and so does `clojure -M:bench report`.
- Several `SPEC` arguments (`src/cljjev/library.clj`): `clojure -M:run "SPEC" "SPEC" ...` solves
  them in turn, and every function that passes becomes an option for the later ones, as a call
  where its kind fits and by name in a function slot. `--test` goes to the last `SPEC`, and
  `--out` gets every program. A single `SPEC` runs as before, so the benchmark does not change. One
  live run: `prime-sum` (sum of the primes below n) passed in 9 API calls after `prime?` (87
  calls), and failed alone after 142.
- Shrink (`shrink` in `src/cljjev/local.clj`, `hoists` in `mutate.clj`): a program that passes
  every test loses the code the tests ran but never needed. Each expression is replaced by one of
  its own parts where that is legal and drops only code some test ran, all are run in one batch
  with no API call, and the largest part that still passes stays, until none does. Not yet run
  live. Replayed on the passing programs of the six runs of 2026-10-21, scored with the hidden
  checks: basic 14 and 14 of 15 before, 15 and 15 after (`power`); hard 11 and 11 of 14 before,
  12 and 12 after (`largest`); held-out 11 and 11 of 12 before and after. No pass became an OVER,
  and API calls do not change.
- Local repair (`src/cljjev/local.clj`, `mutate.clj`, `batch.clj`): a failed program's small legal
  changes (a new subtree of up to three decisions, another operator, swapped arguments, a wrap) are
  ranked by Jev's build probabilities and up to 6000 run at once with no API call; one `Choice`
  then asks Jev which passing program implements the spec, or none. It found 18 of the passing
  programs over six live runs, among them `take-n` and `collatz`. Two runs per set, scored with the
  hidden checks: basic 14 and 14 of 15 before and after (186 and 180 calls before, 140 and 138
  after), hard 10 and 13 of 14 before, 11 and 11 after (558 and 460 calls before, 378 and 389
  after), held-out 11 and 11 of 12 before and after (330 and 334 calls before, 375 and 325 after).
- Hidden checks in the benchmark (`checks` in `src/cljjev/bench.clj`): a program passes only if it
  also passes tests the search never sees; one that fits only the spec's tests is reported as
  OVER. Rescored this way, the old code already returned an OVER `power` in both basic runs.
- `clojure -M:bench --runs N` repeats a set and prints the median and range of passes and API
  calls; every run is written to `bench/results/<time>-<set>.edn`.
- Lean 4 model and proofs in `verify/`: every program the generator can build binds all its
  names, places `recur` only in tail position with the right arity, and calls functions with the
  right number of arguments. Run `lake build` in `verify/`.
- Restart edits: the first program also gets up to four rebuilds that switch one decision to a
  close runner-up and ask every later hole again, so the search can leave the first program's
  shape (`dot` found `(map * xs ys)` this way). Hard 10 and 10 of 14 before, 11 and 11 after; API
  calls 401 and 444 before, 580 and 443 after. Basic stays 15/15 (218 and 142 calls).
- `--pick` (and `pick` for `clojure -M:bench`): an experimental repair flow where Jev picks the next
  change among concrete ones (restarts, runner-ups, swaps, wraps) shown as code, with every test's
  values and the history of tried changes in one question. Two runs each: hard 10 and 11 of 14 (455
  and 614 calls) against 12 and 12 (524 and 674) for the default flow; held-out 11 and 11 of 12 for
  both, at 144 calls per run against 353 to 424.
- Held-out benchmark set: `clojure -M:bench held` runs 12 specs never used for tuning.

### Changed

- When local repair ran on a program, Jev's edits no longer swap a leaf for a leaf (local repair
  tested those), and a subtree where one local change brought the program closer to passing is
  rated wrong whatever Score says. Only a program that passes at least as many tests as the best
  one so far is repaired, within 30000 local programs per search.
- Score and keep/replace/wrap questions show what each subtree computed on every test, next to
  that test's result, instead of on the first failing test only. Params and names get no values
  rather than "never evaluated".
- A rebuilt hole is shown the parent's test results, the code the edit changes with its values, and
  the last four programs that failed.
- Traces keep the first three values, a count, and the last value of each subtree, so a recursion's
  base case is no longer cut off.

Benchmark (live, two runs each): hard 11 and 10 of 14 before, 10 and 10 after; API calls 324 and
430 before, 401 and 444 after. Basic 15/15 in both runs after (204 and 165 calls). The difference
is within run-to-run noise.

### Fixed

- A program no longer outgrows `--max-steps`: each option is offered only if the steps left can
  still close every open hole. Before, a compound form chosen just before the budget could end the
  whole search when it happened in the first program (seen on `spread`). No change in pass rate:
  hard 12 and 11 of 14, held-out 11 and 11 of 12.

## [0.1.0] - 2026-09-29

### Added

- Grammar-constrained program construction: one TypeSafe `Choice` per hole over the options
  returned by `catalog/legal`, covering `defn fn let loop recur if when cond if-let do quote` and
  36 core functions.
- Test pools: `(= ...)` forms in the specification fix the function name, arity and parameter kinds.
- Repair search: Score-based fault localization with traced subtree values, next-best option edits,
  argument swaps, keep/replace/wrap edits, and pairs of non-overlapping edits.
- Out-of-process test runs through `bb` or `clojure`, with a 4-second timeout.
- `clojure.spec` validation of every API answer; malformed answers fail with the question and field.
- Deterministic mock policy for offline runs (`--mock`).
- Command line (`clojure -M:run`), benchmark (`clojure -M:bench`), and REPL helpers in `dev/user.clj`
  with nREPL (`clj -M:dev:nrepl`) and namespace reloading.
- Development aliases: `:test`, `:lint` (clj-kondo), `:fmt` and `:fmt/fix` (cljfmt).

Benchmark (one live run): basic 15/15 with 196 API calls, hard 10/14 with 374 API calls.
