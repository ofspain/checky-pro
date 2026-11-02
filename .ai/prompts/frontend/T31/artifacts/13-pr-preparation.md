<!-- MODEL: Claude Opus 5 — Phase 13 (PR / Commit Preparation) -->

# frontend · T31 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this phase prepares the task for merge.

---

## Commit title

```
feat(frontend/T31): read the account status from /accounts/me, which the token cannot tell you
```

## Commit message

```
feat(frontend/T31): read the account status from /accounts/me, which the token cannot tell you

Add `profile/readAccount.ts` and `profile/accountStatus.ts`, and answer
R20's second clause in the named test T30 created,
`profileIsReadFromUserinfoAndAccountStatusFromMe`. T30 built the profile
read and proved PII never comes off the token; T31 does the same for
account state.

The measurement is the argument for the whole module. Suspending an
account through POST /admin/accounts/{uuid}/suspend does NOT invalidate
its access token: the same unchanged token then answers 200 with
status: "SUSPENDED", and no 401 ever arrives. So a tab can hold a working
session for a suspended account for the token's fifteen minutes, and this
read is the only thing that can say so. A token-borne shortcut would be
worse than merely cheaper - the token was minted while the account was
ACTIVE and goes on asserting that until it expires, which is a stale
answer that looks current.

Unlike T30, the whole stack already existed: the contract path, the
AccountResponse schema, the generated operation, the public-operation
allowlist correctly excluding it, and the backend handler. No contract
change and no regeneration.

Decisions worth stating:

- An unrecognised status fails CLOSED. The union stays as generated
  rather than widening to string, because with a closed union a consumer
  writing an exhaustive switch is compile-time complete and runtime
  incomplete - TypeScript proves all five arms are handled while a sixth
  value walks through, and the type system reassures the author while it
  does. On a field that gates access, failing is the only safe direction.
- A present-but-blank required field is rejected: it is the same absence
  wearing a type.
- Whitespace is not a token. '' sends no Authorization header; '   ' is
  truthy, so the boundary attaches it and a malformed bearer goes out -
  both reaching the measured 401 with its Set-Cookie and one doomed
  renewal, which is what the guard exists to prevent.
- The status enum lives in its own module so T29's state-word scan can
  license a FILE rather than a regex over a construct. A regex licence
  pins a textual shape, and the first reformat breaks it in a way whose
  obvious repair is to widen it and lose the property. The bound is now
  the carriers: a state word reaches a visitor by being rendered, thrown,
  logged or translated, and the licensed file does none of those.

Three ACs are held by source-shape cases rather than behavioural ones,
each for the same measured reason: the mutant produces an identical
runtime result. An assembled path yields the same string as a literal
(MQ06, MQ30), and reaching for answer.error is refused by !response.ok
either way (MQ29). Behaviour cannot see any of them.

54 cases in one additive describe('T31: ...') block beside T30's
untouched 53, 24 instruments each with a dirty-proof, 31 of 31 mutants
killed with no survivors and none broken. Four files outside T31 were
edited, all to hold T31's own exception or its own gate, and each is
dirty-proofed.

OQ-T31-3 stays open by owner decision: nothing yet owns ACTING on a
readable-but-not-ACTIVE account. OQ-T31-4 is logged for the owner -
type-fest, via workbox-build and tempy, declares
/// <reference lib="esnext"/> and silently lifts this project's declared
ES2022 ceiling for every file in src/.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

**Trailer note, thirteenth consecutive task.** The template above this phase still asks for a
`Claude Opus 4.8 (1M context)` trailer. The commits use the attribution this session is actually given.
The single source is `.ai/generate.py:476`; regenerating the 382 affected prompt files remains the owner's
call.

## Files changed

| File | Change |
|---|---|
| `frontend/src/profile/readAccount.ts` | **new**, 182 lines — the read |
| `frontend/src/profile/accountStatus.ts` | **new**, 50 lines — the status list, its guard, and nothing else |
| `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` | +857 — T31's 54 cases, additive; T30's 53 untouched |
| `frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` | +149/− — the allowlist row, its carrier bound, two dirty-proofs, and a corrected premise (D2) |
| `frontend/src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` | +39/− — `tfa` left-bounded, with a T27-scoped control (D3) |
| `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` | +13/− — the `this.name =` count, 4 → 5 (D4) |
| `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx` | +7/− — one ES2023 call becomes ES2022 (D7, Kimi A1) |
| `.ai/prompts/frontend/T31/artifacts/*` | the fourteen phase artifacts and the mutation harness |

No production file outside `src/profile/` was touched. No contract, no `spec/`, no config.

## Summary

R20 splits across three tasks. T30 built the profile read from `/userinfo` and proved that PII and
verification state never come off the access token. **T31 builds the account-state read**, and its reason for
existing is measured rather than argued: a suspended account's access token keeps working, answers `200`, and
reports `status: "SUSPENDED"` — so `/accounts/me` is the only thing in the system that can tell a tab its
account is no longer in good standing.

The read is deliberately small and does four things: refuse before issuing a request when there is no usable
token, call the generated client with a literal path, refuse a 401 or 403 as `unauthorized` without carrying
a word of the server's detail, and refuse anything that is not a complete, in-contract account. It persists
nothing, logs nothing, and returns the account exactly as sent.

## Testing performed

| | |
|---|---|
| T31's own cases | **54 / 54** |
| Shared named test | **107 / 107** (T30's 53 + T31's 54) |
| Inherited files touched | `identityErrors…` 58/58 · `loginChallenge…` 51/51 · `tokensAreNever…` 48/48 · `stateSurvives…` 29/29 |
| Whole suite | **1508 passed / 1 failed (1509)** — `lintRejectsHandWrittenBackendFetch`, environmental and pre-existing, now pinned by name in the harness |
| Mutation | **31 / 31 killed**, 0 survivors, 0 broken |
| `tsc --noEmit` | exit 0 under TypeScript **7.0.2** and **5.9.3** |
| `oxlint src` | exit 0 |
| Live stack | `GET /accounts/me` measured at `localhost:18080` with a real bearer, no bearer, a malformed bearer, and after an admin suspend |

Two Kimi review phases produced eighteen findings. Twelve were taken into code; two were rejected on
measurement with the measurement recorded (a `tsc` gate that was green under the pinned compiler, and an
`Object.entries` hardening that `Object.keys` already covers); two were resolved as documentation; two needed
no action. Two mutants written for those findings proved **inert** and were replaced with reachable ones
rather than counted.

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` task **31** |
| **Requirements** | **R20** — (b) account state from the account resource, (c) verification state never from a token claim; **R21**, **R22**, **R23** preserved |
| **LOCKED decisions** | **L3** in-memory tokens · **L6** no hand-written transport · **L10** generated client only · **L11** catalogue-owned copy · **L14** fixed routes (honoured by omission) · **L18** no PII on any surface |
| **Contracts** | `contracts/api/auth.yaml` — `GET /accounts/me`, `AccountResponse`; unchanged |
| **Named tests** | `profileIsReadFromUserinfoAndAccountStatusFromMe` · `generatedClientCompiles` · `authorizeRequestsExactlyTheDeclaredScopes` · `emailVerifiedClaimIsNotAccountState` |
| **Open questions** | OQ-T31-1, OQ-T31-2, OQ-T31-3 (owner decisions), OQ-T31-4 (new), FU-T31-4, FU-T27-1 |

---

**T31 COMPLETE.** Fourteen phases, verdict **PASS**, branch `spec/service-specs-and-ai-framework`;
`main` stays deployable.
