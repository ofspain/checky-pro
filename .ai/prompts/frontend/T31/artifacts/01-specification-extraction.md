# frontend · T31 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## Business Rules

| ID | Rule, as it applies to T31 |
|---|---|
| **R20 (b)** | Account status is read from **`GET /accounts/me`**. This is T31's whole mandate. |
| **R20 (c)** | PII and verification state are **never** read from the access token. T30 proved this tree-wide; T31 must not regress it, and `status` and `emailVerified` are precisely the two fields a shortcut would reach for. |
| **R21** | A 401 runs **at most one renewal per tab**, shared across parallel 401s and sibling tabs. T31's endpoint answers a real 401, so this applies to it directly — unlike T30's. |
| **R22** | If renewal fails or the session is no longer valid: clean re-auth, **no half-authenticated screen**, neutral copy, sign-out propagating to every tab. **Owned by task 33**, not T31 — see OQ-T31-3. |
| **R23** | A 429 shows fixed generic copy, no countdown, no automatic replay of non-idempotent POSTs. `GET /accounts/me` is idempotent, so only the copy half applies. |
| **R28** | Only public routes are served while unauthenticated. T31 adds no route. |
| **R30** | Identity-related error copy, redirect and treatment are identical across causes. Discharged for the sign-in path by T29/task 58; T31 must not introduce a status-dependent error surface that reopens it. |

**R20's three clauses, and who owns them:** (a) profile from `/userinfo` — **T30, done**; (b) account status
from `GET /accounts/me` — **T31, this task**; (c) nothing from the access token — **T30 built the proof**,
T31 extends it to its own two fields. `emailVerifiedClaimIsNotAccountState` is **T32's**.

## Locked Decisions

| ID | Constraint on T31 |
|---|---|
| **L3** | Access tokens in memory only — never storage, logs or analytics. The account body is not a token, but it carries `email` and `accountUuid`, so the same sweep discipline applies. |
| **L4** | Enumeration-safe UI for every unauthenticated or **low-trust** caller, **without carve-out**. `/accounts/me` is authenticated and returns only the caller's **own** account — the backend derives it from the principal's `sub` and exposes no path parameter — so there is no enumeration surface here to protect. Stated because the absence is the point, not because the rule is irrelevant. |
| **L6** | Generated client only. `operations["getOwnAccount"]` already exists; **no regeneration is needed**. |
| **L9** | Single origin — all traffic through the edge path map; no per-service URL. |
| **L11** | Accessibility and i18n on every screen; every user-facing string in the catalogue. Relevant to T31 only as a prohibition: an error message it invents must not be renderable as copy. |
| **L14** | The route set is fixed in §4c. `/app/account` **does not exist**, so T31 renders nothing. |
| **L16** | `sessionStorage` holds only the listed items. The account body is not among them, so it must not persist. |
| **L18** | Single-flight renewal and cross-tab coherence. T31 consumes the boundary; it does not reimplement any part of it. |

## Files involved

### Read / extend

| File | Why |
|---|---|
| `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` | The named test. **T31 adds only `describe('T31: …')` blocks**; T30 owns imports, setup, helpers and all T30 blocks (frozen in T30 phase 4 from Kimi F6). |
| `frontend/src/profile/readProfile.ts` | The sibling reader. Its shape — one function, a derived type, an error neutral by construction — is the pattern to follow or to deliberately depart from. |
| `frontend/src/api/client.ts` | `createAuthApi`, `AUTH_PUBLIC_OPERATIONS`. **Read only** — `GET /accounts/me` is already correctly absent from the public set. |
| `contracts/api/auth.yaml` | `:34` the path, `:659` `AccountResponse`. Read. Any change is OQ-T31-1/2. |
| `libs/ts/api-client/src/generated/auth.ts` | `:24,:32,:658`. Read; **do not hand-edit**. |

### New

- A reader for the account, beside `readProfile.ts`. **Whether that is a new module or an addition to the
  existing one is Phase 2's decision.**

**No new contract path, no regeneration, no `spec/` edit, and no new route.** T31's footprint is materially
smaller than T30's.

## Dependencies

