<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation) -->

# frontend · T31 · Phase 10 — Test Manifest

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |
| **Test file** | `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` |
| **Harness** | `artifacts/07-mutation-harness.py` |

No production code was changed in this phase. The three production fixes resolving Kimi I5, I8 and I9
landed in phase 9 (`ec8cf25d`) and are recorded there.

## Where T31's tests live

| | |
|---|---|
| `describe('T31: account status from GET /accounts/me')` | **52 cases**, the whole task |
| Same file, `describe('T30: …')` | **53 cases**, untouched — 105 in the file |
| `identityErrorsIndistinguishableAcrossAccountStates.test.tsx` | **+1** (the place bound's dirty-proof, T31's exception) |
| `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` | **+1** (the `\btfa` narrowing's control, T31's narrowing) |

The two cases outside the file are there because the thing they bound is T31's: an exception T31 added to
T29's scan, and a narrowing T31 made to T27's pattern. An exception without a dirty-proof is how a scan rots,
and neither could be asserted from inside T31's own block.

## Named tests from the task header

| Named test | Where |
|---|---|
| `profileIsReadFromUserinfoAndAccountStatusFromMe` | **this file** — T31's 52 cases, beside T30's 53 |
| `generatedClientCompiles` | existing (T07); `tsc --noEmit` exit 0 covers the new `Account` derivation |
| `authorizeRequestsExactlyTheDeclaredScopes` | existing (T20); untouched — T31 adds no scope |
| `emailVerifiedClaimIsNotAccountState` | existing; AC5's claim scan is T31's extension of its property to `status` |

---

## Manifest

### AC1/AC2 — the contract declares the operation and the client exposes it typed · 4 cases

| Case | Verifies |
|---|---|
| declares the operation, and requires ALL FIVE fields — unlike `UserInfoResponse` | AC1, AC2 · R20(b) |
| names no transport of its own, and NAMES the path rather than assembling it | AC1 · L6 |
| exposes the operation on the generated client, typed | AC2 · L10 |
| `ACCOUNT_STATUSES` is exactly the contract enum, in the contract order | AC2 · the YAML comparison |

The second case exists because **MQ06 escaped the first mutation run**: an assembled `` `/accounts/${'me'}` ``
produces the identical runtime string, so no behavioural case can tell it from a literal. AC1 asked for "a
quoted literal … never assembled" in those words with no case behind it. It is killable by a source scan or
not at all.

### AC3/AC4/AC11 — the boundary supplies the bearer and owns the 401 · 8 cases

| Case | Verifies |
|---|---|
| sends the tab in-memory token as a bearer to `/accounts/me` | AC3 · L3 |
| lets the BOUNDARY renew on 401 and re-send, which the reader never sees | AC4 · R22 |
| surfaces a FINAL 401 as unauthorized, after the boundary has already tried once | AC4 |
| surfaces a 403 as unauthorized too, and renews for neither reason twice | AC4 |
| holds `/accounts/me` OUT of the public set, as an upper and a lower bound | AC3 · the allowlist both ways |
| preserves R23: a 429 on this call shows the notice | AC11 · R23 |
| shows the notice for a 429 on the RE-SEND, which is what the registration order is for | AC11 |
| CONTROL: a 200 leaves the rate-limit notice alone | AC11, non-vacuity |

`onResponse` runs in **reverse registration order**, so the rate-limit notice is registered FIRST to run
LAST. The re-send case is the one that fails if that order is ever flipped (MQ24).

### AC5 — no module reads `status` or `emailVerified` from a token · 5 cases

| Case | Verifies |
|---|---|
| the decode scan still bounds the file set to exactly one, which AC5 depends on | AC5 · the bound |
| reads neither `status` nor `emailVerified` off a decoded token, in the one file that decodes | AC5 · R20(c) |
| CONTROL: the scan flags both fields when a decoding file reads them | dirty-proof |
| CONTROL: the scan stays SILENT on the commissioned reads, which is why it is bounded | false-positive proof |
| CONTROL: a scan that reads nothing is refused, not reported clean | T22-D14 empty-tree refusal |

T31 defines its own `T31_FORBIDDEN_TOKEN_CLAIM` and **calls** T30's decode-scan helpers rather than editing
T30's constant — which is how G1 holds AC5 without amending the ownership rule T30 froze (Kimi's phase 3
blocker).

