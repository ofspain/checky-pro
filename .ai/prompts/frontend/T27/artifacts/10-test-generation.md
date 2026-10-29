# frontend · T27 · Phase 10 — Test Manifest

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

> Written **after** `artifacts/11-test-review.md` rather than before it. T27's deliverable is a test file, so
> Phase 6 already produced what Phase 10 would have; this manifest exists so the evidence is reproducible
> rather than asserted. See the Phase 9 resolution for the full record.

## The file

`frontend/src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` — **50 cases**, eleven
`describe` blocks, one per acceptance criterion. **No production code changed.**

## How to run it

```
cd frontend
npx -y pnpm@12.14.0 exec vitest run src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx
```

**The exit code is the verdict, not the pass count** — an unhandled rejection prints below the all-passed line.
Whole suite: drop the path. One known environmental failure is expected and unrelated:
`src/api/lintRejectsHandWrittenBackendFetch.test.ts:118` (this host's `oxlint@1.87.0` prints no summary line).

## The four instruments, and the control that makes each one fail

| Instrument | Proven by |
|---|---|
| Hand-over counter (`assign`, `replace`, `href` setter, `window.open`) | all four mechanisms counted; a counter installed late sees nothing |
| Source scan — credentials, MFA identifiers, forbidden imports | synthetic one-time-code input, password form, `verifyTotp`, dormant branch, `otpauth` import, `require('otpauth')` |
| Dependency manifest | a synthetic manifest carrying `otpauth` |
| Catalogue scan | a synthetic MFA sentence; a camelCase MFA key through a second grammar; a permitted "verification code" that must NOT fire |
| Symmetry comparison | a wrong `nonce` makes four of five `Outcome` fields differ |
| Escape sweep | the SPA is shown to have received every sentinel; a planted sentinel is found |
| Parameter set | an added `acr_values` fails the set, where T16:223's derived comparison does not |
| Walkers | an empty source tree and an empty catalogue are each refused rather than reported clean |

## Mutation evidence

Harness: `.ai/prompts/frontend/T27/artifacts/07-mutation-harness.py`, committed before every run.

```
python3 -I .ai/prompts/frontend/T27/artifacts/07-mutation-harness.py [MA<n>]
```

**22 mutants, 22 killed, survivors: none.** 21 killed by T27's own file. MA18 is inherited-only by design —
AC1 counts hand-overs by any mechanism, so `window.open(url, '_self')` satisfies it and the mechanism itself
is T16's claim (self-review S1, measured: under MA18 T27 is green and T16 fails 8).

Two mutants carry the argument: **MA17**, a second hand-over through `location.href` alongside the existing
`assign`, which an `assign`-only spy reports as exactly one call — the mutant Kimi F1 exists for, killed by
T27's own counter. **MA22**, written to escape: an MFA marker under a computed key, through a computed method
name, behind a condition no test drives — invisible to every runtime instrument, caught by the identifier
scan, because that scan keys on the name of the factor rather than the call that stores it.

---

**Phase 10 complete.**
