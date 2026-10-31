# frontend · T30 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/11-test-review.md` |
| **Produces** | `artifacts/12-specification-verification.md` |

**Task statement (verbatim, `spec/frontend/tasks.md` task 30):**
> **Profile from userinfo.** Profile data is read from `/userinfo` and never from the access token. Verifies
> `profileIsReadFromUserinfoAndAccountStatusFromMe`. Cites R20.

---

## Traceability matrix

`R20` has three clauses and T30 owns the first and the third. The second — account status from
`GET /accounts/me` — is **T31's**, by the split frozen in phase 4.

| Requirement | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R20 (a)** profile data read from `/userinfo` | **Yes** | `profile/readProfile.ts:125` — `api.GET('/userinfo', …)`, the only transport in the module; declared at `contracts/api/oidc.yaml:51` | AC1–AC3, cases 1–6 | no | **T30-D2** — the contract change reaches outside `frontend/`; owner-authorized |
| **R20 (a)** scopes `openid profile email` | **Pre-existing, verified not regressed** | `auth/authorizeRequest.ts` requests exactly the three; **measured** on a live token: `scope: ["openid","profile","email"]` | `authorizeRequestsExactlyTheDeclaredScopes` (T15's, untouched) | no | — |
| **R20 (a)** the data actually arrives | **Blocked, not by this task** | live `/userinfo` returns `{"sub"}` alone; `spec/auth-service/design.md` **O6** | AC9 case 34 (sub-only is a success), AC11 case 35 (full profile parses) | **yes, upstream** | **T30-D1** — stated as a fact, not an `it.skip` |
| **R20 (b)** account status from `GET /accounts/me` | **Not T30** | — | — | **by design** | frozen split: T31 |
| **R20 (c)** no PII or verification state from the access token | **Yes, and this is the clause T30 exists for** | enforced by absence; proven over the comment-stripped tree | AC5 cases 15–22 | no | — |
| **R20 (c)** the one permitted token read | **Yes** | `auth/idTokenNonce.ts` — R7 nonce validation, one allowlist row, upper **and** lower bound | AC5 cases 15, 17, 18 | no | — |
| **L3** the token lives in memory only, one tab | **Yes, not weakened** | `readProfile.ts:90` reads it and holds nothing; no storage write on any path | AC6 cases 23, 27, 47 | no | — |
| **L6** every backend call goes through the generated client | **Yes** | `api/client.ts:51` `createOidcApi`; generated `oidc.ts` regenerated, never hand-edited | AC2 case 4, AC3 cases 5–6, `generatedClientCompiles` 10/10 | no | — |
| **L18 / R21** one renewal per tab, shared | **Yes, unchanged — the reader has no renewal logic** | `api/client.ts:54` passes the real boundary; `OIDC_PUBLIC_OPERATIONS` at `:25` holds exactly `POST /oauth2/token` | AC4 cases 7–10, 14 | no | **T30-D4** — the boundary is built but unwired; both arrive as parameters |
| **R23** a 429 shows fixed generic copy | **Yes, preserved through the new factory** | `api/client.ts:53` registers the notice **first** so it runs **last** | AC4 cases 11–13 | no | — |
| **L16** nothing persists beyond the sanctioned items | **Yes** | no write on any path, success or failure | AC6 cases 23, 27; AC7 cases 24, 26 | no | **T30-D7** — `document.cookie` and `indexedDB` declared unswept (jsdom) |
| **L4 / L11** neutral copy, catalogue-owned | **Yes** | `readProfile.ts:51` — `ReadProfileError.message` is internal **by construction**, looked up by key | AC8 case 32 (asserted by inversion) | no | **T30-D3** — no screen consumes it yet |
| **AC8** a failure surfaces no detail, no stack, no cause | **Yes** | `readProfile.ts:132` transport `catch`; `:140`/`:145`/`:148` carry nothing from the response | AC8 cases 28–33, 48, 49 | no | — |
| **AC12** no session means no request | **Yes** | `readProfile.ts:90` guards `null` **and** `''`; `:125` `redirect: 'manual'`; `:140` all three redirect arms | AC12 cases 38–43, 51–53 | no | **T30-D6** — the post-check clearance window |
| **AC9** a sub-only response is a success | **Yes** | `readProfile.ts:148` requires only a non-empty string `sub` | AC9 cases 34, 36, 50 | no | — |
| **AC10** every instrument shown to report dirty | **Yes** | 25 instruments, 12 named controls, the empty-tree refusal | AC10 cases 44–46 | no | — |
| **`emailVerifiedClaimIsNotAccountState`** | **Not T30** | — | — | **by design** | T32's named test |

## The four questions

### 1. Is the task fully complete?

**Yes, for everything inside T30's boundary — and one sentence of the task statement cannot be demonstrated
end-to-end by anyone.**

"Profile data is read from `/userinfo`" is implemented, and `/userinfo` on the running auth service returns
`{"sub":"<uuid>"}` and nothing else. That was **measured, not inferred** — with a real interactive token
carrying `scope: ["openid","profile","email"]`, so the scopes are granted and the shortfall is elsewhere:
Spring Authorization Server returns `sub` alone unless an OIDC user-info mapper is configured, and
auth-service configures none. It is recorded as `spec/auth-service/design.md` **O6** with reproduction
commands, under the owner's explicit authorization to edit that file.

The reader is written against the shape the **contract** declares, so closing O6 requires no change here, and
a case proves the full shape parses. The task's verification is complete; the data's arrival is upstream.

### 2. Does it satisfy every acceptance criterion?

**Yes — all twelve, and three of them are stronger than they were at phase 10.**

The clause worth singling out is **R20(c)**, because it is the only one an implementation can break
invisibly. Reading `email` or `email_verified` off the access token produces a plausible value, no error, and
no behavioural difference any runtime test could see. It is caught by a source scan or by nothing — which is
why MP01–MP05 exist and why MP05 was written to escape by sitting in a module nothing imports. A whole-tree
scan is the only instrument that sees dormant code.

The scan is **two instruments composed**, and that was forced by measurement: `email`, `name` and `sub` are
ordinary words this package uses constantly, so a tree-wide claim scan is meaningless. The decode scan bounds
the file set to exactly one — asserted, not assumed — and the claim scan runs only over that set.

### 3. Does it violate any LOCKED decision?

**No.** L3, L4, L6, L11, L16 and L18 are each either enforced or left untouched, and the one existing test
T30 edited was an inherited count raised by one (**T30-D5**).

Two LOCKED-adjacent calls are worth naming as judgements rather than omissions:

- **`api/sessionBoundary.ts` was not changed**, although T30-D6's side-effect window lives there. The frozen
  brief names it as not T30's, and every other client shares it.
- **`contracts/api/oidc.yaml` was changed**, which reaches outside `frontend/`. The file's own description
  says *"Adding any further path is a decision of its own, not a drive-by"* — so it was taken to the owner
  and authorized before anything was written (**T30-D2**).

### 4. Remaining risks?

**Upstream and stated:** O6 blocks the end-to-end profile read for everyone. **T30-D6:** the window between
the reader's `tokens.current()` check and the boundary's own read is measured and accepted — the outcome is
safe (`no-session`), but a bearer-less request is issued and provokes the `302` and its `Set-Cookie`.

**The honest risk inside the verification:** the decode scan is enumerative. It matches `atob`, `jwtDecode`,
`decodeJwt` and `split('.')`, and would not see `import { jwtVerify } from 'jose'`. Kimi raised it twice and
its recommended control was refused both times as self-contradictory — it asks for an assertion that the scan
*flags* `jwtVerify`, which it does not. The residual risk is instead guarded by something that actually
fires: **no JWT-decoding library is a dependency**, checked across both manifests against seven names. The
day one is added, that case fails and the pattern must widen before anything else proceeds.

**A risk that was real and is now closed:** two production defects reached phase 11 — a JSON `null` body
threw a raw `TypeError` out of the reader, and the token guard tested `=== null` so `''` passed it. Both were
found by Kimi, both verified by running the cited line, and **one of its two stated mechanisms was wrong in a
way that mattered** (`typeof null.sub` does not evaluate to `'undefined'`; property access on `null` throws).
Accepting the conclusion without checking the evidence would have left the defect's shape misunderstood.

**A process risk that was live and is now closed:** the harness scored two mutants it had never applied. A
stale anchor printed `BROKEN`, `BROKEN` is not `SURVIVED`, and the run still ended `survivors: none` — so 26
killed and 2 unapplied were published as 28 of 28. `preflight()` now refuses to start until every anchor
matches exactly once.

## Final verification

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0, no output |
| The file alone | **53 / 53**, exit 0 |
| Whole suite | **1451 passed / 1 failed (1452)** — `lintRejectsHandWrittenBackendFetch`, pre-existing and environmental on this host |
| Mutation | **34 / 34 killed, no survivors, nothing BROKEN**, every one by T30's own file |
| `generatedClientCompiles` | 10 / 10 |

## Deviations, final list

| ID | Deviation |
|---|---|
| **T30-D1** | `/userinfo` returns `{"sub"}` alone live (auth-service **O6**). Both shapes proven; the end-to-end read is upstream |
| **T30-D2** | `contracts/api/oidc.yaml` gained `GET /userinfo`, reaching outside `frontend/`. Owner-authorized |
| **T30-D3** | No screen consumes the profile; `/app/account` is unbuilt |
| **T30-D4** | The boundary is built but unwired, and no production `renew()` exists. Both are parameters |
| **T30-D5** | `tokensAreNeverWrittenToAnyStorage`'s `this.name =` count raised three → four |
| **T30-D6** | The token-clearance window between the reader's check and the boundary's read, measured and accepted |
| **T30-D7** | `document.cookie` and `indexedDB` declared unswept — jsdom gives an empty cookie jar and no `indexedDB`, so sweeping either reports clean for an environmental reason |

---

**PASS** — R20(a) and R20(c) are implemented and verified to 53 cases, 25 dirty-proven instruments and 34/34
mutants, with every gap stated: the profile's *content* is blocked upstream by auth-service O6, R20(b) is
T31's by the frozen split, and two defects Kimi found in phase 11 are fixed with their mechanisms measured
rather than assumed.
