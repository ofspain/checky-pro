# frontend · T28 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` + the committed diff |
| **Produces** | `artifacts/07-self-review.md` |

Findings only. Nothing is rewritten here; fixes belong to the Phase 9 gate.

Reviewed: `frontend/src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx` (39
cases) against the frozen brief's eleven ACs, `spec/frontend/agents.md`, and the mutation score below.

---

## Mutation score: 23 mutants, **22 KILLED**, **1 SURVIVED**

Harness: `artifacts/07-mutation-harness.py`, committed before it was run. Baseline: whole suite 1 failed of
1335 (the known `lintRejectsHandWrittenBackendFetch.test.ts:118`); T28's own file 39/39, exit 0.

**20 of the 22 kills are T28's own file.** Two are inherited, and both legitimately so — see S2.

| Family | Result |
|---|---|
| MB01–MB05 the error survives the discard | 4 killed, **MB04 SURVIVED** |
| MB06–MB09 the copy names a cause | 4 killed, all own |
| MB10–MB12, MB23 uniformity and ordering | 4 killed (MB12, MB23 inherited — S2) |
| MB13–MB15 an account-state or role gate | 3 killed, all own |
| MB16–MB18 enrollment arrives structurally | 3 killed, all own |
| MB19 the denylist | killed, own |
| MB20–MB22 escape and instrument-stripping | 3 killed, all own |

**MB17 — the mutant this task exists for — is KILLED by T28's own file, with 34 failures.** Kimi F3's own
sentence, verbatim, which passed every scan in this package before today. **MB18 is KILLED by T28's own file**
and is the one mutant no other instrument in the package can see: a module named `EnrollMfaScreen.tsx` with an
innocent body. **MB20, the escape mutant, is KILLED by the source scan**, so the structural half of AC5/AC7 is
structural and not behavioural.

---

## S1 · AC4's document sweep misses `document.title` · **HIGH** · the surviving mutant

**Evidence.** `…ShowsWrongPasswordCopy.test.tsx:surfacesAfter()` builds the `document` surface from
`document.body.textContent` and `document.body.innerHTML`. Neither reaches `document.title`.
MB04 — `document.title = params.get('error_description') ?? document.title` in `CallbackScreen.tsx`'s discard
closure — **survived the whole suite**: 1 failed of 1335, exactly the baseline.

**Why it matters, and it is not cosmetic.** This is Kimi F8's leak in its most visible form. The tab title is
*read by the visitor*, is *read aloud by screen readers on navigation*, and lands in browser history and
session restore, so it outlives the discard in a way the DOM does not. A refusal that wrote
"Two-factor authentication must be set up" into the title would breach R10 exactly as the backend refused to,
and this file reports clean.

AC4's words are "absent from the rendered document". The title is part of the rendered document; my
implementation narrowed "document" to "body" without saying so, which is the kind of silent narrowing this
package's own rules forbid.

**Recommendation (Phase 9).** Extend the `document` surface to `document.title` and `document.head.innerHTML`,
and extend the sentinel control to plant into the title — a surface added to the sweep without a sentinel is a
surface nobody has proved is read. Then re-run MB04; it must become KILLED by T28's own file.

## S2 · The AC2 ordering case does not own the property its name implies · **MEDIUM**

**Evidence.** `…ShowsWrongPasswordCopy.test.tsx` block `AC2: an error response is never a state oracle` has a
comment explaining that `CallbackScreen.tsx:101-104` tests `error` before the state comparison at `:138`. But
MB12 (branch deleted) and MB23 (branch **moved** below the state check) are both killed **INHERITED-ONLY**, and
the inherited case is `callbackWithMismatchedStateIsDiscarded.test.tsx` →
*"the error check still precedes the comparison (D-12 ordering) › discards without reading the stored state"*.

**T18 already owns the ordering**, and owns it properly: it spies on `getItem` and asserts the stored state is
never *read* on an error path, which observes the order even though the outcome is identical. My case cannot,
because it compares outcomes, and the three state shapes discard identically under either order.

**Why it matters.** Two things, and the second is the real one. First, the comment overclaims: a reader would
conclude T28 pins the ordering, and it does not. Second — and this is a finding about **Kimi F1, not about my
code** — F1 asked for the ordering to be covered, the Phase 4 gate accepted it, and it was **already covered
before T28 was written**. What my case adds is a genuinely different and still-worthwhile claim: the three
`state` shapes are *indistinguishable in outcome*, which forecloses a future divergence rather than today's
order.

**Recommendation (Phase 9).** Keep the case; rename the claim to what it proves (the three shapes are
indistinguishable) and cite `callbackWithMismatchedStateIsDiscarded.test.tsx` as the owner of the ordering
itself. Record in Phase 12 that F1 was satisfied by an existing test, so the accept was not wrong but was
redundant.

## S3 · The key-matching grammar is built by string surgery on a regex source · **MEDIUM**

**Evidence.** `forbiddenCopyEntries` matches a catalogue **key** with
`new RegExp(FORBIDDEN_COPY_SOURCE.replace(/\\b/g, ''), 'i')` — it strips the word boundaries out of the prose
pattern to get an identifier pattern.

**Why it matters.** It works today and two controls prove it (`signIn.mfaRequired`, `signIn.enterTotp`), so
this is not a defect. It is a fragility: the two grammars are now coupled through a textual rewrite, and any
future alternative whose meaning depends on a `\b` would be silently changed rather than reported. T27 solved
the same problem with two explicitly written constants, which is the more honest shape — the reason the two
patterns differ should be readable, not computed.

