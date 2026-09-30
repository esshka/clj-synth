import Cljjev.Tree

/-!
# What the generator can build

`Gen sigs c k e` holds when the generator can fill a slot of kind `k`, in scope `c`, with the
complete expression `e`. Each constructor is one option that `catalog/legal` offers, and the
scope each child hole sees is the one `env/context` computes for it.

The model may allow more than the real generator, never less: depth limits, the "no bare param as
the defn body" rule and parameter kinds only remove options, so they are left out. Soundness of a
larger set implies soundness of the real one.

Expressions here are typed syntax, not raw `Node` trees: each constructor is one Clojure form, in
the shape the catalog builds it. `cond` is left out; it is `if` with more branches.
-/

namespace Cljjev

/-- A complete expression, one constructor per form the catalog can produce. -/
inductive Expr where
  /-- `local:x` -/
  | var (x : String)
  /-- `lit:...`, of the given kind -/
  | lit (k : Kind)
  /-- `ref:f`, a function passed by name into a `fn` slot -/
  | ref (f : String)
  /-- a core function call, e.g. `(+ a b)` -/
  | core (f : String) (args : List Expr)
  /-- `call:f`, a call to the function being written -/
  | call (args : List Expr)
  | ifE (test yes no : Expr)
  | whenE (test body : Expr)
  | doE (a b : Expr)
  | letE (binds : List (String × Expr)) (body : Expr)
  | loopE (binds : List (String × Expr)) (body : Expr)
  | ifLet (x : String) (init yes no : Expr)
  | fnE (params : List String) (body : Expr)
  | recur (args : List Expr)

/-- A core function's signature: its return kind and argument kinds (`catalog/core-fns`). -/
structure Sig where
  ret : Kind
  args : List Kind

/-- The scope of a hole, as `env/context` returns it. -/
structure Ctx where
  locals : List (String × Kind)
  tail : Bool
  recurArity : Option Nat
  selfArity : Nat

namespace Ctx

/-- Scope inside a non-tail position: an argument, a test, a binding init. -/
def inner (c : Ctx) : Ctx := { c with tail := false }

/-- Scope with new local names. Names bound by `let`, `loop`, `fn` and `if-let` have kind `any`. -/
def bind (c : Ctx) (xs : List String) : Ctx :=
  { c with locals := xs.map (fun x => (x, Kind.any)) ++ c.locals }

end Ctx

/-- The names bound before binding `i`: an init sees only those (`env/step`). -/
def namesBefore (bs : List (String × Expr)) (i : Nat) : List String :=
  (bs.take i).map Prod.fst

def names (bs : List (String × Expr)) : List String := bs.map Prod.fst

inductive Gen (sigs : String → Option Sig) : Ctx → Kind → Expr → Prop where
  | var {c k x kx} :
      (x, kx) ∈ c.locals → fits kx k → Gen sigs c k (.var x)
  | lit {c k kl} :
      fits kl k → Gen sigs c k (.lit kl)
  | ref {c f} :
      Gen sigs c .fn (.ref f)
  | core {c k f s args} :
      sigs f = some s → fits s.ret k → args.length = s.args.length →
      (∀ i (h1 : i < args.length) (h2 : i < s.args.length), Gen sigs c.inner s.args[i] args[i]) →
      Gen sigs c k (.core f args)
  | call {c k args} :
      args.length = c.selfArity → (∀ a ∈ args, Gen sigs c.inner .any a) →
      Gen sigs c k (.call args)
  | ifE {c k a y n} :
      Gen sigs c.inner .bool a → Gen sigs c k y → Gen sigs c k n → Gen sigs c k (.ifE a y n)
  | whenE {c k a b} :
      Gen sigs c.inner .bool a → Gen sigs c k b → Gen sigs c k (.whenE a b)
  | doE {c k a b} :
      Gen sigs c.inner .any a → Gen sigs c k b → Gen sigs c k (.doE a b)
  | letE {c k bs body} :
      (∀ i (h : i < bs.length), Gen sigs (c.inner.bind (namesBefore bs i)) .any bs[i].2) →
      Gen sigs (c.bind (names bs)) k body →
      Gen sigs c k (.letE bs body)
  /-- The generator keeps the outer tail flag inside `loop`; Clojure itself allows more. -/
  | loopE {c k bs body} :
      (∀ i (h : i < bs.length), Gen sigs (c.inner.bind (namesBefore bs i)) .any bs[i].2) →
      Gen sigs { c.bind (names bs) with recurArity := some bs.length } k body →
      Gen sigs c k (.loopE bs body)
  | ifLet {c k x init y n} :
      Gen sigs c.inner .any init → Gen sigs (c.bind [x]) k y → Gen sigs c k n →
      Gen sigs c k (.ifLet x init y n)
  | fnE {c k ps body} :
      fits .fn k →
      Gen sigs { c.bind ps with tail := true, recurArity := some ps.length } .any body →
      Gen sigs c k (.fnE ps body)
  | recur {c k args} :
      c.tail = true → c.recurArity = some args.length → (∀ a ∈ args, Gen sigs c.inner .any a) →
      Gen sigs c k (.recur args)

/-- The scope of a `defn` body: its params are the locals, it is a tail position, and both
`recur` and a call to the function itself take one argument per param. -/
def defnCtx (params : List (String × Kind)) : Ctx :=
  { locals := params, tail := true, recurArity := some params.length, selfArity := params.length }

end Cljjev
