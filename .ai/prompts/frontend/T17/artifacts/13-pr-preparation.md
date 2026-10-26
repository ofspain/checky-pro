# frontend · T17 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` — **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12's verdict is **PASS**, so this phase proceeds.

---

## Commit title

```
frontend T17: discard a callback that arrives without a usable state
```

## Commit message

```
frontend T17: discard a callback that arrives without a usable state

Add the screen the Spring Authorization Server returns to. A callback
without a usable `state` is discarded: nothing is exchanged, and the
visitor is returned to the sign-in start with neutral copy that names no
cause. This is the first half of R8; task 18 adds the mismatch
comparison at the line marked for it, and both halves verify the same
named test.

The task as specified cannot fail on its own. "No exchange happened" is
unobservable while the exchange is task 20's: the assertion would pass
against an empty file, and would keep passing once task 20 wired an
exchange that fired unconditionally. So add codeExchange.ts as a real
collaborator with a real call site, spy it from every discard case, and
drive one positive case through the path that does call it. Without the
positive case the arrangement would be decorative.

Carry the discard notice in memory, module-scoped and single-read,
rather than in sessionStorage - L16's carve-out is exhaustive and names
four items, and this is not one of them - and not in history state,
which survives a reload, wrong for a notice that should die with the
navigation that caused it.

Discard an authorization error response too, and one carrying both an
error and a code: RFC 6749 has the server send one or the other, so a
response with both did not come from this flow intact.

Leave the three stored items untouched. Deleting them is task 23's by
name, nothing is leaked by keeping them, and premature deletion would
make a discarded callback indistinguishable from one that arrived with
no stored attempt at all.

Pin the second strand of the circular import T16 closed. routes.tsx
imports this screen before it declares SIGN_IN_PATH, so a module-scope
read would touch the binding inside its temporal dead zone, where Vite
yields undefined rather than throwing and the target silently becomes
'/undefined'. Vitest's module resolution does not reproduce that, so an
indentation scan is the only thing that can catch it - and the scan is
itself fed a module-scope use, because an unproven regex in that
position is a silent hole rather than a weak test.

Verified end to end in a real browser against the live stack: both
discard shapes land on /app/sign-in with byte-identical copy, and a real
authorization code returns to /app/callback with the state matching
byte-for-byte what task 15 stored.

Spec: frontend task 17; R8; L4, L9, L11, L16; D-1..D-12.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

**Note on the trailer.** The generated prompt for this phase asks for a `Claude Opus 4.8 (1M context)` trailer.
That string is stale in the template — this task was run by Opus 5, and every T17 commit already carries
`Co-Authored-By: Claude Opus 5`. Worth regenerating the templates for; it is a `.ai/generate.py` concern, not a
T17 one, and `.ai/` is not hand-edited.

---

## Files changed

Nine files, **+770 / −4**, across `421df5f1..HEAD`.

| File | Change | Lines |
|---|---|---|
| `src/auth/CallbackScreen.tsx` | **new** — the screen | 82 |
| `src/auth/codeExchange.ts` | **new** — the seam (D-1, D-2) | 21 |
| `src/auth/signInNotice.ts` | **new** — in-memory, single-read (D-3) | 26 |
| `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | **new** — 15 blocks, **73 cases**; task 18 extends it | 606 |
| `src/auth/SignInScreen.tsx` | takes and renders the notice | +7 |
| `src/app/routes.tsx` | `element` on `callback`; `SIGN_IN_PATH` and `CALLBACK_PATH` | +17/−2 |
| `src/i18n/catalogue.ts` | `callback.heading`, `callback.working`, `signIn.discarded` | +9 |
| `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` widened (T02) | +1/−1 |
| `src/app/bundleContainsNoSecretEnvKeys.test.ts` | one justified URL added (T13) | +4 |

No dependency added; `package.json` is untouched. No file outside this list was modified.

---

## Summary

