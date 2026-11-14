<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T44 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T44 — Registration accepts 128 characters |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — The upper-bound rows must extend the existing `PASSWORDS` table without restructuring it
· **Severity:** **Blocker/Major**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:233-247` defines a `PASSWORDS` table whose comment explicitly says "TASK 44 ADDS ROWS AND NOTHING ELSE". The brief's L12 forbids restructuring.
· **Assessment:** Three rows are needed: 128 ASCII in range, 128 astral in range, 129 ASCII out of range. They must use the same shape (`name`, `password`, `codePoints`, `inRange`) and the same `it.each` harness as the existing rows. Row names should be descriptive literals (e.g., `'128 ASCII characters, the upper bound itself'`) rather than interpolating 128-character strings, which would be unreadable in test output.
· **Recommended brief amendment:** State the exact three rows and confirm the table's column shape is frozen.

### F2 — AC2 requires a dedicated case that 128 astral code points are 512 UTF-16 units in the DOM
· **Severity:** **Major/Blocker**
· **Evidence:** AC2 claims *"128-code-point astral password (`'👍'.repeat(128)`, 512 UTF-16 units) is accepted ... and the count reports 128 where `String.length` reports 512"*. The existing table-driven case (`:250-274`) asserts `countCodePoints(password)` equals the row's `codePoints`, but it does not assert the DOM value's `.length` is 512.
· **Assessment:** The `.length` bug at the upper bound is the mirror image of the lower-bound bug and is the core falsifiable claim. A dedicated case should type `'👍'.repeat(128)` into the password field and assert `passwordInput(container).value.length === 512` while `countCodePoints(...) === 128`. This is analogous to the existing 12-astral case at `:276-286`.
· **Recommended brief amendment:** Add an AC2 case: "types 128 thumbs-up emoji into the password field, asserts `value.length` is 512 UTF-16 units, `countCodePoints` is 128, and the request goes through unaltered."

### F3 — The table control case must prove the upper bound is load-bearing too
· **Severity:** **Major**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:315-322` currently asserts the `inRange` column has both true and false rows, and that at least one row sits exactly on `ADVISORY_MINIMUM_CODE_POINTS`.
· **Assessment:** With only the lower bound pinned, the table control cannot falsify a future change that silently widens the maximum to 999. It must also assert a row sits exactly on `ADVISORY_MAXIMUM_CODE_POINTS` (128) and a row sits just outside it (129).
· **Recommended brief amendment:** Update the control case to assert rows exist on both `ADVISORY_MINIMUM_CODE_POINTS` and `ADVISORY_MAXIMUM_CODE_POINTS`, and that at least one row is above the maximum.

### F4 — `maxLength`/`minLength` absence must be pinned on both field primitives
· **Severity:** **Major/Blocker**
· **Evidence:** AC4 requires that neither `EmailField` nor `PasswordField` carries `maxLength` or `minLength`. `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` already has a field-pair exception section (`:408-417`) but no constraint-length assertion.
· **Assessment:** A `maxLength={128}` on `PasswordField` would truncate a 128-astral password to 128 UTF-16 units (64 emoji) before React's handler runs. The advisory check would then see 64 code points (in range) and the request would go out with a password the user did not type. The table's body-equality assertion would catch this, but AC4 wants the primitive itself to be pinned so the hazard cannot be introduced in any future task.
· **Recommended brief amendment:** Add a case in `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` that scans `EmailField.tsx` and `PasswordField.tsx` (comments stripped) and asserts neither contains `maxLength` nor `minLength`. The scan should use the existing `withoutComments` helper to avoid false positives from prose.

### F5 — The mutation harness must be re-aimed at the upper bound
· **Severity:** **Major**
· **Evidence:** The brief's required tests include "a fresh copy of T43's 28-row harness, re-aimed" with specific rows that must now behave differently.
· **Assessment:** At least these mutants are upper-bound specific:
  - Replace `countCodePoints(password)` with `password.length` — must now be killed by the 128-astral row (false reject) as well as the lower-bound rows.
  - Change `ADVISORY_MAXIMUM_CODE_POINTS` from 128 to a larger number — must be killed by the 129 row.
  - Delete the `<= ADVISORY_MAXIMUM_CODE_POINTS` half of the comparison — must be killed by the 129 row.
  - Add `maxLength={128}` to `PasswordField` — must be killed by AC4's source scan and/or the 128-astral body-equality assertion.
