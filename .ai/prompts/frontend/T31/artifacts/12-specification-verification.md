<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T31 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Consumes** | every prior artifact |
| **Produces** | `artifacts/12-specification-verification.md` |
| **Head** | `91a2db00` |

## Traceability matrix

| Requirement / AC | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R20(b)** account state is read from the account resource, never inferred | Yes | `profile/readAccount.ts:129-181` | 52 cases + 31 mutants | — | — |
| **R20(c)** verification state is not taken from a token claim | Yes | AC5 scan over the one decoding file | AC5, 5 cases | — | — |
| **AC1** the contract declares `GET /accounts/me`; the reader names no transport and never assembles the path | Yes | `contracts/api/auth.yaml:34`; `readAccount.ts:157` | AC1, 2 cases (one single-file, one tree-wide) | — | — |
| **AC2** the returned type is derived from `AccountResponse`, status unwidened | Yes | `readAccount.ts:43`; `profile/accountStatus.ts:31-45` | AC1/AC2, 2 cases + YAML comparison | — | — |
| **AC3** the bearer comes from the session boundary; the operation is not public | Yes | `readAccount.ts:157` via `AuthApi`; `AUTH_PUBLIC_OPERATIONS` excludes it | AC3, 2 cases (upper **and** lower bound) | — | — |
| **AC4** the boundary owns the 401 and its single-flight renewal | Yes | `readAccount.ts:173` | AC4, 4 cases | — | — |
| **AC5** no module reads `status` or `emailVerified` from a token | Yes | scan, bounded to the one decoding file | AC5, 5 cases | — | — |
| **AC6** all five statuses survive unchanged; `false` is an answer | Yes | `readAccount.ts:116-117,181` | AC6, 7 cases | — | — |
| **AC7** a required field missing, empty, blank, null or wrong-typed fails | Yes | `readAccount.ts:85,109-118` | AC7/AC8, 10 cases | — | — |
| **AC8** an unrecognised status fails CLOSED | Yes | `accountStatus.ts:48`; `readAccount.ts:117` | AC7/AC8 + MQ13/MQ16 | — | **G2** (frozen) |
| **AC9** a failure surfaces no detail — header, body, cause or message | Yes | `readAccount.ts:49-80,160-177` | AC9, 7 cases | — | — |
| **AC10** the account never persists and is never logged | Yes | nothing written anywhere in `readAccount.ts` | AC10, 4 cases | — | — |
| **AC11** R23's rate-limit notice still fires, on the call and the re-send | Yes | registration order in `api/client.ts` | AC11, 3 cases | — | — |
| **AC12** every instrument has been shown to report dirty | Yes | `T31_INSTRUMENTS`, 24 rows | AC12, 2 cases | — | — |
| **AC13** no session means no request at all | Yes | `readAccount.ts:145-146` | AC13, 5 cases | — | — |
| **L3** tokens stay in memory | Yes | unchanged; the reader holds no token | `tokensAreNeverWrittenToAnyStorage` 48/48 | — | — |
| **L6** no hand-written transport | Yes | `readAccount.ts:157` | AC1 + the lint instrument | — | — |
| **L10** the generated client is the only client | Yes | `AuthApi` | AC1/AC2 | — | — |
| **L11** visitor-facing copy is catalogue-owned | Yes | the three messages are internal, not catalogue keys | AC9, 1 case | — | — |
| **L18** no PII on any surface | Yes | seven-surface sweep + store emptiness | AC10, 4 cases | — | — |

### Named tests

| | Status |
|---|---|
| `profileIsReadFromUserinfoAndAccountStatusFromMe` | **107/107** — T30's 53 and T31's 54 in one file |
| `generatedClientCompiles` | green; `tsc --noEmit` exit 0 under **both** 7.0.2 and 5.9.3 |
| `authorizeRequestsExactlyTheDeclaredScopes` | untouched — T31 adds no scope |
| `emailVerifiedClaimIsNotAccountState` | extended by AC5 to cover `status` as well |

## Deviations

