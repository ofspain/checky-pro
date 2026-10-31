# frontend · T31 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement (verbatim, `spec/frontend/tasks.md` task 31):**
> **Account status from accounts/me.** Account status is read from `GET /accounts/me`. Verifies
> `profileIsReadFromUserinfoAndAccountStatusFromMe`. Cites R20.

T31 is the second of R20's three clauses. T30 built the first (`/userinfo`) and proved the third (no PII or
verification state from the access token); **T32** owns `emailVerifiedClaimIsNotAccountState`.

**Everything measured below was measured on the running stack on 2026-10-31**, not read off SAS or inferred
from the contract. That discipline is here because T30's premise turned out to be false when measured, and
because a carry-forward note of mine survived several tasks while being wrong in both halves.

---

## 1. Architecture summary

The prompt's headings are Java-service shaped (JPA/Flyway, outbox, resource-server security). `frontend` has
none of those, so this section answers the honest equivalents rather than inventing matches.

- **Stack.** React 19.3.1 + TypeScript, Vite, react-router under an `/app` basename. Vitest 5.0.5 + jsdom
  30.1.3. The run recipe is `npx -y pnpm@12.14.0 exec …` — bare `pnpm` is not on this host.
- **Persistence.** None by design. **L3**: the access token lives in memory, in one tab, and is never written
  to any storage. **L16** limits `sessionStorage` to a short sanctioned list — measured in T26 as exactly four
  keys from two modules. `localStorage`, IndexedDB, cookies and Cache Storage have **no production caller at
  all**.
- **Events/outbox.** Not applicable. The closest analogue is cross-tab coordination (`api/crossTabRenewal.ts`)
  and the service-worker denylist that keeps API responses uncached.
- **Security boundary.** `api/sessionBoundary.ts` is the one place a bearer is attached and the one place a
  401 triggers renewal — at most one renewal per tab, shared across parallel 401s and sibling tabs (**R21**,
  **L18**). Every backend call goes through a generated client (**L6**); hand-written `fetch` is blocked by
  two oxlint rules, which ban the **identifiers** `fetch` and `XMLHttpRequest` and seven `window` properties,
  not a list of routes.
- **Two generated clients.** `createAuthClient` from `contracts/api/auth.yaml` and `createOidcClient` from
  `contracts/api/oidc.yaml`. **T31 lives entirely in the first**, which is the one difference that drives most
  of what follows.

## 2. Existing code this task touches

### Already exists and is complete

| Thing | State |
|---|---|
| `contracts/api/auth.yaml` → `GET /accounts/me` | `:34`, `operationId: getOwnAccount`, `$ref` to `AccountResponse`. **Already there — T31 needs no contract path added**, unlike T30 |
| `AccountResponse` schema | `:659` — `required: [accountUuid, email, emailVerified, status, createdAt]`, `status` an enum of five |
| Generated client | `libs/ts/api-client/src/generated/auth.ts:24,32,658` — the path, the `get`, and `operations["getOwnAccount"]`. **Already generated; no regeneration needed** |
| `createAuthApi` | `frontend/src/api/client.ts:33` — the real boundary, the rate-limit notice registered first |
| `AUTH_PUBLIC_OPERATIONS` | `client.ts:6` — five `POST`s, and **`GET /accounts/me` is correctly absent**, so it already carries a bearer and already renews on 401 |
| Backend handler | `services/auth/src/main/java/com/themistra/auth/account/AccountController.java:71` — `@GetMapping("/me")`, `@PreAuthorize("isAuthenticated()")`, derives the account from the principal's `sub` so there is no path parameter to substitute |
| Backend DTO | `AccountResponse.java` — matches the contract field for field |
| `AccountStatus` enum | `PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, DELETED` — the same five T29 drove through the sign-in failure path |

**This is the striking difference from T30.** T30 had to add a contract path, get owner authorization,
regenerate a shared client, and then found the server returning `{"sub"}` alone. **T31's full stack already
exists and works.** The contract, the generated client, the factory, the public-operations set and the backend
are all in place and consistent.

### New in T31

- `describe('T31: …')` blocks in `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx`.
- Probably a reader module beside `profile/readProfile.ts`. **Whether that is one module or two is Phase 2's
  decision, not this phase's.**

### Shared-file ownership, already frozen

The test file's header (`:12-19`) states it: **T30 owns the imports, the setup, every helper and every
`describe('T30: …')` block. T31 adds only `describe('T31: …')` blocks**, and may touch a T30 block only to
repair a compilation error caused by a renamed export. This came from Kimi's F6 in T30 phase 3 and was frozen
in T30 phase 4. T31 inherits it as a constraint, not a suggestion.

