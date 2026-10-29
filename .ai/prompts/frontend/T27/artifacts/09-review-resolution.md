# frontend · T27 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Human Approval (packet assembled by Claude Opus 5) |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/11-test-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

---

## A pipeline deviation to record first, because it changes what this artifact consumes

**Phase 8 was never produced.** Three "kimi done" cues arrived; on the first two the artifacts directory still
ended at `07-self-review.md`, verified by `git pull`, a directory listing, a clean `git status`, and a search
of every service's `artifacts/` directory. On the third, what arrived was
**`artifacts/11-test-review.md`** — the phase *11* artifact, declaring that it consumes
`artifacts/10-test-generation.md`, which did not exist either.

**Its content is nonetheless a genuine independent review of the real deliverable.** It reviews
`loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` as committed, names real line-level properties
of it (`importedSpecifiers`, `MFA_IDENTIFIER_SOURCE`, `catalogueStrings`, `CREDENTIAL_SOURCE_PATTERNS`), and
engages with this task's own Phase 7 self-review by number (it agrees with S4). It was not written against a
manifest that does not exist; it was written against the repository.

**Why that is acceptable here rather than a blocker.** T27's deliverable **is a test file**. The pipeline
separates implementation (6) from test generation (10), and code review (8) from test review (11), because
most tasks produce code and tests separately. This task produces one artefact, so 6 and 10 are the same file
and 8 and 11 are reviews of the same object. One adversarial review of that object discharges both.

**What I did not do:** write Phase 8 myself. The standing rule is that 3, 8 and 11 are Kimi's and are never
self-produced as a fallback, and a self-written "independent" review is worth nothing precisely because it is
not independent. `artifacts/08-independent-review.md` and `artifacts/10-test-generation.md` are therefore
recorded as **not produced**, with Phase 10's manifest written separately for reproducibility.

---

## Disposition of the seven findings — five accepted, two rejected

Every finding was measured against source before being accepted or rejected.

| Gap | Disposition | Basis |
|---|---|---|
| 1 · `require()` escapes the import scan | **ACCEPTED** | Measured: the old pattern matched `react` and missed `require('otpauth')` entirely |
| 2 · Missing second-factor synonyms | **ACCEPTED in part** | Four added; `verification code` rejected — it is R3–R5's word too |
| 3 · Exclude stories/mocks from the walker | **REJECTED** | No such file exists; no Storybook, no msw. Same finding, same rejection, as T26 |
| 4 · Catalogue scan is English-only | **ACCEPTED** (comment) | True; one locale exists, and the next one must be added to the scan |
| 5 · The `<form>` pattern is broad | **ACCEPTED** (comment) | True, and intentional; the breadth is now justified in place |
| 6 · The AC11 table is documentation | **ACCEPTED** (comment) | Agrees with self-review S4; now says so in the file |
| 7 · No keyboard-activation test | **ACCEPTED, reimplemented** | Measured: the suggested form would be **inert** in jsdom |

### Gap 1 — accepted

`importedSpecifiers` matched `from`, `import` and `import(...)` but not `require(...)`. Measured against a
synthetic file carrying both forms: the old pattern returned `['react']` and missed `otpauth` entirely. No
production file uses `require` and ESM is the project's style, **which is exactly why the omission would have
gone unnoticed until the one file that did**. Pattern extended; a control now asserts both forms are caught.

### Gap 2 — accepted in part, and the exclusion is the interesting half

Added, each measured to **zero hits** in production source before being added, so none fires on anything that
exists: `security key`, `backup code`, `multi-factor`, `fido`, `tfa`.

**`verification code` rejected.** It belongs to two domains at once: it is an MFA term, and it is what R3–R5's
**email-verification** flow would call its own artefact — a flow that is specified, permitted, and owned by
tasks 48–51. A scan firing on it would block a permitted feature while reporting an L2 breach. That is T26's
lesson in its exact form: *a guard must not forbid what the spec permits.* The measurement that settles it:
`verification code` has no hit today, but the looser stem `verif` has **forty**, every one of them PKCE's
`codeVerifier`/`isValidCodeVerifier` or the email-verification route and client names. The rejection is held
by a control case asserting the scan stays silent on "Enter the verification code we emailed you", so it
cannot be "fixed" without reading why.

### Gap 3 — rejected, consistently with T26

Re-measured, not recalled: **no** `.stories.*`, `.mock.*`, `.spec.*` or `__mocks__` file exists anywhere under
`src/`, and `package.json` contains neither Storybook nor msw. Kimi raised this same finding against T26 and
it was rejected then on the same evidence. Pre-emptively excluding file patterns would weaken the one
instrument whose entire value is breadth — **a story demonstrating a credential input is exactly the file a
leak hides in**. If such files ever land, the permitted-hit table absorbs them individually, with a reason
each, which is the mechanism this file already has.

### Gap 7 — accepted in intent, reimplemented in substance

Kimi proposed asserting that Enter or Space on the sign-in button triggers the hand-over. **Measured: jsdom
implements no default activation behaviour for a button, so a dispatched `keydown` fires zero click events —
0 of 2.** The suggested case would have passed against a button that nothing could activate: inert, and
inertness is the failure mode this package has been bitten by repeatedly ("write the mutant before believing a
new test case").

What is verifiable is the property that makes keyboard activation possible, and it is the property genuinely
at risk. `SignInScreen.tsx:54-56` uses `aria-disabled` rather than `disabled` **because a focused element that
becomes disabled drops a keyboard user to `<body>`, and this is the only control on the screen.** The new case
pins `disabled === false`, `tabIndex >= 0` and `type === 'button'`, **before and after** the hand-over starts,
which catches a regression from `aria-disabled` back to `disabled`. Real activation across a full-page
navigation remains `oidcRoundTripEndToEnd`'s, which has no suite and no owning task.

---

## State after the resolution

- **50 cases** (was 47): three added — the `require` control, the synonym control with its deliberate
  omission, and the keyboard-operability case.
- `tsc --noEmit` clean; `oxlint` clean on the file.
- Suite **1295 passed / 1 failed (1296)** — the one failure being the known environmental
  `src/api/lintRejectsHandWrittenBackendFetch.test.ts:118`.
- Mutation re-run after the changes: **22 of 22 killed, survivors: none.** MA17 and MA22 still `own`; MA18
  still inherited-only by design (see self-review S1).
- **No production code changed**, at any point in this task.

## Deviations carried to Phase 12

- **T27-D1** — the completed resumption renders nothing, announces nothing and strands focus on `<body>`,
  because `/app/home` has no element. Pinned, not asserted. Owners: task 135 and, now, **task 174**, added to
  `tasks.md` on 2026-10-29 at the owner's instruction.
- **T27-D2 (new, process)** — Phase 8 and Phase 10 were not produced as separate artifacts; one Kimi review
  covered the single deliverable, as recorded above.

---

**Phase 9 complete.** Ready for Phase 12 specification verification.