| Kind | Item |
|---|---|
| Contract | `contracts/api/auth.yaml` — `GET /accounts/me` → `AccountResponse` `{accountUuid, email, emailVerified, status, createdAt}`, `status ∈ {PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, DELETED}`, all five fields **required** |
| Generated type | `components['schemas']['AccountResponse']` from `@themistra/api-client` — derive, never restate (T30's F10, which T30's own first draft failed to honour) |
| Client factory | `createAuthApi(boundary, rateLimit?)` — `client.ts:33` |
| Boundary | `api/sessionBoundary.ts` — `SessionTokens.current()`, `renew()`, `middleware(publicOps)` |
| Rate limit | `api/rateLimitNotice.ts` — `createRateLimitNotice()`, registered **first** so it runs **last** |
| Backend | `AccountController.me(Authentication)` — `@PreAuthorize("isAuthenticated()")`, account derived from `sub` |
| Config keys | none new |

**Difference from T30 worth stating once:** `AccountResponse` requires **all five** fields, where
`UserInfoResponse` requires only `sub`. So absence is an error here and was a normal answer there, and the two
readers cannot share a validation rule even if they share a shape.

## Acceptance Criteria

| AC | Criterion | Requirement |
|---|---|---|
| **AC1** | The account is read through the generated client at `GET /accounts/me`, typed by the schema path — one call, no hand-written transport. | R20(b), L6 |
| **AC2** | The returned type is **derived** from the generated `AccountResponse`, not restated. | L6 |
| **AC3** | The call carries the tab's in-memory token as a bearer **via the boundary**, and `GET /accounts/me` is absent from `AUTH_PUBLIC_OPERATIONS` — asserted as an upper and lower bound. | R21, L18 |
| **AC4** | A **401 is the boundary's**: one renewal, one re-send, and the reader never renews. A final 401 surfaces as a neutral failure. | R21, L18 |
| **AC5** | **Neither `status` nor `emailVerified` is read from any token**, anywhere in the tree. Extends T30's two composed scans to T31's two fields. | **R20(c)** |
| **AC6** | A complete `AccountResponse` with **all five statuses** is read faithfully — `status` is reported as the server sent it, never defaulted, coerced or narrowed to a boolean. | R20(b), L8-adjacent truthfulness |
| **AC7** | An **incomplete** body is a failure, not a partial account: a missing or empty `accountUuid`, `status`, `email`, `emailVerified` or `createdAt` is rejected, because the contract requires all five. | R20(b) |
| **AC8** | An **unknown** `status` string is not silently treated as ACTIVE. | R20(b) |
| **AC9** | A failed read surfaces **no detail, no stack and no cause** — including from the **`WWW-Authenticate` header**, which is where the measured 401 puts it, with an empty body. | R22-adjacent, L4 |
| **AC10** | The account body never persists and is never logged: absent from `sessionStorage`, `localStorage`, `document.title`, `window.name`, the URL and all six console channels, raw and percent-decoded. | L3, L16 |
| **AC11** | R23 is preserved: a 429 on this call shows the notice, including a 429 on the boundary's **re-send after a renewal**, which is the case that distinguishes middleware registration order. | R23 |
| **AC12** | Every instrument is shown to report DIRTY first, and a scan that reads nothing is **refused** rather than reported clean. | T22-D14 |

**Deliberately not an acceptance criterion:** anything about what the SPA *does* with a non-ACTIVE status.
The task statement is "account status **is read** from `GET /accounts/me`". See OQ-T31-3.

## Tests required

| Test | Status |
|---|---|
| `profileIsReadFromUserinfoAndAccountStatusFromMe` | **T31's half.** Exists at `frontend/src/profile/…`, 53 T30 cases; T31 adds `describe('T31: …')` blocks only |
| `generatedClientCompiles` | Must stay green — 10/10 today. No regeneration expected, so this is a guard, not a deliverable |
| `authorizeRequestsExactlyTheDeclaredScopes` | T15's, untouched |
| `emailVerifiedClaimIsNotAccountState` | **T32's.** T31 must not pre-empt it, and must not leave `emailVerified` unconstrained in a way that makes T32 unprovable |

**Boundary tests implied by the measurements:** a real 401 with an empty body and header-borne detail; a 401
followed by a successful re-send; a final 401; a 429 on the re-send; a transport that **throws** (openapi-fetch
rejects rather than resolving); a body missing each required field in turn; an unknown status string; and a
status that is **not** ACTIVE while the token is still valid — which is measured-reachable, not hypothetical.

