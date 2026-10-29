# frontend · T28 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

T28 is a verification task, so the implementation IS the test file. Phase 10 extends it if the Phase 8 and
Phase 9 gates ask for more; nothing here is a placeholder.

---

## What changed

| File | Change |
|---|---|
| `frontend/src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx` | **New.** 39 cases in 10 `describe` blocks. |

**No production file changed.** Verified: `git diff --name-only 2cdb1215..HEAD -- frontend/src` names this one
file. `CallbackScreen.tsx:84-85` already cites task 28 and needed nothing.

Ten blocks for eleven ACs: **AC8 and AC9 share the catalogue's data** but are separate blocks, and **AC1 and
AC2 share one block for the ordering property** (Required Test 2), since the claim is that an `error=` arrival
is indistinguishable across three `state` shapes — which is an AC2 equivalence stated about an AC1 path.

---

## The four measurements the plan ordered, and what they returned

Taken in a scratch file under `src/auth/`, read from the failure output, then deleted. None survives in the
repo.

### M1 — the console is spyable, and asserting *silence* would have been wrong

All six channels (`log`, `info`, `warn`, `error`, `debug`, `trace`) are spyable with `vi.spyOn` before the
screen mounts, and the real discard flow writes **nothing**. But the measurement returned something the plan
did not anticipate: this suite's jsdom environment emits React's own

    "The current testing environment is not configured to support act(...)"

through `console.error`, twice per arrival. A sweep asserting *nothing was written to any channel* would have
failed on ambient noise with no connection to T28.

**Consequence, and it improves the claim.** The sweep asserts that nothing written to any channel **contains**
the error code, description or URI — which is what R10 actually requires — rather than that nothing was
written at all. A sentinel per channel proves each of the six is read, and `watchConsole`'s channel list is the
thing the sentinel case iterates, so a channel dropped from the watcher fails that case.

### M2 — the four failure kinds are already identical, in every field measured

`error=` (with a description naming two-factor enrollment), a mismatched `state`, a refused exchange and a bad
`nonce` all produced, byte for byte:

```
destination    /app/sign-in          historyAction  REPLACE
search         ""                    hash           ""
liveRegions    1                     liveText       ["Your sign-in did not complete. Try again."]
storedKeys     [code_verifier, nonce, state]        activeElement  BODY
text           "Sign in… Continue to sign in Your sign-in did not complete. Try again."
```

So **AC2 needed no production change**, and the `error_description` naming two-factor enrollment did not appear
in the rendered text. Also measured: the `error=` arrival made **zero** `fetch` calls, which is AC1's "never
exchanges" and is now its own case.

### M3 — a success differs strongly, so the blindness control has teeth

`/app/home`, `text` `""`, `liveRegions` `0`, `storedKeys` `[]`. Four fields move, and the control names all
four rather than resting on `not.toEqual`, which would also pass if only one did. T27 could not use this
control shape because its success path rendered nothing *and* its failure path rendered nothing; here the
discard renders copy, so the struct's `text` field carries real weight.

### M4 — the notice cannot be a struct field, and the trap was worse than the plan predicted

The plan expected that *taking* the notice in the test would change the copy it was also measuring. Measured,
it is worse and simpler: `SignInScreen.tsx:22` takes it in a `useState` initialiser, so by the time any test
can look the screen has **already consumed** it and `takeSignInNotice()` returns `null` — after a discard and
after a success alike.

So a struct field reading the notice would have been `null` on every path: identical for the right and the
wrong reason, which is the exact shape of an assertion that cannot fail. **Removed from the struct**, observed
instead through `liveText`, which is where the notice reaches the visitor anyway. Recorded as **T28-D3**.

---

## How each AC is discharged

