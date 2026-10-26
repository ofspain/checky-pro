# frontend · T17 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 17):**
> **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

No production code was changed in this phase.

---

## What this phase added

Phase 6 wrote 47 cases and phase 9 took it to 56. Auditing AC1–AC17 for genuine gaps rather than padding the
count produced **six cases in three groups** — 56 → **62**. All three groups were mutation-verified.

### 1. AC12 — the second strand of the circular import (the one that mattered)

T16 closed one cycle: `routes → SignInScreen → authorizeRequest → routes`, and pinned its safety with a source
scan proving `APP_BASENAME` is read only inside a function body. **T17 added a second strand that nothing
pinned:** `routes → CallbackScreen → routes`, because the screen imports `SIGN_IN_PATH` to know where to send a
discarded callback.

`routes.tsx` imports `CallbackScreen` at line 6 and declares `SIGN_IN_PATH` at line 13, so a module-scope read
in the screen would touch the binding inside its temporal dead zone. T16 measured what happens then: **Vite
yields `undefined` rather than throwing**, so the target would silently become `'/undefined'` and land on the
catch-all, which renders nothing. AC12 asked for the cycle guard to hold with the second strand, and it did not
exist.

Two cases now: the indentation scan mirroring T16's technique, and a dynamic import in the application's own
load order asserting `SIGN_IN_PATH` arrives as `'sign-in'` and the built target contains no `'undefined'`.

**One honest observation from killing this mutant.** Hoisting the read to module scope fails **only** the source
scan — 61 of 62 cases still pass, and no render or dynamic-import case notices. Vitest's module resolution does
not reproduce the production TDZ failure. So the scan is not belt-and-braces over a runtime assertion; **it is
the only thing in the suite that can catch this class of defect**, which is exactly why T16 wrote one and why
T17 needed its own.

### 2. AC16 — the drain, as a unit

D-3 specified "one case asserts the drain works: set, take, take again → `null`". Phase 6 covered the *rendered*
path — a first sign-in screen shows the notice and a second does not — but never the primitive. The mutant that
drops the clear from `takeSignInNotice` is caught by the rendered cases too, but the unit case localises the
failure to the one function instead of to four screens.

### 3. R8 boundaries — shapes a real authorization server sends

- **The documented OAuth error response.** The server returns `error` *instead of* `code`, keeping `state`:
  `?error=access_denied&error_description=…&state=xyz`. T17 discards it for want of a code, which is right, and
  the case records that as deliberate rather than incidental. It also asserts the server's own
  `error_description` never reaches the visitor — L4 neutrality against a string the *backend* chose, which
  nothing else in the suite covered. Task 19 owns showing anything about an authorization error.
- **A duplicated security parameter.** `?code=abc&state=first&state=second` is a parameter-pollution shape.
  `URLSearchParams.get` returns the first, so that is what the exchange receives and what task 18 will compare
  against the stored value. Pinned rather than left to the reader.

---

## Test manifest

`frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` — 13 blocks, 35 declarations, **62 cases**
(six `it.each` tables expand). This is the named test `callbackWithMismatchedStateIsDiscarded` from
`package.md` §8.

| Block | Cases | Verifies |
|---|---|---|
| a callback with no usable state is discarded | 13 | AC1, AC2, AC3, R8 |
| nothing is exchanged | 10 | AC4 (+ F1, F2 from phase 9) |
| the return navigation | 3 | AC5, AC17 |
| session storage is left exactly as T15 wrote it | 2 | AC7, AC8, L16 |
| the notice is in memory and single-read | 2 | AC6, AC16 |
| it handles the callback once, under StrictMode | 3 | AC14, D-8 (+ F4) |
| accessibility and copy | 5 | AC9, AC10, L4, L11 |
| the new files touch nothing they must not | 16 | AC15, D-9 (+ F6) |
| **the second strand of the circular import stays safe** | **2** | **AC12** |
| **the notice drains exactly once, as a unit** | **2** | **AC16, D-3** |
| **the shapes a real authorization server can send** | **2** | **R8 boundaries** |
| the seam is honest about being unfinished | 2 | AC4, D-1, D-2 |

**Coverage of the frozen criteria.** AC1–AC10 and AC14–AC17 are asserted in this file. **AC11** (T01, T02, T13,
T16 stay green) and **AC13** (no new dependency, measured suite delta) are regression obligations discharged by
running the suite, not by a case — recorded as such rather than faked with an assertion. **AC12** moved from
uncovered to covered in this phase.

### Non-vacuity

Every matcher in this file is fed a deliberately wrong input, per the standing rule: the seam spy is proved to
register a call by a positive case; the five AC15 patterns each carry their own violation in the same record
(phase 9's F6); the copy-property word list is proved to have reach against `'Your state parameter was
invalid'`; and the L4 identical-copy assertion compares the six discard shapes to **each other** rather than to
the catalogue.

### Mutation verification

| Mutant | Undoes | Result |
|---|---|---|
| M8 | hoists the `SIGN_IN_PATH` read to module scope | **killed** — 1 failed, 61 of 62 |
| M9 | makes the notice re-deliverable | **killed** — 4 failed, 58 of 62 |
| M10 | accepts a grant with no code | **killed** — 8 failed, 54 of 62 |

Earlier mutants M1–M7 (phase 9) remain killed. The harness compares collected totals, not failure counts.

---

## Conventions

`agents.md`'s JUnit/Testcontainers/contract-test conventions are **backend** conventions and do not apply to
this package: the frontend's equivalents are Vitest with jsdom, which is what every T01–T16 suite uses. No
Clock is involved — nothing in T17 reads time. No contract test applies: T17 makes no backend call, and the one
contract touching it is auth's registered redirect URI, which AC17 now pins by its literal spelling and which
phase 6 verified against the live stack.

---

## Open questions

**OQ-5 (new, gates nothing) — this prompt's header lists a named test that is not T17's.**
`stateSurvivesRedirectAndIsConsumedOnce` is **task 23's** by name (`tasks.md:35`: "*`state`, `nonce`, and
`code_verifier` survive the full-page redirect in session storage and are **deleted at callback***"). It appears
in T17's header because `.ai/generate.py` lists every named test mapping to a scoped requirement ID and both map
to R8 (`package.md:159`).

It was **not** implemented here, deliberately, on three grounds: the guardrail restricts this phase to T17; D-7
locked deletion as task 23's by name, with the reasoning recorded; and T17's **AC8 pins the opposite** — the
three items are still present after a discard. Implementing it would have contradicted a locked decision of
this very task. Flagged so phase 12 does not read the header as an unmet obligation.

**OQ-3 — the callback copy is unreviewed.** Unchanged. **Gates nothing.**

**Carried, gating nothing ·** T16's OQ-1; T12's OQ-1; `design.md` §4c's stale heading and its claim that auth
T19 is "not built"; `package.md:172` mapping `serviceWorkerUpdateWaitsForReload` to R57.

---

## Next

**Phase 11 — test review. This is Kimi 2.7's phase, and I am stopping here for it.**
