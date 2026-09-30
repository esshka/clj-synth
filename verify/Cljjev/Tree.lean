/-!
# Program trees and paths

Model of `src/cljjev/tree.clj`. A program is a tree of symbols, literals, lists and vectors, with
holes in the unfilled slots. A path is a list of child indexes (`[3 1]` in Clojure).

`nodeAt` models `node-at`, `putAt` models `put-at`.
-/

namespace Cljjev

/-- Slot kinds, as in `catalog.clj`. -/
inductive Kind where
  | num | bool | seq | fn | any
  deriving DecidableEq, Repr

/-- `catalog/fits?`: a form returning `r` may fill a slot expecting `e`. -/
def fits (r e : Kind) : Prop := e = .any ∨ r = .any ∨ r = e

theorem fits_any_slot (r : Kind) : fits r .any := Or.inl rfl

theorem fits_any_value (e : Kind) : fits .any e := Or.inr (Or.inl rfl)

theorem fits_refl (k : Kind) : fits k k := Or.inr (Or.inr rfl)

inductive Node where
  | sym (name : String)
  | lit (text : String)
  | lst (items : List Node)
  | vec (items : List Node)
  | hole (kind : Kind) (hint : String)

namespace Node

/-- The children of a list or vector. -/
def items : Node → Option (List Node)
  | lst xs => some xs
  | vec xs => some xs
  | _ => none

/-- `tree/with-items`: a node of the same shape holding `ys`. -/
def withItems : Node → List Node → Node
  | lst _, ys => lst ys
  | vec _, ys => vec ys
  | n, _ => n

theorem items_withItems {n : Node} {xs : List Node} (h : n.items = some xs) (ys : List Node) :
    (n.withItems ys).items = some ys := by
  cases n <;> simp_all [items, withItems]

theorem withItems_self {n : Node} {xs : List Node} (h : n.items = some xs) : n.withItems xs = n := by
  cases n <;> simp_all [items, withItems]

end Node

/-- `tree/node-at`. -/
def nodeAt : Node → List Nat → Option Node
  | n, [] => some n
  | n, i :: p =>
    match n.items with
    | some xs =>
      match xs[i]? with
      | some c => nodeAt c p
      | none => none
    | none => none

/-- `tree/put-at`. A path that does not exist leaves the tree unchanged. -/
def putAt : Node → List Nat → Node → Node
  | _, [], x => x
  | n, i :: p, x =>
    match n.items with
    | some xs =>
      match xs[i]? with
      | some c => n.withItems (xs.set i (putAt c p x))
      | none => n
    | none => n

/-- Writing `x` at an existing path, then reading that path, gives `x`. -/
theorem nodeAt_putAt {t x c : Node} {p : List Nat} (h : nodeAt t p = some c) :
    nodeAt (putAt t p x) p = some x := by
  induction p generalizing t c with
  | nil => simp [putAt, nodeAt]
  | cons i p ih =>
    unfold nodeAt at h
    split at h
    next xs hxs =>
      split at h
      next c' hc =>
        have hi : i < xs.length := (List.getElem?_eq_some_iff.mp hc).1
        simp only [putAt, hxs, hc, nodeAt, Node.items_withItems hxs, List.getElem?_set_self hi]
        exact ih h
      next => simp at h
    next => simp at h

/-- Writing back what is already at a path leaves the tree unchanged. -/
theorem putAt_nodeAt {t c : Node} {p : List Nat} (h : nodeAt t p = some c) : putAt t p c = t := by
  induction p generalizing t c with
  | nil => simp_all [putAt, nodeAt]
  | cons i p ih =>
    unfold nodeAt at h
    split at h
    next xs hxs =>
      split at h
      next c' hc =>
        have hi : i < xs.length := (List.getElem?_eq_some_iff.mp hc).1
        have hget : xs[i] = c' := (List.getElem?_eq_some_iff.mp hc).2
        simp only [putAt, hxs, hc, ih h]
        rw [← hget, List.set_getElem_self]
        exact Node.withItems_self hxs
      next => simp at h
    next => simp at h

end Cljjev