| AC | Where | Note |
|---|---|---|
| AC1 | `AC1:` 3 cases | destination, `REPLACE`, catalogue sentence, no token held, **and zero fetch calls** |
| AC2 | `AC2:` ordering (1) + equivalence (3) | whole-struct `toEqual`, plus the success control and the not-a-shell control |
| AC3 | `AC3:` 2 cases | 8 named causes incl. the vendor code, `error_description`, `error_uri` |
| AC4 | `AC4:` 4 cases | 5 surfaces × 3 needles, a sentinel on each surface, 6 channels, the late-watcher control |
| AC5 | `AC5:` 6 cases | permitted table, Kimi's sentence, 9 phrasings, the permitted-copy guard, emptiness, stripping |
| AC6 | `AC6:` 3 cases | path scan, the innocent-body control, the not-too-wide control |
| AC7 | `AC7:` 4 cases | scan, role gate, 5 account-state gates, **and the silence-on-OAuth-`state` case** |
| AC8 | `AC8:` 6 cases | value grammar, key grammar, emptiness, the six-key pin, the seventh-key control |
| AC9 | `AC9:` 3 cases | distinct, cause-free, one at a time |
| AC10 | `AC10:` 1 case | from `DENYLISTED_NAVIGATION_PATHS` |
| AC11 | `AC11:` 2 cases | the signpost table, marked as such; one real case driving the shared guard |

### The three patterns, measured against the tree before being written in

| Pattern | Hits in production source |
|---|---|
| `ENROLLMENT_SOURCE` (incl. `two-factor`, `second factor`, `pending-enrollment`) | **0** |
| `ACCOUNT_STATE_SOURCE` (incl. role comparisons) | **0** |
| enrollment-shaped **path** | **0** of 24 files |
| `FORBIDDEN_COPY_SOURCE` over the catalogue | **0** of 10 values |
| a bare `state ===`, for contrast | **3** — `CallbackScreen.tsx` ×2, `registerServiceWorker.ts` |

So `PERMITTED_HITS` is **empty**, which is the measured fact and not an unfilled table.

---

## My own self-audit, run because 38 of 38 passed on the first attempt

A file whose subject is absence passing immediately is not reassuring, so I audited every `toEqual([])` for
the shape that passes when the instrument read nothing. **Three real holes, all fixed before the commit:**

1. **The permitted-hit accounting was untested.** `PERMITTED_HITS` is empty, so the counting logic never ran
   against anything. A future row with a wrong count would have gone unnoticed. **Fixed:** extracted
   `unexplained(hits, rows)` and added a control that drives it with synthetic rows — the right count admits
   both hits, a count one short reports the second, a row for another file explains nothing.

2. **Each emptiness control re-implemented the guard it was testing.** Three cases wrote their own
   `if (length === 0) throw`, so they tested a *copy* of the logic and would have stayed green if the real
   guard were deleted. **Fixed:** one `refuseEmpty(items, refusal)` that both walkers call and all three
   controls drive. A guard against vacuity that is itself vacuously tested is the joke this file cannot afford.

3. **Two comparison loops read their length from the array under test.**
   `expect(measured).toHaveLength(FAILURE_KINDS.length)` stays green if the list is emptied and every
   comparison after it becomes vacuous. **Fixed:** absolute counts (`4`, `8`), with the reason in a comment.

One more, deliberate rather than fixed: `expect(targets.length).toBeGreaterThanOrEqual(20)` instead of
`> 0`, so a walker narrowed to one directory fails rather than reporting clean over three files.

---

## Deviations

- **T28-D1 (as anticipated).** R10's uniformity is verified on the client path; the refusal copy itself is
  SAS's server-rendered login page and is `oidcRoundTripEndToEnd`'s to verify — which has no suite and no
  owning task. The file's header says so in its "what this file cannot do" section rather than leaving the
  green to imply otherwise.
- **T28-D2 (as anticipated).** "Identical visual treatment" is trivially true because no styling exists
  anywhere in the SPA. Task 173 owns it.
- **T28-D3 (new, forced by M4).** The notice is not a field in the outcome struct. It is single-read and
  already consumed by the screen's render initialiser, so reading it would have asserted `null` on every path.
  Observed through the rendered live region instead. See M4 above.

## Verification

- `tsc --noEmit` — clean.
- `oxlint` on the new file — clean.
- The file alone: **39 passed**.
- Whole suite: **1334 passed / 1 failed**, exit non-zero. The one failure is the known environmental
  `src/api/lintRejectsHandWrittenBackendFetch.test.ts:118` (this host's `oxlint@1.87.0` prints no summary
  line), unchanged and unrelated. Baseline at T27 close was 1295/1, so this task adds 39 and breaks nothing.
- **No production code changed**, confirmed by `git diff --name-only`.

The test file is **committed and pushed before** the mutation harness is written, because the harness reverts
with `git checkout -- src/` and would otherwise destroy uncommitted cases (cost 19 cases in T14, 5 in T15).

---

**Phase 6 complete.** Ready for Phase 7 self-review.
