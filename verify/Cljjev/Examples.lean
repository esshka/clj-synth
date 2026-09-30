import Cljjev.Soundness

/-!
# Examples: the model is not empty, and the specification is not trivial

* The generator can build factorial, so `Gen` does not hold vacuously.
* The specification rejects an unbound name, a `recur` outside tail position and a `recur` with the
  wrong arity. By soundness, the generator can never build any of them.
-/

namespace Cljjev

/-- The three core functions factorial needs, with their `catalog/core-fns` signatures. -/
def factSigs : String → Option Sig
  | "<=" => some ⟨.bool, [.num, .num]⟩
  | "*" => some ⟨.num, [.num, .num]⟩
  | "dec" => some ⟨.num, [.num]⟩
  | _ => none

/-- `(if (<= n 1) 1 (* n (factorial (dec n))))` -/
def factBody : Expr :=
  .ifE (.core "<=" [.var "n", .lit .num])
       (.lit .num)
       (.core "*" [.var "n", .call [.core "dec" [.var "n"]]])

theorem fact_generated : Gen factSigs (defnCtx [("n", .num)]) .any factBody := by
  have hn : ∀ c : Ctx, c.locals = [("n", .num)] → Gen factSigs c .num (.var "n") :=
    fun c hc => .var (by simp [hc]) (fits_refl _)
  refine .ifE ?_ (.lit (fits_any_slot _)) ?_
  · refine .core rfl (fits_refl _) rfl fun i h1 _ => ?_
    match i, h1 with
    | 0, _ => exact hn _ rfl
    | 1, _ => exact .lit (fits_refl _)
  · refine .core rfl (fits_any_slot _) rfl fun i h1 _ => ?_
    match i, h1 with
    | 0, _ => exact hn _ rfl
    | 1, _ =>
      refine .call rfl fun a ha => ?_
      simp only [List.mem_singleton] at ha
      subst ha
      refine .core rfl (fits_any_slot _) rfl fun j hj _ => ?_
      match j, hj with
      | 0, _ => exact .var (kx := .num) (by simp [Ctx.inner, defnCtx]) (fits_refl _)

theorem fact_wellFormed : WellFormed factSigs (defnScope ["n"]) factBody :=
  gen_defn_wellFormed fact_generated

/-- `m` is not bound in `(defn f [n] m)`. -/
theorem unbound_rejected : ¬ WellFormed factSigs (defnScope ["n"]) (.var "m") := by
  intro h
  cases h with
  | var hx => simp [defnScope] at hx

/-- `(* (recur n) 1)`: the `recur` is an argument, not in tail position. -/
def nonTailRecur : Expr := .core "*" [.recur [.var "n"], .lit .num]

theorem nonTailRecur_rejected : ¬ WellFormed factSigs (defnScope ["n"]) nonTailRecur := by
  intro h
  cases h with
  | core _ _ hargs =>
    have hr := hargs (.recur [.var "n"]) (by simp)
    cases hr with
    | recur htail _ _ => simp [Scope.inner] at htail

/-- `(recur n n)` in a one-parameter function has the wrong arity. -/
def wrongArityRecur : Expr := .recur [.var "n", .var "n"]

theorem wrongArityRecur_rejected : ¬ WellFormed factSigs (defnScope ["n"]) wrongArityRecur := by
  intro h
  cases h with
  | recur _ htarget _ => simp [defnScope] at htarget

/-- The generator can never build a body with an unbound name, a non-tail `recur`, or a `recur`
of the wrong arity. -/
theorem never_generated (k : Kind) :
    ¬ Gen factSigs (defnCtx [("n", k)]) .any (.var "m") ∧
    ¬ Gen factSigs (defnCtx [("n", k)]) .any nonTailRecur ∧
    ¬ Gen factSigs (defnCtx [("n", k)]) .any wrongArityRecur :=
  ⟨fun h => unbound_rejected (gen_defn_wellFormed h),
   fun h => nonTailRecur_rejected (gen_defn_wellFormed h),
   fun h => wrongArityRecur_rejected (gen_defn_wellFormed h)⟩

end Cljjev
