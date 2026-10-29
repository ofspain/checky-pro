# frontend · T28 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

The Phase 9 resolutions are applied. **43 cases, 10 `describe` blocks, no production code changed.**

---

## Final mutation score: **23 of 23 KILLED**, 21 by T28's own file

| | Before Phase 9 | After |
|---|---|---|
| Mutants killed | 22 of 23 | **23 of 23** |
| Killed by T28's own file | 20 | **21** |
| Inherited-only | 2 (MB12, MB23) | 2 (MB12, MB23) — **by design**, see below |
| Survivors | **MB04** | none |
| Cases | 39 | 43 |
| Suite | 1334 passed / 1 failed | 1338 passed / 1 failed |

The one suite failure is the known environmental `lintRejectsHandWrittenBackendFetch.test.ts:118`.
`tsc --noEmit` and `oxlint` clean.

**MB04 is now KILLED by T28's own file.** It was the only survivor, and it is closed by the fix it asked for
rather than by a case written around it: the sweep's `document` surface now reads the title and the head, and
each has a sentinel.

---

## What changed, finding by finding

### I1 / S1 — the document surface reaches the title and the head · **the survivor is closed**

`surfacesAfter()` now joins `document.body.textContent`, `document.body.innerHTML`, `document.title` and
`document.head.innerHTML`. The AC4 sentinel control plants into **each** of the new surfaces
(`SENTINEL-TITLE`, `SENTINEL-HEAD`), because a surface added to a sweep without a sentinel is a surface nobody
has proved is read.

**A defect the fix introduced, found while applying it and fixed in the same pass.** `document.title` and
`window.name` are *global* and survive `document.body.innerHTML = ''`, so a sentinel planted by one case was
still there for the next — one case's sentinel becomes the next case's leak. Both are now reset in `afterEach`
alongside the stores.

### I5 — the copy pattern is split, and this was the most serious finding in either review

`FORBIDDEN_COPY_SOURCE` became two constants with two scopes:

| Constant | Scope | Why |
|---|---|---|
| `ALWAYS_FORBIDDEN_COPY` | every catalogue entry | the second factor in every spelling; no screen may say it |
| `SIGN_IN_FORBIDDEN_COPY` | keys under `signIn.` / `callback.` | `code`, `role`, `merchant`, `administrator`, `locked`, `lockout`, `suspended`, `disabled`, `deactivated` |

Six of those terms were globally forbidden in the committed version and would each have **blocked copy a later
task is specified to write** — tasks 81–89 build the admin area with `assignRole`, `adminSuspendAccount` and
`adminUnlockAccount`. Kimi flagged only `\bcode\b`; the rest came out of checking `tasks.md` at the Phase 9
gate.

**Three controls hold the split**, and each is written to fail if the split is undone in either direction:

1. each of the six words is **forbidden** under `signIn.hint` and **permitted** under `admin.roles.heading`;
2. the second-factor vocabulary is forbidden under `signIn.hint`, `admin.roles.heading`, `invoices.empty` and
   `apiKeys.created` alike — so if the global group is ever scoped by mistake, R10's structural claim quietly
   becoming sign-in-only is caught;
3. a `PERMITTED_COPY` row admits exactly the key it names, one short reports the entry, and a row for another
   key explains nothing.

The third exists because `PERMITTED_COPY` is **empty today** — the same hole the Phase 6 self-audit found in
`PERMITTED_HITS`, closed the same way.

### I3 — the exchange count, outside the struct

Kimi asked for `fetchCount` **in** the `Outcome` struct, asserted `0` for the refusal and `1` for the rest,
inside the AC2 equivalence case. That breaks AC2 by construction: one `toEqual` across four kinds cannot hold
a field that differs between them.

Added as a **per-kind table in its own case**, with the measured values:

| kind | exchanges |
|---|---|
| `error-response` | **0** |
| `mismatched-state` | **0** |
| `refused-exchange` | 1 |
| `bad-nonce` | 1 |

`mismatched-state` is `0` as well, which the resolution did not predict and the measurement settled: the state
check discards before any exchange is started. The case's comment records why the count cannot live in the
struct — an error response carries no `code` to exchange (RFC 6749 §4.1.2.1), so the difference is in the
mechanism and not in anything the visitor observes.

### I2 — two written grammars instead of one derived one

`FORBIDDEN_KEY_SOURCE` is now a named constant with a comment saying why keys carry no word boundaries, in
place of `ALWAYS_FORBIDDEN_COPY.replace(/\b/g, '')`. Both controls (`signIn.mfaRequired`, `signIn.enterTotp`)
kept and still passing.

### I4 — `window.name` joins the sweep

A sixth surface, with its own sentinel. Beyond AC4's wording, which is the right direction for a leak sweep.
Named in the header's out-of-reach list so the contrast with `document.cookie` and `indexedDB` is explicit.

### I6 / S2 — the AC2 block says what it proves

Renamed from *"an error response is never a state oracle"* to **"the three state shapes are indistinguishable
when an error is present"**, and its comment now cites
`callbackWithMismatchedStateIsDiscarded.test.tsx` — *"the error check still precedes the comparison (D-12
ordering)"* — as the owner of the ordering, with the measured reason this file cannot own it.

### I7 — the harness is named in the header

Path, run command, the `git checkout` warning, the mutant count, and which mutant ever survived.

### Harness correction, not a finding from either review

The harness header predicted MB12 and MB23 would survive. **Both are killed**, by T18's `getItem` spy. The
header now says so, MB23's label changed from `EXPECTED SURVIVOR` to `INHERITED-ONLY BY DESIGN`, and the
baseline figures are updated. A harness that keeps a wrong prediction in its own documentation is worse than
one with no prediction at all.

---

## The four new cases

| Case | AC | Guards |
|---|---|---|
| exactly one exchange per failure kind, none for the refusal | AC2 | I3 — a retry on one kind and not another |
| the account-state vocabulary is forbidden on a sign-in key and permitted elsewhere | AC8 | I5 — the split, from the over-broad side |
| the second-factor vocabulary is forbidden everywhere, not only on a sign-in key | AC8 | I5 — the split, from the too-narrow side |
| a permitted-copy row admits exactly the key it names | AC8 | I5 — the accounting, which the empty table leaves untested |

Plus four assertions added to the existing AC4 sentinel case (title, head, `window.name`) and the
`afterEach` reset that keeps them from leaking between cases.

## Nothing changed in production

`git diff --name-only` across every T28 commit names one file under `frontend/src/`: the test file itself.
Both reviewers stated independently that no production change was indicated, and none was made.

## Carry-forward

Unchanged from Phase 7: the Phase 13 template's stale `Claude Opus 4.8 (1M context)` trailer (eight
consecutive tasks; the fix belongs in `.ai/generate.py`), `oidcRoundTripEndToEnd` having no suite and no owning
task, and **OQ1** — the live SAS `/login` form carries no `mfaCode` field while the backend requires it in the
same POST.

---

**Phase 10 complete. 43 cases, 23/23 mutants killed, no production change.** Ready for Phase 11 — Kimi's test
review.