## Open Questions

### OQ-T31-1 — `auth.yaml` declares no global `security`, so every operation in it is nominally public

Carried from Phase 0 with no change. The file declares `securitySchemes` (`:572`) and marks five operations
`security: []`, but has **no root-level `security:`** — so in OpenAPI 3 there is no requirement to inherit and
`GET /accounts/me` is documented as needing no credential, while the server enforces
`@PreAuthorize("isAuthenticated()")`. The five `security: []` stanzas are no-ops that read as meaningful, and
`client.ts:5` describes `AUTH_PUBLIC_OPERATIONS` as "the `security: []` operations of
contracts/api/auth.yaml" — a hand-maintained set standing in for a statement the document does not make.

**This is the same defect Kimi's I4 found in `oidc.yaml` during T30.** The fix there was per-operation; here
the correct fix is a root-level `security: [- bearerAuth: []]`, which would make all five existing stanzas
mean what they appear to mean.

**Not a blocker for T31.** It changes nothing about what the code must do — the server requires a bearer and
the client sends one — and it is a change to a contract shared by every service, affecting every operation in
the file rather than T31's one. **Owner decision**, on T30-D2's precedent. AC1 can state the requirement
honestly either way, as long as it does not assert a security property the document does not express; T30
phase 6 made exactly that mistake and the assertion survived review because it was only ever textual.

### OQ-T31-2 — `auth.yaml` documents no 401 or 403 anywhere

Zero occurrences in the whole file; `/accounts/me` declares only a `200`. The **measured** 401 — the one R21
and the boundary exist to act on — is undocumented, as are its empty body and its `WWW-Authenticate` detail.
Same owner question as OQ-T31-1, and the same non-blocking status: the reader is written against measured
behaviour, and AC9 asserts the header case regardless of what the document says.

### OQ-T31-3 — nothing owns acting on a non-ACTIVE status, and the situation is measured-reachable

**This is the one genuine gap, and it is a specification gap rather than a T31 gap.**

Phase 0 measured that suspending an account through `POST /admin/accounts/{uuid}/suspend` does **not**
invalidate the existing access token: the same token then returns `200` with `status: "SUSPENDED"`. So a tab
can hold a working session for a suspended account for up to the token's 15 minutes, and
**`GET /accounts/me` is the only thing that can tell it so** — no 401 arrives.

**R22** covers "renewal fails **or the session is no longer valid**" and forbids a half-authenticated screen.
A suspended account whose token still works is arguably exactly a half-authenticated session — but R22's
mechanism is renewal, and **task 33** owns it (`failedRenewalTriggersCleanReauthWithoutHalfSession`). Reading
`tasks.md` 30–33 together, no unit owns "the account is readable and not ACTIVE":

- **31** reads the status. (This task. The verb in the statement is *read*.)
- **32** keeps `email_verified` out of verification UI.
- **33** handles a failed **renewal** — a different trigger entirely.

**Disposition, pending owner input:** T31 implements the **read** and proves the status is reported faithfully
and never invented, which is what its statement asks and what any consumer will need. It does **not** invent a
route, a copy key or a redirect for a non-ACTIVE status, because L14 fixes the route set, L11 requires catalogue
copy, and inventing either would commit the product to behaviour no requirement states. The gap is logged here
so the owner can decide whether it belongs to task 33, to a new unit, or to the account screen when it is
built — rather than being silently absorbed into T31 or silently dropped.

### Not blockers, recorded for completeness

- **`/app/account` does not exist** (T30-D3). T31 renders nothing, so this constrains only what can be
  asserted about surfaces: the sweep is over the document after a read, as T30's is.
- **No production `renew()` and no composition root wiring** (T30-D4). T31 takes the client and the token
  accessor as parameters, as `readProfile` does.
- **`spec/auth-service/design.md` O6** blocks T30's half end-to-end. **T31 has no such blocker** — measured
  `200` with the complete body.

---

**Phase 1 complete.** Twelve acceptance criteria, eight requirement and nine LOCKED IDs in scope, three open
questions of which **only OQ-T31-3 is substantive** — and it is a gap in the task breakdown, not in T31. No
design proposed.
