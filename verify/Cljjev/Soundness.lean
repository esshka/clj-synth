import Cljjev.Spec

/-!
# Soundness: the generator only builds well-formed programs

Main result: `gen_defn_wellFormed`. Every `defn` body the generator can build follows Clojure's
rules from `Spec.lean`: no unbound name, `recur` only in tail position with the right arity, and
right-arity calls.
-/

namespace Cljjev

/-- What the generator's scope means as a specification scope. -/
def Ctx.toScope (c : Ctx) : Scope :=
  { bound := c.locals.map Prod.fst, tail := c.tail, target := c.recurArity, selfArity := c.selfArity }

@[simp] theorem toScope_inner (c : Ctx) : c.inner.toScope = c.toScope.inner := rfl

@[simp] theorem toScope_bind (c : Ctx) (xs : List String) : (c.bind xs).toScope = c.toScope.bind xs := by
  simp [Ctx.bind, Ctx.toScope, Scope.bind, List.map_append, List.map_map, Function.comp_def]

/-- A program that is well formed outside a tail position stays well formed in one: being in
tail position only allows more. -/
theorem WellFormed.to_tail {sigs : String → Option Sig} {s : Scope} {e : Expr}
    (h : WellFormed sigs s e) : WellFormed sigs { s with tail := true } e := by
  induction h with
  | var hx => exact .var hx
  | lit => exact .lit
  | ref => exact .ref
  | core hs hl _ _ => exact .core hs hl (by assumption)
  | call hl _ _ => exact .call hl (by assumption)
  | ifE ha _ _ _ ihy ihn => exact .ifE ha ihy ihn
  | whenE ha _ _ ihb => exact .whenE ha ihb
  | doE ha _ _ ihb => exact .doE ha ihb
  | letE hi _ _ ihb => exact .letE hi ihb
  | loopE hi hb _ _ => exact .loopE hi hb
  | ifLet hinit _ _ _ ihy ihn => exact .ifLet hinit ihy ihn
  | fnE hb _ => exact .fnE hb
  | recur _ htarget hargs _ => exact .recur rfl htarget hargs

theorem gen_wellFormed {sigs : String → Option Sig} {c : Ctx} {k : Kind} {e : Expr}
    (h : Gen sigs c k e) : WellFormed sigs c.toScope e := by
  induction h with
  | var hx _ =>
    exact .var (List.mem_map.mpr ⟨_, hx, rfl⟩)
  | lit => exact .lit
  | ref => exact .ref
  | @core c k f s args hs _ hl _ ih =>
    refine .core hs hl fun a ha => ?_
    obtain ⟨i, h1, rfl⟩ := List.getElem_of_mem ha
    simpa using ih i h1 (hl ▸ h1)
  | call hl _ ih =>
    exact .call hl fun a ha => by simpa using ih a ha
  | ifE _ _ _ iha ihy ihn => exact .ifE (by simpa using iha) ihy ihn
  | whenE _ _ iha ihb => exact .whenE (by simpa using iha) ihb
  | doE _ _ iha ihb => exact .doE (by simpa using iha) ihb
  | letE _ _ ihi ihb =>
    exact .letE (fun i h => by simpa using ihi i h) (by simpa using ihb)
  | @loopE c k bs body _ _ ihi ihb =>
    refine .loopE (fun i h => by simpa using ihi i h) ?_
    have := WellFormed.to_tail ihb
    simpa [Ctx.toScope, Ctx.bind, Scope.target_, Scope.bind, names, List.map_append, List.map_map,
      Function.comp_def] using this
  | ifLet _ _ _ ihinit ihy ihn =>
    exact .ifLet (by simpa using ihinit) (by simpa using ihy) ihn
  | @fnE c k ps body _ _ ihb =>
    refine .fnE ?_
    simpa [Ctx.toScope, Ctx.bind, Scope.target_, Scope.bind, List.map_append, List.map_map,
      Function.comp_def] using ihb
  | recur htail harity _ ih =>
    exact .recur htail harity fun a ha => by simpa using ih a ha

/-- **Main theorem.** Every `defn` body the generator can build is well formed. -/
theorem gen_defn_wellFormed {sigs : String → Option Sig} {ps : List (String × Kind)} {body : Expr}
    (h : Gen sigs (defnCtx ps) .any body) : WellFormed sigs (defnScope (ps.map Prod.fst)) body := by
  simpa [defnCtx, defnScope, Ctx.toScope] using gen_wellFormed h

end Cljjev
