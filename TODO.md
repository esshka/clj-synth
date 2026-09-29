# TODO

Planned work, highest priority first. Move an item to [CHANGELOG.md](CHANGELOG.md) when it ships.

## Search quality

- [ ] **Localize faults from traced values, not only from Score.** `take-n` fails because Jev rates
  `(> n 0)` as plausible even when the trace shows it true after `(first xs)` returned `()`. Rank a
  subtree as suspect when its values contradict the expected result of the failing test.
- [ ] **Explore near-tied first decisions.** When the top two options of an early decision are within
  a small margin (for `fib`, `<=` vs `<` at p ≈ 0.5), queue the runner-up as an edit before any
  Score round, so one unlucky pick cannot sink the run.
- [ ] **Guard against programs that only fit the given tests.** Hold one test out of the search and
  accept a passing program only if it also passes the held-out test (see `power` in the README).
- [ ] **Fix the remaining hard failures:** `take-n`, `dot`, `digit-sum`, `prime?`.

## Model

- [ ] **A model trained on Clojure code.** Fine-tune or train a model on a Clojure corpus (open
  source libraries, clojure.core, the programs this project finds) so its option probabilities
  reflect idiomatic Clojure. It must keep the policy contract `(fn [state questions] answers)`, so
  it drops in beside `jev/client` and the mock, and be compared with Jev on `clojure -M:bench`
  over several runs.

## Measurement

- [ ] **Report the benchmark over several runs.** Add a `--runs N` option to `clojure -M:bench` that
  prints the median and range of passes and API calls per set, since single runs vary.
- [ ] **Keep benchmark results as data.** Write each run to `bench/results/<date>.edn` so changes can
  be compared without re-running.

## Engineering

- [ ] **Continuous integration.** Run `clojure -M:test`, `clojure -M:lint` and `clojure -M:fmt` on
  every push once the repository has a remote.
- [ ] **Faster test runs.** Keep one `bb` process alive and send it programs over its nREPL server,
  restarting it after a timeout; each run currently pays about 0.2 s of process start-up.

## Scope

- [ ] Map literals and `get`/`assoc` in the catalog.
- [ ] Destructuring in `let` and `fn` parameters.
