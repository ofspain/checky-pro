# frontend · T28 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this task is ready for merge.

---

## Commit title

```
frontend T28: verify an unenrolled privileged refusal shows the wrong-password copy
```

## Commit message

```
frontend T28: verify an unenrolled privileged refusal shows the wrong-password copy

Add sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy, the named
test for R10 and L5. No production code changes.

R10's value is silence, so read the backend before writing anything. The refusal
for an unenrolled MERCHANT or ADMIN is a LOGIN FAILURE, not an authorization
error: TotpAuthenticationProvider:100-101 throws BadCredentialsException("Bad
credentials"), the same exception, with the same message, as an unknown user
(:118), a wrong password (:125) and a bad TOTP (:132). Its class comment records
that a distinct "please enroll" signal was CONSIDERED AND REJECTED, because a
signal that fires only once the password is right is a password-correctness
oracle. So the wrong-password copy R10 names is the SAS login page's, which L2
puts on the server - and what this bundle can verify is the other half: that the
SPA never learns enrollment exists.

That makes the task a proof of absence, and absence passes by default. Every
instrument is therefore paired with the violation that must break it, 27 of the
47 cases are controls, and the file refuses to report clean on a tree it could
not read.

Four instruments, one claim each:
- the outcome struct - a refusal, a mismatched state, a refused exchange and a
  bad nonce produce ONE struct, including destination, historyAction, search,
  hash, rendered text, stored keys and whether a token is held;
- the leak sweep - the error code, description and URI survive on none of six
  surfaces, document.title and window.name among them, across six console
  channels;
- the source, path and catalogue scans - no production module, FILE NAME or
  user-facing string names enrollment, a factor, a code or a lockout, and
  nothing gates a failed sign-in on an account state or a role;
- the key-set pin - signIn.* is exactly the six keys that exist, so a seventh
  fails whatever it is called.

The mutant this task exists for is MB17, the sentence from Kimi's phase 3
review: "Your account needs two-factor authentication set up before you can sign
in." It passed every scan in the package before this file, because T27's pattern
has multi-factor and no two-factor. Phase 11 found the mirror of that hole in
this file - two-factor and no multi-factor - so MB24 and MB25 now hold both
sides.

Four declared deviations:
- T28-D1: R10's uniformity is verified on the client path. The refusal's own
  sentence is SAS's (L2) and belongs to oidcRoundTripEndToEnd, which has no
  suite and no owning task.
- T28-D2: "identical visual treatment" is trivially true - no styling exists.
  Task 173 owns it.
- T28-D3: the notice is not a field of the struct. signInNotice.ts:13 is a
  one-member union, so asserting one notice equals another cannot fail, and the
  value is single-read.
- T28-D4: phase 11 arrived after phase 10 closed rather than at the gate.
  Nothing was self-produced in the interval.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

> **Template note, ninth consecutive task.** The generated Phase 13 prompt asks for a
> `Claude Opus 4.8 (1M context)` trailer. That model is not the one running this pipeline and the trailer is
> stale; this task uses `Claude Opus 5`, as T22–T27 did. The fix belongs in `.ai/generate.py`, which generates
> every task's prompts — not in the artifacts, where it has now been corrected by hand nine times.

## Files changed

| File | Change |
|---|---|
| `frontend/src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx` | **new**, 1475 lines, 47 cases, 27 of them controls |
| `.ai/prompts/frontend/T28/artifacts/07-mutation-harness.py` | **new**, 29 mutants, committed before every run |
| `.ai/prompts/frontend/T28/artifacts/*.md` | the phase artifacts |

**No production module was modified.** `git diff --name-only 21e727a4~4..HEAD -- frontend/src` reports one
file changed: the test file itself. Both Phase 8 and Phase 11 stated independently that no production change
was indicated, and none was made.

## Summary

One new test file proving the SPA's half of R10 and L5 — that a SAS refusal is an ordinary discard and that
the SPA contains no knowledge of enrollment anywhere a developer might later add some.

- **Uniformity.** Four failure kinds, one `Outcome`, compared as a single struct. The comparison is shown not
  to be blind: a *successful* callback differs in four named fields, and the struct is shown not to be a shell
  of empty values. Exchange counts are asserted per kind outside the struct — 0 for all three `error=` shapes,
  1 for a refused exchange and a bad nonce — because a retry on one kind and not another is a uniformity
  breach the struct cannot see.
- **Absence.** Across 24 production modules, their paths, and the i18n catalogue: no enrollment vocabulary, no
  enrollment-shaped file or directory name, no account-state or role gate, no forbidden copy, and exactly the
  six `signIn.*` keys. The permitted-hit table is empty and its accounting is still exercised, by synthetic
  rows.
- **The scans are narrow on purpose, and that is held by cases.** A bare `state ===` scan has three legitimate
  hits in this repository, so the gating scan keys on account-state-shaped *names*. The copy pattern is split
  in two — second-factor vocabulary forbidden everywhere, account-state vocabulary forbidden only on sign-in
  keys — because the single wide pattern would have blocked copy that tasks 43–57 and 81–89 are specified to
  write. Three cases hold the split from both directions.

## Testing performed

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | clean |
| The file alone | **47 / 47**, exit 0 |
| Whole suite | **1342 passed / 1 failed (1343)** |
| Known environmental failure | `src/api/lintRejectsHandWrittenBackendFetch.test.ts:118` — this host's `oxlint@1.87.0` prints no summary line; fails before and after this change |
| Mutation | **29 / 29 killed, survivors: none.** 27 by this file. MB12 and MB23 are inherited-only *by design* — T18's `getItem` spy owns the error-before-state ordering, and this file pins the three `state` shapes identical instead |
| Vacuity checks by hand | With the Gap 5 assertions deleted, **MB29 survives all 47 cases**. With the Gap 2 clearing removed, the sentinel control fails and nothing else does |

Run the harness from the repository root, with everything under `frontend/src/` committed first:

```
python3 -I .ai/prompts/frontend/T28/artifacts/07-mutation-harness.py [MB<n>]
```

## Specification references

- **Task:** `spec/frontend/tasks.md` task **28**
- **Requirements:** **R10** (scoped); **R30** not contradicted; **R57** cited; **R6**, **R7**, **R8** as
  boundaries
- **LOCKED decisions:** **L5** (scoped); **L4**, **L2**; and **L1**, **L3**, **L14**, **L16**, **L17**
- **Named test:** `sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy` (`package.md` §8)
- **Standing rules:** `spec/frontend/agents.md:40-44` — *"No client-side account-state gating. No lockout
  timers. Uniform copy only."*

The package's other named test for this area, `privilegedBootstrapPathEnrollsWithoutEnrollmentHint`, is **not**
T28's: it is an enrollment path, not a refusal, and nothing in the SPA reaches it.

## Outstanding, for the owner — none of it blocking

1. **OQ1 — the SAS login page has no `mfaCode` field.** Confirmed 2026-10-29 against the running local stack:
   the live form carries `username`, `password` and `_csrf` only, while `TotpAuthenticationDetailsSource`
   requires `mfaCode` in the same POST. **A real browser cannot complete an MFA sign-in today.** L2 puts that
   UI on the server, so it is neither T28's to build nor the SPA's to work around. Needs a backend task or an
   accepted limitation against R9. Carried from T27.
2. **`oidcRoundTripEndToEnd` has no suite and no owning task**, which is what leaves T28-D1 standing: nothing
   in this repository drives a real refusal through SAS and reads the page the visitor actually sees.
3. **Kimi's phases arrive late.** T27's Phase 8 never produced; T28's Phase 11 arrived after Phase 10 closed,
   across two cues. Twice in two tasks is a pattern, and the pipeline should expect the lag rather than treat
   it as absence — the cost here was an unwound Phase 12 commit, which is cheap, but a self-produced
   substitute would not have been.
4. **T27-D1's owners** — task 135 and task 174. **L16 clause (b)** is unbuilt and unowned.
5. **`contracts/api/token-claims.md`** is wrong about `email` and `name` and silent on `amr`/`acr`.
6. **The stale Phase 13 trailer in `.ai/generate.py`** — nine consecutive tasks.

---

**T28 COMPLETE — 14 of 14 phases, verdict PASS.**