**Recommendation (Phase 9).** Replace the `.replace(/\\b/g, '')` with a second named constant and a comment
saying why the key grammar has no boundaries. Keep both controls.

## S4 · Only the `error=` path asserts that no exchange was attempted · **LOW**

**Evidence.** AC1's second case asserts `fetchSpy` was not called. The other three failure kinds are expected
to call it (that is how they fail), but nothing asserts the **count**, so an implementation that retried a
refused exchange would pass.

**Why it matters.** Weakly. T20 owns the exchange and its replay behaviour (`400 invalid_grant`, measured
there), so this is a gap in this file's reach rather than in the package's. It belongs in the "what this file
cannot do" list if it is not closed.

**Recommendation (Phase 9).** Either add the fetch count to the `Outcome` struct — which would make it part of
the equivalence claim, where it arguably belongs — or name T20 as its owner in the header. The first is
better: a retry on one failure kind and not another is a uniformity breach, which is this file's subject.

## S5 · `PERMITTED_HITS` is empty, so the real table is only synthetically exercised · **LOW**

**Evidence.** `PERMITTED_HITS: readonly PermittedHit[] = []`, measured: zero enrollment hits across 24 files.
The counting logic is exercised by a control with synthetic rows (added in Phase 6's self-audit).

**Why it matters.** Accepted rather than fixed. An empty table is the measured truth, and the control proves
the accounting works, which is the best available position. Recorded so a future reader does not read the
empty table as an oversight.

**Recommendation.** No change. Keep the control.

## S6 · The AC11 instrument table cannot fail for the right reason · **LOW**

**Evidence.** `AC11: every instrument has been shown to report dirty` → `names one dirtying violation per
instrument` asserts the table has eight rows and no empty `dirtiedBy`.

**Why it matters.** It does not — it is documentation. It is already labelled a signpost in a comment, which is
exactly what T27's Kimi review (Gap 6) asked for after finding the same shape. The second case in that block
is real evidence and drives the shared `refuseEmpty` guard.

**Recommendation.** No change. The label is the fix, and it is already applied.

## S7 · `document.cookie` and `indexedDB` are declared out of reach rather than swept · **LOW**

**Evidence.** `surfacesAfter`'s comment, and the header's "what this file cannot do" section.

**Why it matters.** Honest and correct: jsdom gives an empty cookie jar and no `indexedDB`, so sweeping them
would report clean for an environmental reason and read as evidence. T22 owns the application-wide leak claim.
Noted so the Phase 8 reviewer does not have to rediscover it.

**Recommendation.** No change.

---

## What I checked and found no finding on

- **Module boundaries.** Every route, key, path and sentence comes from its owning module (`routes.tsx`,
  `AUTHORIZE_STORAGE_KEYS`, `DENYLISTED_NAVIGATION_PATHS`, `en`). No literal stands in for one. `'/app/sign-in'`
  and `'/app/home'` appear as literals in assertions about *measured destinations*, which is the right side of
  that rule — the test is asserting where the router went, not re-spelling a constant.
- **Null-safety.** `document.body.textContent ?? ''`, `node.textContent ?? ''`, `expect.getState().testPath as
  string`. No unguarded read.
- **Spy ordering.** `watchConsole`, `stubFetch` and the seeded storage are all installed before `arriveAt`
  renders; AC4 holds the late-watcher control. `vi.unstubAllGlobals` + `vi.restoreAllMocks` +
  `vi.resetModules` in `afterEach`, and `arrival.unmount()` rather than dropping the root.
- **Module-generation hazard.** `outcomeOf` and `measure` import `accessToken` and `signInNotice`
  **dynamically**, inside the function, so they read the generation the screens hold rather than a stale one
  from before `vi.resetModules()`. A static import here would have made `tokenHeld` read `false` for the right
  answer by accident, from the second case onward.
- **Regex `lastIndex`.** Every pattern is a factory (`enrollmentWords()`, `accountStateGates()`,
  `forbiddenCopy()`, `enrollmentShapedPath()`); `namesEnrollment` uses a non-global. T27's defect is not
  repeated.
- **No production code changed.** `git diff --name-only` over the task's commits names the one test file.
- **Enumeration-safety.** No case asserts copy that distinguishes one failure from another, and
  `AC5`'s permitted-copy control holds the line against the patterns Kimi asked for and the gate refused.
- **Money types, transactions, idempotency, concurrency.** Not applicable to a frontend verification task.

## Carry-forward, not this task's

- **The Phase 13 template's stale `Claude Opus 4.8 (1M context)` trailer** — now eight consecutive tasks. The
  fix belongs in `.ai/generate.py`, not in another task's artifact.
- **`oidcRoundTripEndToEnd`** still has no suite and no owning task, and it is the only test that could verify
  R10's actual refusal copy (T28-D1).
- **OQ1** — the live SAS `/login` form carries no `mfaCode` field while the backend requires it in the same
  POST, so no real browser can complete an MFA sign-in. Needs a backend task or an accepted limitation.

---

**Phase 7 complete. Three findings worth acting on: S1 (high, the surviving mutant), S2 (the overclaiming
comment and Kimi F1's redundancy), S3 (the derived regex).** Ready for Phase 8 — Kimi's independent review.