### AC6 — the account is read faithfully, status included · 7 cases

| Case | Verifies |
|---|---|
| returns each of the five statuses unchanged, nothing defaulted or inferred (`it.each`, 5) | AC6 · R20(b) |
| accepts `emailVerified: false` and returns it as `false` | AC6, AC7 |
| does not narrow `status` to a boolean or an is-active flag | AC6 |

`false` is an **answer, not an absence** — the guard is `typeof … === 'boolean'`, never truthiness, which was
Kimi's phase 3 AC7 finding and is killed by MQ12.

### AC7/AC8 — an incomplete or out-of-contract body fails · 10 cases

| Case | Verifies |
|---|---|
| rejects a body whose `accountUuid` / `email` / `createdAt` is missing (`it.each`, 3) | AC7 |
| rejects a body whose each field is empty, **BLANK**, null or wrong-typed (`it.each`, 3) | AC7 · Kimi I8 |
| rejects a missing, null or non-boolean `emailVerified` — but never `false` | AC7, AC6 |
| rejects an UNRECOGNISED status rather than passing it through or calling it ACTIVE | AC8 · G2 |
| rejects a JSON `null` body without reaching a property access | AC7 · T30's shipped defect |
| rejects a malformed JSON body, and a body that is an array | AC7 |
| CONTROL: the complete body succeeds, so the checks are not rejecting everything | non-vacuity |

The `null`-body case is here because the identical shape threw a raw `TypeError` out of `readProfile` and was
a **shipped defect found in T30's phase 11**. `'   '` is Kimi I8.

### AC9 — a failed read surfaces no detail · 6 cases

| Case | Verifies |
|---|---|
| carries nothing from `WWW-Authenticate`, which is where the live detail is | AC9 · measured |
| names no `cause` on any failure path, which is the other place detail can hide | AC9 |
| converts a THROWING transport into a neutral error, keeping neither message nor stack | AC9 · G5 |
| does the same for an abort and for a thrown non-Error | AC9 |
| the three messages are INTERNAL: none of them is in the catalogue | AC9 · L11 |
| CONTROL: an error carrying server detail WOULD be caught | dirty-proof |

AC9 was **measured against `/accounts/me` itself**, not inherited from T30's `/userinfo` — Kimi's phase 3
finding. The live 401 puts its detail in the header with an empty body, so the header is what the case
drives. The catalogue case is a **source-level pin** (Kimi I7): it proves the messages are not commissioned
copy, not that no screen renders them, and that obligation transfers with `failure` to the first consumer.

### AC10 — the account never persists and is never logged · 4 cases

| Case | Verifies |
|---|---|
| leaves no field on any of the seven surfaces after a successful read, **and both stores are empty** | AC10 · L18, Kimi I4 |
| writes nothing to either store, even on the failure paths | AC10 |
| CONTROL: the sweep reads all seven surfaces, proven with a sentinel on each | dirty-proof |
| CONTROL: all six console channels are watched, not only the ones a read happens to use | dirty-proof |

Store emptiness on the success path is Kimi I4, resolved by **rejecting** the recommended needle: `'true'`
and `'false'` are the least distinctive strings there are — the same reason `ACTIVE` is excluded. MQ26 proves
the emptiness assertion catches a cache no needle could see.

### AC13 — no session means no request · 5 cases

| Case | Verifies |
|---|---|
| issues NO request at all when the tab holds `null` / `''` / `' '` / `'\t\n'` (`it.each`, 4) | AC13 · Kimi I5 |
| CONTROL: a tab holding a token DOES issue the request, so the guard is not always refusing | non-vacuity |

Measured: a bearer-less call answers 401 **with `Set-Cookie: JSESSIONID`** — an unauthenticated read has a
server-side side effect, and it also provokes a renewal that cannot succeed. `''` sends no header at all;
whitespace is truthy and sends a malformed one. Both reach the same two side effects.

### AC12 — every T31 instrument has been shown to report dirty · 2 cases

| Case | Verifies |
|---|---|
| names one dirtying violation per instrument (**24 rows**) | AC12 |
| refuses a scan that reads nothing, through the one guard every walker calls | AC12 |

