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
