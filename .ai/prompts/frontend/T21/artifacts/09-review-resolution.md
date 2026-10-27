# frontend · T21 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Human Approval (packet assembled by Claude Opus 5) |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Seven independent-review findings and seven self-review findings, all decided. **Every citation in Phase 8 was
checked against the source and every one is accurate.** One code change applied, one acceptance criterion
corrected, one finding already closed before the review ran.

---

## Two corrections to Phase 8 before the ledger

**1 · "Incomplete without its verification half" is the pipeline, not a defect.** Phase 8's summary says the
implementation "is incomplete without its verification half" and I1 says to add the tests "before treating the
implementation as complete". Phase 6's own prompt says *"Do NOT write tests here (that is Phase 10)"*, and Phase 6's
notes said the suite total deliberately did not move for that reason. I1 is **accepted as a fact and scheduled
where the pipeline puts it**; it is not a Phase 6 omission and Phase 6 is not reopened.

**2 · I6 was already decided, and Phase 8 could not have seen it.** Its header lists `Consumes:
artifacts/07-self-review.md` only — not the frozen brief — so it re-raised the composition root as an open
question. **T21-D5** (`artifacts/04-frozen-task-brief.md` §1) decided it in the negative two phases earlier, and
its reasoning is almost word for word I6's own "if no" branch: record the deferral explicitly, name the owner, and
prove AC5/AC6 by isolated use of the real consumers. Nothing to do. Worth noting the review reached the same
conclusion independently.

**3 · The suite does run here.** Phase 8 again notes that `vite-plugin-pwa` is missing in its environment so
`vitest` cannot start, making its review static-only — honestly disclosed. On this host the package is present and
the suite ran throughout Phase 6 (892 passed / 1 failed of 893).

---

## Independent review (Phase 8)

| # | Finding | Decision |
|---|---|---|
| I1 | The named test's in-memory half is not committed | **Accepted as scheduled — Phase 10.** |
| I2 | AC9 is literally false as written | **Accepted — AC9 corrected below as T21-D8.** |
| I3 | `adopt` and `retainAccessToken` both assign `held` | **Accepted — applied now.** |
| I4 | `retainAccessToken` validates nothing | **Accepted as documentation — applied with I3.** |
| I5 | `renew()` could become an unhandled rejection | **Accepted as scheduled — Phase 10.** |
| I6 | The composition root remains unwired | **Already closed by T21-D5.** No action. |
| I7 | A forged callback link clears an existing session | **Accepted — Phase 10 pins it, Phase 13 records it.** |

**I1 · accepted, Phase 10.** Correct and comprehensive, and its minimum set matches the frozen brief's §7 almost
exactly. Phase 10 writes: retention over two unrelated values; `current()` null before and after every failure
path; `adopt()` with and without a token held; the real holder through the real `createSessionBoundary` asserting
`Authorization: Bearer <token>`; the real holder through the real `shareRenewalAcrossTabs`; non-leakage sweeps;
and the source scans.

**I2 · accepted, and this is the review's most valuable finding** — it is also the one the self-review reached
independently (F1), from the same evidence. **AC9 is replaced**, and the replacement is frozen as:

> **T21-D8 · AC9, corrected.** *T21 introduces no copy of the token of its own, and holds no durable second copy
> anywhere: one module variable in `accessToken.ts`, and nothing in React state, a prop, a `globalThis` property,
> another module-level binding in `src/auth`, or a thrown message.* The client boundary's **transient
> per-request** copies — the token string in a `pending` map entry (`sessionBoundary.ts:100`, `:108`) and a cloned
> `Request` already carrying the bearer header (`:89`, `:104`), both deleted at `:114`/`:134` — are a built,
> tested mechanism outside this task, and AC5 *requires* the header copy to exist. Task 22's sweep is the right
> place to state that none of them reaches storage.

The literal wording could only have been satisfied by an implementation that does not exist, so a test written
against it would have failed, and a test written to pass anyway would have asserted nothing. That is this
package's recurring defect, caught before the test existed rather than after.

**I3 · accepted, applied.** `adopt` now delegates: `adopt(token) { retainAccessToken(token); }`. One place in the
file assigns a non-null token, one clears. Both comments were updated to say why two names remain — `adopt` is the
cross-tab contract's word, and the callback screen is not adopting anything from a sibling — so the delegation
does not read as an accident. The self-review (F3) left this for Phase 9 deliberately so Phase 8 could disagree on
the record; it agreed.

**I4 · accepted as documentation, applied in the same edit.** Both reviews independently preferred documenting
over guarding, for the same reason. `retainAccessToken`'s comment now names **both** guards that make the absence
safe — `codeExchange.ts:74-81` for the exchange path and `crossTabRenewal.ts:54` for the cross-tab path — so the
missing third check reads as a decision rather than an oversight. No guard added: an empty string cannot reach
either caller, and a silent `if (token === '') return;` would be an un-killable line, which T20 already rejected
once on the same ground.

**I5 · accepted, Phase 10.** One case drives `accessToken.renew()` through the real boundary and asserts the
rejection is handled — `onRenewalFailed` once, the fixed message, the caller's original 401, and no unhandled
rejection in the run's exit code. No production change: a `renew()` that resolved or swallowed would be the defect.

**I7 · accepted.** Its recommendation is better than the self-review's (F2), which only recorded the trade-off:
Phase 10 adds a case where an **already-signed-in tab** arrives at a forged callback and ends with `current()`
null and the ordinary neutral discard outcome. That turns a known consequence into pinned behaviour, so a later
reader cannot mistake it for a regression. Phase 13 records it in the PR notes.

## Self-review (Phase 7)

| # | Finding | Decision |
|---|---|---|
| F1 | AC9 literally false | **Accepted** → T21-D8, same as I2. |
| F2 | Forged link clears a session | **Accepted**, strengthened by I7 → Phase 10 case. |
| F3 | Three assignment sites | **Accepted — applied** (I3). |
| F4 | `renew()` unhandled rejection | **Accepted as scheduled** → Phase 10 (I5). |
| F5 | The seam's message must never become copy | **No change** — recorded as a constraint on task 33, carried to Phase 13. |
| F6 | `clearAccessToken` has one caller | **Keep** — `agents.md:35-36` names the sign-out behaviour, and T21-D3 put it here because `LocalSession` cannot express it. |
| F7 | No validation in the holder | **Accepted as documentation — applied** (I4). |

## Changes applied

| File | Change | Finding |
|---|---|---|
| `frontend/src/auth/accessToken.ts` | `adopt` delegates to `retainAccessToken`; both comments updated to name the reason and both callers' guards. | I3, I4, F3, F7 |

Nothing else was touched. No refactoring, no optimization, no public API change, no rename — the exported surface
is identical.

## Verification after the change

- `tsc --noEmit` clean. `oxlint` exit **0**.
- Suite **892 passed / 1 failed of 893** — the known environmental `oxlint` AC4 case and no other, and no `Errors`
  line, so no unhandled rejection. Unchanged from Phase 6, as a pure delegation should leave it.

## Open questions

**None new.** The frozen brief's §6 still records the two pieces of work the specification never assigned — silent
re-authentication and the composition root (the latter decided for T21 by T21-D5, and owned by the first task that
makes an authenticated call).

---

## Next — Phase 10 — test generation. It owes I1, I5, I7 and the AC9 rewrite of T21-D8.