Three rows were added in phase 9: the state-word **place** bound (I2), the left-bounded `tfa` control (I3),
and store emptiness (I4). The first two name instruments living in other tasks' files, because the exception
and the narrowing they bound are T31's.

---

## Mutation map — 28 mutants, 28 killed, 0 broken, 0 survivors

| Mutant | Killed by | Failures |
|---|---|---|
| MQ01 `status` off the access token (**the subtlest**) | AC5 + AC6 | 20 |
| MQ02 `emailVerified` off the access token | AC5 + AC6 | 20 |
| MQ03/MQ04 the allowlisted decoding file reads them | AC5 | 2 / 2 |
| MQ05 the reader calls `fetch` directly | AC1 + L6 lint | 21 |
| MQ06 the path is assembled rather than named | AC1 source shape | 2 |
| MQ07 `/accounts/me` made PUBLIC | AC3 both bounds | 31 |
| MQ08 the reader renews the 401 itself | AC4 | 2 |
| MQ09 a 403 reported as unavailable | AC4 | 2 |
| MQ10 a final 401 becomes a SUCCESS with an invented account | AC4 | 5 |
| MQ11 the null-body guard removed | AC7 | 2 |
| MQ12 `emailVerified` checked for TRUTHINESS | AC6 + AC7 | 3 |
| MQ13 any string accepted as a status | AC8 | 6 |
| MQ14 the `accountUuid` check dropped | AC7 | 3 |
| MQ15 the whole shape check dropped | AC7 | 13 |
| MQ16 an unrecognised status DEFAULTED to ACTIVE | AC8 | 3 |
| MQ17 `status` narrowed to an is-active flag | AC6 | 5 |
| MQ18 the account cached in `sessionStorage` | AC10 | 7 |
| MQ19 the account status logged | AC10 | 3 |
| MQ20 `WWW-Authenticate` carried into a plain `Error` | AC9 | 5 |
| MQ21 the transport failure wrapped with a `cause` | AC9 | 2 |
| MQ22 the no-session short-circuit removed | AC13 | 6 |
| MQ23 the guard narrowed back to `null` only | AC13 | 4 |
| MQ24 the rate-limit notice registered AFTER the boundary | AC11 | 5 |
| MQ25 the runtime status list loses `DELETED` | AC2 | 4 |
| **MQ26** only the `emailVerified` flag cached | AC10 store emptiness | 6 |
| **MQ27** the no-session guard loses `trim()` | AC13 whitespace | 3 |
| **MQ28** blankness narrows back to emptiness | AC7 blank fields | 4 |

Every kill is by T31's **own** cases — no `INHERITED-ONLY` line in the record. MQ26–MQ28 are phase 9's, one
per behavioural fix, so no fix rests on a case alone.

### What the harness asserts about itself

- **Baseline, by count AND by name.** `EXPECTED_BASELINE_FAILURES = 1` plus
  `KNOWN_ENVIRONMENTAL_FAILURES = {"src/api/lintRejectsHandWrittenBackendFetch.test.ts"}` (Kimi I6) — a count
  alone absorbs a swap.
- **T31's own cases must be green on a clean tree**, else the run refuses.
- **`preflight()`** refuses to start unless every anchor matches its file **exactly once**.
- **`BROKEN` now fails the run** and prints beside `survivors:`. Until phase 9 it printed and was invisible to
  the exit code — T30's exact score-inflation defect in its one remaining hiding place.

## Gates

| | |
|---|---|
| T31's own cases | **52 / 52** |
| Shared file | **105 / 105** |
| Inherited files touched | `identityErrors…` 58/58 · `loginChallenge…` 51/51 · `tokensAreNever…` 48/48 |
| Whole suite | **1506 passed / 1 failed (1507)** — `lintRejectsHandWrittenBackendFetch`, environmental, pre-existing |
| Mutation | **28 / 28**, 0 broken, 0 survivors |
| `tsc --noEmit` | exit 0 (TypeScript 7.0.2) |
| `oxlint src` | exit 0 |

**Phase 10 complete.** 52 cases in T31's block, 2 more bounding T31's cross-task exception and narrowing, 24
instruments each with a dirty-proof, 28 of 28 mutants killed.
