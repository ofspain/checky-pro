# frontend · T16 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Model** | **Claude Opus 5 — SELF-PRODUCED** |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

> **Normally Kimi 2.7's phase.** Self-produced at the owner's instruction for T16, and labelled so the record is
> accurate.

---

## Method

Reviewed the 31-case suite against the frozen ACs. Rather than reading for gaps, I **mutated for them** — the
reliable way to find an assertion that is not there is to break the thing it should be watching.

Two gaps found, both of the same class, both now closed.

---

## Gaps

### 1. `aria-busy` was added in phase 9 and shipped untested
**Gap ·** Independent-review finding I4 asked for `aria-busy` alongside `aria-disabled`. I added it to the
component. I added no assertion. **Mutant M23 removed the attribute and all 31 cases stayed green.**
**Why it matters ·** Less for the attribute than for the pattern: a review finding applied without a guard is a
change nobody is watching, and it arrives with the false confidence of having been "addressed". This one came
from a review of my own work, in a phase whose entire purpose was to harden the thing.
**Closed ·** `aria-busy` is now asserted `false` at rest and `true` in flight. M23 is caught.

### 2. The landmark check asserted existence, not count
**Gap ·** `expect(container.querySelector('main')).not.toBeNull()`. Two `<main>` elements is a worse
accessibility failure than none — a screen reader's landmark navigation becomes ambiguous — and `querySelector`
cannot distinguish the two cases.
**Closed ·** `querySelectorAll('main')` and `querySelectorAll('[role="status"]')` are both asserted to have
length 1, matching how the `h1` was already checked.

---

## Considered and not raised

- **No test for `location.assign` throwing.** It does not throw for a same-origin navigation; a sandboxed iframe
  could make it, but the SPA is not framed and L9 puts everything on one origin. Not worth a case.
- **No `autoFocus` guard.** Nothing autofocuses today and AC3 would catch a side effect on mount. The focus case
  pins where focus ends up after a click, which is the property L11 cares about.
- **StrictMode is not used in the test's own mount**, though `mountShell` uses it. M3 put a call in the render
  body and six cases caught it, so render-phase side effects are covered by behaviour rather than by harness.
- **`#${describedBy}` would break on an id with CSS-special characters.** The id is a literal in our own source.

---

## Non-gaps

- **31 cases, 22 of 24 mutants caught**; the two unkilled are M18 (equivalent by design — wording is the owner's,
  properties are pinned, M21/M22 prove the property guard has reach) and M24 (a malformed mutant that produced
  unbalanced JSX, so it measured nothing and is excluded rather than counted).
- **R6 is demonstrated against the live platform**, not only asserted: the password field is on SAS's page.
- **The circular import is pinned**, and the guard's real reach is stated honestly — it catches the quiet variant;
  the loud one is caught by the suite collapsing.
- **The harness scores collection failures correctly** after the phase-10 fix.
- **The two environmental failures are correctly distinguished** at last: AC4 is the oxlint summary line, AC2 is
  the missing `pnpm`.

---

## Summary

The suite is strong and, unusually, partly verified in a real browser against the running stack. Both gaps found
here were assertions I failed to write while responding to earlier findings — which is the honest lesson of this
phase: the review that applies a fix is the review most likely to forget to guard it.