## 3. Established patterns to follow

- **Call through the generated client, typed by the schema path.** `api.GET('/accounts/me')`. The schema path
  *is* openapi-fetch's first argument, so "the module does not contain the path" is not an assertable
  property — T30 learned this by writing the wrong assertion first.
- **`openapi-fetch` resolves an HTTP error status but REJECTS when the underlying `fetch` throws.** Any reader
  needs a `try/catch` around the call. T30 shipped without one and a raw `TypeError` carrying an internal
  hostname escaped; it was Kimi's one High-confidence finding.
- **Register the rate-limit notice FIRST** so it runs LAST — `onResponse` runs in reverse registration order,
  and the case that distinguishes the orders is a 429 on the boundary's re-send after a renewal, not a 429 on
  the first call.
- **Errors neutral by construction.** `ReadProfileError` looks its message up by key from a
  `Record<Failure, string>`, so there is nowhere to put detail. Its `message` is internal and must not be
  rendered (**L11**); a consumer maps the `failure` value onto a catalogue key.
- **Absence is proven by scanning the comment-stripped tree**, with a literal-aware stripper (a regex
  stripper eats `'https://…'`), an allowlist held as an upper **and** lower bound, an empty-tree refusal, and
  every instrument shown to report dirty before it is trusted.
- **Flat `application.properties`, never YAML** — backend-side, and not something T31 touches.

## 4. Testing conventions

- **The vitest exit code is the verdict, not the pass counts** — an unhandled rejection prints below the
  all-passed line.
- **One known environmental failure**, pre-existing and unrelated:
  `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, because this host's `oxlint@1.87.0` prints no summary
  line. The clean baseline is **1 failed of 1452**.
- **Node's `Request` rejects relative URLs.** The package idiom is `installBrowserRequest()`, now also in
  T30's harness in the shared file.
- **A real boundary, not a stub.** T30's harness builds `createSessionBoundary` and `createRateLimitNotice`
  for real, because "the boundary owns the 401" is made true by construction if the boundary is stubbed.
- **Mutation testing is the standard of proof**, and the harness must assert **both** its baseline and its
  anchors before scoring. Both guards exist because both failures happened: T29's baseline drifted and
  converted two kills into reported survivors, and T30's `GET_CALL` anchor went stale so two mutants printed
  `BROKEN` — which is not `SURVIVED` — and a 26-killed run was published as 28 of 28.
- **Commit tests before running the harness.** It reverts with `git checkout -- src/`; uncommitted edits are
  destroyed silently (19 cases in T14, 5 in T15).
- No Testcontainers, no fixed `Clock`, no ArchUnit on this side. The analogue of ArchUnit here is the
  comment-stripped source scan.

## 5. Measured facts

All three bearer shapes, `GET http://localhost:18080/accounts/me`, 2026-10-31:

| Request | Response |
|---|---|
| **A real bearer** | **`200`** with the **complete** body: `{"accountUuid":"3cbdb847-…","email":"dev-user-…@example.com","emailVerified":true,"status":"ACTIVE","createdAt":"2026-10-31T18:34:21.699390Z"}` |
| **No bearer** | **`401`**, `WWW-Authenticate: Bearer`, **empty body** (`Content-Length: 0`), and a `Set-Cookie: JSESSIONID` |
| **A malformed bearer** | **`401`**, empty body, detail in the header: `WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Malformed token", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"` |

**This endpoint behaves nothing like `/userinfo`, and the difference is the whole shape of the task.**
`/userinfo` is a SAS **protocol** endpoint: with no bearer it answers `302` to `/login`, which `fetch` follows
into `200 text/html`, so there is no 401 for the boundary to act on and T30 needed a no-token short-circuit
and `redirect: 'manual'`. `/accounts/me` is an ordinary **resource-server** endpoint: it answers a real `401`,
so **the session boundary's renewal path works exactly as designed and T31 inherits it for free.**

**Two things carry over unchanged.** The 401 body is empty and the detail is in `WWW-Authenticate` — so a
no-leak rule written about response *bodies* would miss the only place the detail is. And an unauthenticated
request still sets a `JSESSIONID`, so "no token, no request" remains worth something even without the 302.

### The non-obvious measurement

**A non-ACTIVE status is observable, and a suspension does not invalidate an existing token.** Measured by
suspending the account through `POST /admin/accounts/{uuid}/suspend` with an ADMIN token (`200`), then
re-reading `/accounts/me` with the **same, unchanged USER token**:

```
HTTP/1.1 200
{"accountUuid":"3cbdb847-…","emailVerified":true,"status":"SUSPENDED", …}
```

So `GET /accounts/me` is **the SPA's only way to learn that the account went bad mid-session**, and T31 is
load-bearing rather than ceremonial: the 15-minute access token keeps working, no 401 arrives, and nothing
else tells the tab. **R22** — never show a half-authenticated screen — depends on this read. It also means
fixtures across the five statuses are realistic rather than hypothetical, at least for `SUSPENDED`.

I have **not** measured `LOCKED`, `DELETED` or `PENDING_VERIFICATION` through this endpoint, and whether all
five are reachable with a usable token is a Phase 1 question, not a Phase 0 answer.

## 6. Known gaps, unknowns, and open questions

### OQ-T31-1 — `auth.yaml` declares no global `security`, so every operation in it is nominally public

`contracts/api/auth.yaml` declares `components.securitySchemes.bearerAuth` and `apiKeyAuth` (`:572`) and
marks five operations `security: []`. It has **no root-level `security:` key** — verified by parsing its root
keys: `openapi`, `info`, `servers`, `paths`, `components`.

In OpenAPI 3 an operation with no `security` inherits the **global** requirement, and when the document
declares none there is no requirement at all. So the document says `GET /accounts/me` needs no credential,
while the server enforces `@PreAuthorize("isAuthenticated()")`, and the five `security: []` stanzas are
**no-ops that read as meaningful**. `client.ts:5` even describes `AUTH_PUBLIC_OPERATIONS` as "the
`security: []` operations of contracts/api/auth.yaml" — a hand-maintained set standing in for a contract
statement the document does not make.

**This is exactly the defect Kimi's I4 found in `oidc.yaml` during T30**, where the fix was to declare the
scheme and require it on the operation. Here the correct fix is a **root-level `security: [- bearerAuth: []]`**,
which would make all five `security: []` stanzas mean what they already appear to mean.

**Logged, not acted on.** It is a change to a contract shared by every service and every client, it affects
every operation in the file rather than T31's one, and T30 established that a change to `contracts/` is an
owner decision. Flagging it per the guardrail rather than deviating silently.

### OQ-T31-2 — `auth.yaml` documents no 401 or 403 anywhere

Zero occurrences of `'401'` or `'403'` in the whole file, and `/accounts/me` declares only a `200`. The
measured 401 — the one the session boundary is built to act on — is **undocumented**, as is its empty body and
its `WWW-Authenticate` detail. T30 resolved the equivalent by documenting the measured statuses in
`oidc.yaml`; whether that is in T31's scope for a file this widely shared is a Phase 1/2 question.

### Unknowns I am not going to guess at

- **Which of the five statuses are reachable with a usable token.** `ACTIVE` and `SUSPENDED` are measured.
  `LOCKED`, `DELETED` and `PENDING_VERIFICATION` are not, and whether a token survives into them is a
  question about auth-service's lifecycle, not about the SPA.
- **What the SPA should DO with a non-ACTIVE status.** R22 says no half-authenticated screen and neutral copy;
  which screen, which copy key and which route is **not something I know**, and `/app/account` does not exist
  (T30-D3). Phase 1 extracts it from `requirements.md` and `design.md`; I am not inventing it here.
- **Whether T31 shares `readProfile.ts`'s error type or declares its own.** A design question for Phase 2.
- **The relationship to T29's uniformity rule.** T29 proved identity failures are indistinguishable across all
  five account states *at sign-in*. T31 reads the status *after* a successful sign-in. These are different
  moments and I believe there is no conflict — but "I believe" is not evidence, and Phase 1 should state the
  boundary explicitly rather than leave a future reader to wonder whether T31 undoes T29.

### Carried forward, not T31's

`spec/auth-service/design.md` **O6** (no OIDC user-info mapper) blocks T30's end-to-end read for everyone.
**OQ1** — the live SAS `/login` renders no `mfaCode` field while the backend requires it in the same POST.
Unowned by any task: `mountShell.tsx`, `oidcRoundTripEndToEnd` (no Playwright suite), **L16 clause (b)**. The
phase 13 template's stale `Claude Opus 4.8 (1M context)` trailer, whose single source is `.ai/generate.py:476`
— noted in twelve consecutive tasks.

---

**Phase 0 complete.** No code written, no design proposed. The headline: **T31's stack already exists end to
end and the endpoint returns its data**, which is the opposite of where T30 started — and a suspended account
keeps a working token, which is what makes this read matter.
