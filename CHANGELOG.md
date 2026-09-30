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