| | |
|---|---|
| **D1** | four additive import lines in T30's import block of the shared file |
| **D2** | T29's state scan gained `PERMITTED_STATE_SOURCE`, a corrected premise, and — after Kimi I2 and A2 — a carrier bound with its own dirty-proof |
| **D3** | T27's bare `tfa` left-bounded, with a T27-scoped control added in phase 9 |
| **D4** | the `this.name =` count in T26's instrument, 4 → 5 |
| **D5** | OQ-T31-3 stays open by owner decision |
| **D6** | `auth.yaml` unchanged by owner decision, with OQ-T31-1 and OQ-T31-2 logged |
| **D7** | **new in phase 11** — `profile/accountStatus.ts` was created so T29's allowlist licenses a file rather than a regex over a construct (Kimi A2), and `stateSurvivesRedirectAndIsConsumedOnce.test.tsx:718` lost its ES2023 call (Kimi A1). Both are edits outside T31's own files; both exist to hold T31's own exception and T31's own gate |

## The approving engineer's four questions

**1. Is the task fully complete?** Yes. The task statement is "account status is read from `GET /accounts/me`",
and that is what the module does, with the contract, the client, the boundary, the renewal and the rate-limit
notice all in place and asserted. Nothing in the task's scope is deferred.

**2. Does it satisfy every acceptance criterion?** Yes, thirteen of thirteen, each with at least one case and
at least one mutant. Three ACs needed a **source-shape** case rather than a behavioural one, and in every
instance for the same measured reason: the mutant produces an identical runtime result, so behaviour cannot
see it. AC1's assembled path (MQ06, MQ30) and AC9's reach for `answer.error` (MQ29) are both of that kind.

**3. Does it violate any LOCKED decision?** No. L3, L6, L10, L11, L14, L18 are each asserted, and L14 is
honoured by omission — T31 invents no route, no copy and no redirect, which is what keeps OQ-T31-3 open
rather than silently answered.

**4. Remaining risks.**

- **OQ-T31-4 (outside T31, logged twice, now correctly attributed).** `type-fest@0.16.0` reaches the program
  through `workbox-build` → `tempy` and declares `/// <reference lib="esnext"/>`, lifting this project's
  declared ES2022 ceiling for every file in `src/`. T31's concrete instance is fixed and the tree is swept
  clean of post-ES2022 APIs, but the widening is still in place for whatever is written next.
- **OQ-T31-3, by owner decision.** Nothing owns *acting* on a readable-but-not-ACTIVE account. T31 reads;
  task 33 owns failed-renewal re-auth (R22), a different trigger. This is the one gap a user could feel: a
  suspended account's tab keeps working for up to fifteen minutes and nothing yet consumes the status that
  says so.
- **OQ-T31-1 / OQ-T31-2.** `auth.yaml` declares no root-level `security:` and documents no 401 or 403, so
  every operation is nominally public while the server enforces `isAuthenticated()`. Logged by owner decision.
- **FU-T31-4.** `readProfile.ts` still guards `null || ''` and has I5's whitespace gap by the same mechanism.
  T30 is closed; left deliberately rather than widening this diff.
- **FU-T27-1.** `two[-_ ]?factor` is in no alternative of T27's MFA identifier pattern.
- **The honest one.** T31 touched four files it does not own, all to hold its own exception or its own gate.
  Each is dirty-proofed and MQ31 now reaches the T29 instrument from production source — which closes the gap
  my phase 7 S4 raised. The residual risk is not correctness but ownership: a later task editing those
  instruments has to read T31's reasons to avoid undoing them.

## Measured, finally

| | |
|---|---|
| T31's own cases | **54 / 54** |
| Shared file | **107 / 107** (T30's 53 untouched) |
| Inherited files touched | `identityErrors…` 58/58 · `loginChallenge…` 51/51 · `tokensAreNever…` 48/48 · `stateSurvives…` 29/29 |
| Whole suite | **1508 passed / 1 failed (1509)** — `lintRejectsHandWrittenBackendFetch`, environmental, pre-existing, pinned by name |
| Mutation | **31 / 31 killed**, 0 survivors, 0 broken |
| `tsc --noEmit` | exit 0 under TypeScript **7.0.2** and **5.9.3** |
| `oxlint src` | exit 0 |

Two Kimi phases produced eighteen findings across review and test review. Twelve were taken into code, two
rejected on measurement with the measurement recorded, two resolved as documentation, two needed no action.
Two mutants written for those findings proved **inert** and were replaced by reachable ones rather than
counted.

---

**VERDICT: PASS** — every acceptance criterion is implemented, tested and mutation-proven, with the one
account-state gap (OQ-T31-3) open by the owner's decision rather than by omission.
