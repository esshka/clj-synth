import Cljjev.Gen

/-!
# Clojure's rules, stated on their own

`WellFormed sigs s e` is the specification: the static rules a Clojure program must follow, with
no reference to kinds, options or how the program was built.

* every local name is bound by an enclosing `defn`, `fn`, `let`, `loop` or `if-let`
  (a binding init sees only the names bound before it; an `if-let` name only in its then branch);
* `recur` appears only in tail position, and has as many arguments as the nearest enclosing
  `loop` or `fn` has names;
* a call to the function being written has one argument per parameter;
* a core function call has as many arguments as its signature.
-/

namespace Cljjev

structure Scope where
  bound : List String
  tail : Bool
  target : Option Nat
  selfArity : Nat

namespace Scope

def inner (s : Scope) : Scope := { s with tail := false }

def bind (s : Scope) (xs : List String) : Scope := { s with bound := xs ++ s.bound }

/-- The body of a `loop` or `fn`: a tail position, and the target of `recur`. -/
def target_ (s : Scope) (xs : List String) : Scope :=
  { s.bind xs with tail := true, target := some xs.length }

end Scope

inductive WellFormed (sigs : String → Option Sig) : Scope → Expr → Prop where
  | var {s x} : x ∈ s.bound → WellFormed sigs s (.var x)
  | lit {s k} : WellFormed sigs s (.lit k)
  | ref {s f} : WellFormed sigs s (.ref f)
  | core {s f g args} :
      sigs f = some g → args.length = g.args.length → (∀ a ∈ args, WellFormed sigs s.inner a) →
      WellFormed sigs s (.core f args)
  | call {s args} :
      args.length = s.selfArity → (∀ a ∈ args, WellFormed sigs s.inner a) →
      WellFormed sigs s (.call args)
  | ifE {s a y n} :
      WellFormed sigs s.inner a → WellFormed sigs s y → WellFormed sigs s n →
      WellFormed sigs s (.ifE a y n)
  | whenE {s a b} :
      WellFormed sigs s.inner a → WellFormed sigs s b → WellFormed sigs s (.whenE a b)
  | doE {s a b} :
      WellFormed sigs s.inner a → WellFormed sigs s b → WellFormed sigs s (.doE a b)
  | letE {s bs body} :
      (∀ i (h : i < bs.length), WellFormed sigs (s.inner.bind (namesBefore bs i)) bs[i].2) →
      WellFormed sigs (s.bind (names bs)) body →
      WellFormed sigs s (.letE bs body)
  | loopE {s bs body} :
      (∀ i (h : i < bs.length), WellFormed sigs (s.inner.bind (namesBefore bs i)) bs[i].2) →
      WellFormed sigs (s.target_ (names bs)) body →
      WellFormed sigs s (.loopE bs body)
  | ifLet {s x init y n} :
      WellFormed sigs s.inner init → WellFormed sigs (s.bind [x]) y → WellFormed sigs s n →
      WellFormed sigs s (.ifLet x init y n)
  | fnE {s ps body} :
      WellFormed sigs (s.target_ ps) body → WellFormed sigs s (.fnE ps body)
  | recur {s args} :
      s.tail = true → s.target = some args.length → (∀ a ∈ args, WellFormed sigs s.inner a) →
      WellFormed sigs s (.recur args)

/-- The scope of a `defn` body with parameters `ps`. -/
def defnScope (ps : List String) : Scope :=
  { bound := ps, tail := true, target := some ps.length, selfArity := ps.length }

end Cljjev