· **Recommended brief amendment:** List the upper-bound mutants explicitly and their expected kill mechanism (own / inherited / unfalsifiable).

### F6 — No production change is expected; any required change is a deviation
· **Severity:** **Major**
· **Evidence:** The brief states production source is expected to be untouched. `RegisterScreen.tsx:60` already evaluates `length <= ADVISORY_MAXIMUM_CODE_POINTS`, and `ADVISORY_MAXIMUM_CODE_POINTS` is already 128.
· **Assessment:** T44 is a measurement task. If the implementation discovers that the upper bound is not actually wired, that is a T43 defect to report, not a T44 design change. The design challenge therefore focuses entirely on test instrumentation.
· **Recommended brief amendment:** Reiterate: "If a production change appears necessary, pause and file a finding against T43; do not modify production source to make the tests pass."

### F7 — The 129 out-of-range row has no guidance copy by design
· **Severity:** **Minor/Major**
· **Evidence:** D-T44-1 decides the 129 row belongs to this task as *marked and still sent, with no words*. Task 45 will later add the sentence to an existing row.
· **Assessment:** This is the correct decision. Adding a 129 row that asserts `aria-invalid="true"` but no explanatory text is consistent with T43's 11-character row and preserves L11's catalogue-only rule. The row name should make this clear (e.g., "129 ASCII characters, one past the upper bound").
· **Recommended brief amendment:** Confirm the 129 row asserts only `aria-invalid` and request dispatch, matching the existing out-of-range rows.

### F8 — Test password literals must remain non-secret and deterministic
· **Severity:** **Minor**
· **Evidence:** The brief's security constraint says long test passwords should be literal repeats of `'a'` and `'👍'`.
· **Assessment:** Using `'a'.repeat(128)` and `'👍'.repeat(128)` satisfies this. There is no need to generate random strings or pull from fixtures. The repeats are also deterministic across runs and easy to inspect in source.
· **Recommended brief amendment:** None needed; the constraint is already clear.

### F9 — The existing total-function case already touches 128; avoid duplicating task 46's claim
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:325` already asserts `countCodePoints('👍'.repeat(128)) === 128`.
· **Assessment:** This is task 46's territory (the counter counts code points). T44's AC2 should assert the same number through the UI/DOM, not just the helper. The two claims are complementary: task 46 owns the unit, task 44 owns the screen behavior at the boundary.
· **Recommended brief amendment:** Clarify in AC2 that the 128 count is verified via the DOM input value, distinguishing it from the helper unit test.

### F10 — T43's rejection handler and focus guard carry over unchanged
· **Severity:** **Minor**
· **Evidence:** T44's scope excludes rejection messaging (OQ-T43-9), double-submit guarding (OQ-T43-11), and focus management beyond what T43 established.
· **Assessment:** No new design decisions are required for these areas. The harness should not introduce mutants that target them unless they interact with the upper bound (e.g., a long password triggering a different rejection path — but R2/L4 forbid branching on response).
· **Recommended brief amendment:** Note that T43's mutants TR20-TR28 are inherited unchanged unless upper-bound specific.

---

## Summary

T44 is a measurement task whose main design challenge is to add the upper-bound rows to T43's existing table without restructuring it. The critical additions are:

1. **Three `PASSWORDS` rows** (F1): 128 ASCII in, 128 astral in, 129 ASCII out.
2. **A dedicated 128-astral DOM case** (F2) proving 512 UTF-16 units vs 128 code points.
3. **An updated table control** (F3) proving both bounds are load-bearing.
4. **A `maxLength`/`minLength` absence pin** (F4) in the design-system suite.
5. **A re-aimed mutation harness** (F5) targeting the upper-bound mutants.

No production code changes are expected (F6). The 129 row has no copy by design (F7), test literals stay deterministic (F8), and the existing 128 helper assertion should not be confused with task 46's ownership (F9). T43's non-length behavior carries over unchanged (F10).

**Phase 3 complete.** Ten findings returned; no code changed.
