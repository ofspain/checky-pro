# frontend · T27 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this task is ready for merge.

---

## Commit title

```
frontend T27: verify the SAS MFA challenge resumes at the callback
```

## Commit message

```
frontend T27: verify the SAS MFA challenge resumes at the callback

Add loginChallengeRunsInsideSasFlowAndResumesAtCallback, the named test for R9.
No production code changes.

R9 has two halves and both are about restraint: the TOTP or recovery-code
challenge runs inside the SAS-hosted flow, and the SPA resumes at the callback
once it completes. So the failure mode this guards against is not a bug but a
plausible-looking addition - a one-time-code field beside the sign-in button, an
"enter your authenticator code" step before the redirect, a branch in the
callback for "MFA was used". Each would look like progress, each would breach
L2, and two would breach L4 and R10 as well, because a screen that asks for a
second factor has already told the visitor the first one was right.

Read the backend rather than assuming R9's premise, and it is stronger than the
requirement implies: the second factor is a FIELD, NOT A STEP.
TotpAuthenticationDetailsSource captures an optional mfaCode in the SAME request
as the password, and SecurityChainsConfig registers TotpAuthenticationProvider
as the only provider that filter's manager can reach. One request, both factors,
one response - so the SPA cannot observe how many factors were verified, and the
verifiable claim is that the SPA contains no factor and its resumption is
symmetric. The fixture differs; the outcome does not.

Absence passes by default, so vacuity is the dominant risk here rather than
incorrectness, and every instrument is paired with the violation that must break
it: a hand-over counter over assign/replace/the href setter/window.open; a
comment-stripped source scan for credential shapes, MFA identifiers and
forbidden imports; a dependency-manifest check, which catches a second-factor
package one step before any module can import it; and a catalogue scan, because
copy is where an MFA disclosure would surface.

Three declared deviations:
- T27-D1: L11's managed focus is PINNED, not asserted. Measured - the completed
  resumption renders nothing, holds zero live regions and leaves focus on <body>,
  because /app/home has no element. Owners: task 135 and task 174.
- T27-D2: phases 8 and 10 were not produced as separate artifacts; one Kimi
  review covered the single deliverable. Phase 8 was not self-produced.
- T27-D3: the eight authorize parameter names are literals, because the derived
  form is T16:223's and cannot catch an addition.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

> **Template note, seventh consecutive task.** The generated Phase 13 prompt asks for a
> `Claude Opus 4.8 (1M context)` trailer. That model is not the one running this pipeline and the trailer is
> stale; this task uses `Claude Opus 5`, as T22–T26 did. The fix belongs in `.ai/generate.py`, which generates
> every task's prompts — not in the artifacts, where it has now been corrected by hand seven times.

## Files changed

| File | Change |
|---|---|
| `frontend/src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` | **new**, 1679 lines, 50 cases |
| `.ai/prompts/frontend/T27/artifacts/07-mutation-harness.py` | **new**, 22 mutants, committed before every run |
| `.ai/prompts/frontend/T27/artifacts/*.md` | the phase artifacts |

**No production module was modified.** `git diff 06261068..HEAD -- frontend/src` reports one file changed.

## Summary

One new test file proving R9 by the only two things the architecture makes observable from the SPA:

- **Absence** — no credential input, no second-factor logic, no second-factor package, no MFA-shaped copy,
  across 24 production modules, the dependency manifest and the i18n catalogue, with exactly two permitted
  hits (`account/mfa`, a route path with no element; PKCE's `code_challenge`), each carrying its reason.
- **Symmetry** — three fixtures differing only in what SAS did behind the redirect, including one carrying an
  explicit `mfa_method` claim, produce a deep-equal `Outcome`; and a wrong `nonce` — the one difference the SPA
  *is* meant to notice — makes four of its five fields differ, so the comparison is demonstrably not blind.

## Testing performed

| | |
|---|---|
| `tsc --noEmit` | clean |
| `oxlint` | clean on the new file |
| The file alone | **50 / 50**, exit 0 |
| Whole suite | **1295 passed / 1 failed (1296)** |
| Known environmental failure | `src/api/lintRejectsHandWrittenBackendFetch.test.ts:118` — this host's `oxlint@1.87.0` prints no summary line; fails before and after this change |
| Mutation | **22 / 22 killed, survivors: none.** 21 by this file. MA17 (a second hand-over through `location.href`) and MA22 (an MFA marker under a computed key behind a dead condition) are both killed by this file; MA18 is inherited-only by design |

Run the harness from the repository root:

```
python3 -I .ai/prompts/frontend/T27/artifacts/07-mutation-harness.py [MA<n>]
```

## Specification references

- **Task:** `spec/frontend/tasks.md` task **27**
- **Requirements:** **R9** (scoped); **R10**, **R57** not contradicted; **R6**, **R7** as boundaries
- **LOCKED decisions:** **L2**, **L5**, **L11**; and **L1**, **L3**, **L4**, **L9**, **L14**, **L16**, **L17**
- **Named test:** `loginChallengeRunsInsideSasFlowAndResumesAtCallback` (`package.md` §8)
- **Standing rules:** `spec/frontend/agents.md`

## Outstanding, for the owner — none of it blocking

1. **OQ1 — the SAS login page has no `mfaCode` field.** Re-confirmed on 2026-10-29 against the running local
   stack: the live form carries `username`, `password` and `_csrf` only, while the backend requires `mfaCode`
   in the same POST. **A real browser cannot complete an MFA sign-in today**, and a password-only attempt
   returns a 401 indistinguishable from a wrong password. L2 puts that UI on the server, so it is neither
   T27's to build nor the SPA's to work around. Needs a backend task or an accepted limitation against R9.
2. **T27-D1's owners** — task 135 and task 174.
3. **`contracts/api/token-claims.md`** is wrong about `email` and `name` and silent on `amr`/`acr`.
4. **The stale Phase 13 trailer in `.ai/generate.py`** — seven consecutive tasks.

---

**T27 COMPLETE — 14 of 14 phases, verdict PASS.**
