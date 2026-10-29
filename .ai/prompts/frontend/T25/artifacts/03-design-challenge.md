# frontend · T25 · Phase 3 — Design Challenge

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 task implementation brief. T25 is correctly scoped as verification-only: the predicate is already built, and the work is to prove every family of arbitrary URL is refused. The findings are about making AC9, AC12 and the traversal control observable, and about a few near-miss shapes missing from the table.

---

## F1 · AC9 is only tested indirectly

**Severity.** Medium.

**Evidence.**
- The brief states react-router 8.4.0 throws `External navigation is not allowed` for `https://evil.example/…`, `//evil.example/…`, `javascript:alert(1)` and `/\evil.example`.
- `CallbackScreen` calls `navigate(takeReturnTarget())` inside `.then(onFulfilled, discard)`. A synchronous throw from `onFulfilled` is not caught by the second argument, so an unrefused external shape would blank the page after a successful sign-in.
- The planned rows assert the final pathname is `/app/home`, which is evidence the predicate worked, but they do not directly assert that `navigate()` was never called with the external shape.

**Recommended brief amendment.** Add a direct control case: create a fresh memory router and call `navigate('https://evil.example/…')` (or `javascript:…`, `//…`, `/\…`) inside an `act`, and assert react-router throws / the router state does not become the external URL. Then run the real flow with the same shape and assert it ends on home. This pins the measured behaviour from Purpose as a testable property, so AC9 is not only implied by the absence of a blank screen.

**Confidence.** Medium — the final-route assertion is strong indirect evidence, but AC9 deserves its own control.

---

## F2 · AC12 (producer refuses every shape on write) is not explicitly in the row requirements

**Severity.** Medium.

**Evidence.**
- AC12 says "the producer refuses every one of these shapes on write, so none can be stored by a caller."
- The Required Tests section says each row asserts the predicate refuses the shape and the value is gone after the flow.
- It does not require calling `rememberReturnTarget(shape)` and asserting nothing is stored for each family.

**Recommended brief amendment.** Add to each row: `rememberReturnTarget(shape); expect(sessionStorage.getItem(RETURN_TARGET_KEY)).toBeNull();`. This makes AC12 observable family by family, not merely inferred from the shared predicate. It also guards against a future refactor that accidentally decouples the producer's filter from `isAllowlistedTarget`.

**Confidence.** High — AC12 is explicit in the brief but not in the row requirements.

---

## F3 · No discard case covers a refused target

**Severity.** Low.

**Evidence.**
- AC6 says "no discard honours a target, and a discard leaves a refused target where it is."
- T24's discard cases remember an allowlisted target and assert it survives. T25 should assert the same for a refused target.

**Recommended brief amendment.** Add one T25 discard case: seed a refused target (e.g., `/app/home`), trigger any discard shape, and assert the visitor lands on sign-in and `RETURN_TARGET_KEY` still holds the refused value. This is the R8/T24-D14 counterpart for the rejection families.

**Confidence.** Low — the principle is already covered by T24, but the refused-target variant is not.

---

## F4 · Two realistic near-miss shapes are missing from the table

**Severity.** Low.

**Evidence.**
- The todo list includes prefix match, traversal, encoded traversal, query, fragment, etc.
- It does not include a **trailing slash** (`/home/`) or a **URL-encoded allowlisted path** (`/account%2Fsessions`).
- Both are reachable from a hand-edited store and both should be refused by exact set membership. They are the kind of shape a normalisation-based fix would accidentally accept.

**Recommended brief amendment.** Add two rows:
- `/home/` — refused while `/home` is honoured.
- `/account%2Fsessions` — refused while `/account/sessions` is honoured.

These complement the existing near-miss pairs and make the "no normalisation" design observable.

**Confidence.** Low — coverage strengtheners.

---

## F5 · AC4 should be asserted per row, not only as a final sweep

**Severity.** Low.

**Evidence.**
- AC4 requires a refused target to be indistinguishable from no target — pathname, search, rendered text and history action.
- The Open Questions section says the gate must decide whether this is "one comparison over the whole table, or an assertion inside every row."

**Recommended brief amendment.** Require the assertion inside every row. A single final sweep that compares one rejected outcome against one no-target outcome would still pass if one family accidentally produced different copy, as long as the sampled family matched. Per-row AC4 (pathname, search, text, `historyAction`) makes the failing family obvious.

**Confidence.** Low — test maintainability, not behaviour.

---

## F6 · The traversal control should prove harm before proving defense

**Severity.** Low.

**Evidence.**
- AC8 says the same string handed to `navigate()` reaches `/app/admin`, while through `takeReturnTarget()` it reaches `/app/home`.
- The Required Tests mention the traversal control but do not state the exact mechanism.

**Recommended brief amendment.** Add a control case that creates a memory router, calls `navigate('/home/../admin')`, and asserts the resulting pathname is `/app/admin`. Then in the real flow case assert `takeReturnTarget` with the same string returns `/app/home`. This makes AC8 a measured property rather than an asserted one.

**Confidence.** Low — already implied, but worth pinning.

---

## F7 · A mixed shape should be included

**Severity.** Low.

**Evidence.**
- The Required Tests mention a "mixed shape with more than one reason to be refused."
- The todo list does not name one.

**Recommended brief amendment.** Add one mixed row, e.g., `https://evil.example/../admin?x=1#fragment`. It should be refused and land on home, proving that multiple attack families in one string do not create a special case.

**Confidence.** Low — completeness.

---

## F8 · `historyAction` should be asserted as `REPLACE` for every rejection row

**Severity.** Low.

**Evidence.**
- AC4 includes history action in the indistinguishability requirement.
- A rejected target that used `PUSH` would leave the callback reachable with the back button, violating the same reasoning that applies to honoured targets.

**Recommended brief amendment.** Include `expect(arrival.router.state.historyAction).toBe('REPLACE')` in the per-row assertions for every rejection family.

**Confidence.** Low — symmetry with honoured-path testing.

---

## Summary

F1 and F2 are the most important because they make AC9 and AC12 observable rather than inferred. F3-F8 are smaller coverage and consistency items. No production change is anticipated unless Phase 6 finds a shape that is not refused.