T17 implements the missing-state half of R8. Three new modules and one new suite, plus the minimum ripple into
four existing files — two of them other tasks' guards that correctly fired and were updated in place with a
justification at the line.

Two decisions shaped the result. First, the **seam**: the task's central assertion is an absence, and an absence
is only testable against a present collaborator, so `codeExchange.ts` exists for task 20 to fill and for T17 to
watch. Second, the **in-memory notice**: the one fact a discard must carry forward is carried without touching
storage, which keeps L16's carve-out exhaustive and makes the notice die with the navigation that caused it.

Two review rounds produced fourteen findings between them. All but two were accepted; the two rejected are
recorded with reasons, one of them because it was factually wrong about work already done. Three of the accepted
findings changed production code — the dropped rejection, the untrimmed whitespace, and the error-plus-code
response.

---

## Testing performed

- **`callbackWithMismatchedStateIsDiscarded`: 73 cases, all passing.** 15 named blocks covering the six discard
  shapes, the seam's absence with a positive control, the return navigation, the storage boundary on both paths,
  the notice's single read, StrictMode, accessibility, copy properties, the source scans, the circular-import
  strand, and the shapes a real authorization server sends.
- **Whole suite: 634 passed, 1 failed (635).** The failure is the known environmental
  `lintRejectsHandWrittenBackendFetch` AC4 — this host's `oxlint@1.87.0` prints no summary line for the
  assertion to match. Pre-existing, unrelated, and not T17's; AC2 of that file, the `spawn pnpm ENOENT` one,
  passes under an `npx -y pnpm@12.14.0` invocation. Delta from T16's 561 is **+73**, which is exactly this
  task's cases and no other change.
- **`tsc --noEmit` clean. `oxlint` clean.**
- **Mutation testing: 16 mutants across phases 9–11, 16 killed.** Every accepted finding was undone and the
  suite re-run; none of the remediation is decorative. The harness compares collected totals rather than failure
  counts, because a mutant that stops a file loading shows fewer tests rather than more failures.
- **Real browser against the live stack** (11 containers, Caddy edge on `localhost:5173`). Discard: `/app/callback`
  and `/app/callback?code=injected-code` both land on `/app/sign-in` with **byte-identical** copy. Success: a
  real MERCHANT sign-in through auth's single-step login returned a real authorization code to `/app/callback`
  with the `state` **matching byte-for-byte** what T15 stored, and the screen held on "Checking your sign-in…"
  — D-6's deliberate no-op, observed.

---

## Specification references

- **Task:** `spec/frontend/tasks.md` task 17 — "Missing state discarded."
- **Requirement:** **R8** (`requirements.md:25-26`), missing-state half; the mismatch half is task 18.
- **Named test:** `callbackWithMismatchedStateIsDiscarded` (`package.md` §8). The header's second named test,
  `stateSurvivesRedirectAndIsConsumedOnce`, is **task 23's** — see OQ-5.
- **LOCKED decisions:** **L4** (enumeration-safe copy), **L9** (single origin), **L11** (accessibility, no
  default 404), **L16** (the only sessionStorage carve-out).
- **Task decisions:** **D-1**..**D-11** (frozen at phase 4), **D-12** (phase 12 — `error` with `code` is a
  discard).

---

## Merge notes

Work is on `spec/service-specs-and-ai-framework`, branched off `main`; `main` stays deployable. **T17 alone does
not complete a sign-in** — D-6 — so whoever sequences a deployment should land tasks 18 and 20 with it. That is
stated rather than implied, and it is the owner's call.

---

## Open questions at hand-off

**OQ-3** — the callback copy is unreviewed; properties enforced by test, wording the owner's, in one file.
**OQ-5** — the generated header lists task 23's named test; `.ai/generate.py` will reproduce that in other task
headers. Both **gate nothing**.

---

## Next

**T17 is complete — 14 of 14 phases.** Next task is **T18 — mismatched state**, which extends
`callbackWithMismatchedStateIsDiscarded.test.tsx` and fills `CallbackScreen.tsx:67`.
