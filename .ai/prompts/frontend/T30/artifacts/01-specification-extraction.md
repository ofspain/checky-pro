# frontend · T30 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Phase 0 said the first thing to do was re-measure `/userinfo`. That has now been done, against the live
stack, today.** The result is in §1 and it changes the task.

---

## 1 · The measurement

`infra/local/bin/up` + `status --wait` (all four services ready), `bin/dev-account USER` (a real
register → verify → TOTP → PKCE sign-in), then a direct call. **2026-10-31:**

```
GET /userinfo   (Bearer, real interactive access token)
→ HTTP 200
→ {"sub":"a07e59f8-b047-494a-85b9-14d10d352186"}
```

| What | Measured |
|---|---|
| `/userinfo` body | **`sub` and nothing else** |
| `id_token` claims | `aud, auth_time, azp, exp, iat, iss, jti, nonce, sid, sub` — **no `email`, no `name`, no profile claim of any kind** |
| access-token `scope` | **`["openid","profile","email"]`** |
| access-token `email_verified` | `true` |
| discovery `scopes_supported` | **`["openid"]`** |
| discovery `claims_supported` | **absent** |

### This confirms T19 and refutes my own Phase 0 hypothesis

T19 measured `/userinfo` returning only `{"sub"}`. **It still does, nine days and several tasks later.**

But Phase 0 guessed a cause — that discovery advertising `scopes_supported: ["openid"]` meant the `profile`
and `email` scopes were not actually being granted, so `/userinfo` had nothing to return. **That is wrong.**
The access token carries `scope: ["openid","profile","email"]`. The scopes *are* requested, *are* authorized
and *are* issued. The guess was the reasonable reading of T19's sentence and it does not survive contact with
a real token.

**The actual cause is narrower:** Spring Authorization Server returns only `sub` from `/userinfo` unless an
OIDC user-info mapper is configured, and `auth-service` configures none. The `profile` and `email` scopes are
granted and then nothing maps them onto a response. The same absence explains the empty `id_token` — SAS
populates standard claims from the same source.

**And `scopes_supported: ["openid"]` is a second, separate defect**: discovery under-reports what the server
actually grants. Cosmetic next to the first, real, and it misleads any conformance tooling pointed at it.

Both are **`auth-service` gaps, not frontend ones.** Neither is T30's to fix.

## 2 · Requirement in scope

**R20** (`requirements.md:66-67`), in full:

> WHEN the SPA needs profile data, THEN it SHALL read it from `/userinfo` (scopes `openid profile email`) and
> account status from `GET /accounts/me`, and SHALL NOT read PII or verification state from the access token.
> (backend R48; `token-claims.md`)

Three clauses, and they are not equally buildable today:

| Clause | Owner | Buildable today? |
|---|---|---|
| (a) profile data read from `/userinfo` | **T30** | **No** — the endpoint returns no profile data (§1) |
| (b) account status read from `GET /accounts/me` | **T31** | Yes — the operation exists on the generated client and the schema is complete |
| (c) **SHALL NOT** read PII or verification state from the access token | T30 + **T32** | Yes — and it is an *absence* claim, which this package knows how to prove |

Clause (c) is the one that carries the security weight. (a) and (b) say *where* to read; (c) says what must
never be read, and it is the clause an implementation can violate silently.

## 3 · LOCKED decisions in scope

`design.md` §4a cites none inline for T30, so these are derived:

| ID | Text, as it bears on T30 |
|---|---|
| **L6** | Every backend call goes through a client generated from `contracts/api/*.yaml`. One carve-out, the notification stream (R59). Enforced by `lintRejectsHandWrittenBackendFetch` |
| **L3** | Tokens live in memory only |
| **L11** | Keyboard operation, managed focus, live-region announcement, WCAG AA — on every screen |
| **L13** | Confirmed stack includes TanStack Query and Zustand, **neither wired yet** |
| **L16** | sessionStorage holds only the listed items. **A profile is PII and may not be persisted client-side at all** (`agents.md` §Storage) |
| **L9** | Single origin; `/userinfo` is edge-mapped to the auth service (`design.md:158`) |

`design.md:133-135` lists `/userinfo` among "OIDC and SAS endpoints (standard Spring Authorization Server,
owned by the auth service, **not in `auth.yaml`**)" — the spec already knows it is not in the main contract.

## 4 · The contract question, resolved further than Phase 0 could

Phase 0 framed this as "R20 requires a call the sanctioned mechanism cannot make". That was half right. The
missing fact is **`contracts/api/oidc.yaml`**, which Phase 0 did not find:

- It exists, and it generates `createOidcClient`, already used for `POST /oauth2/token`.
- Its own description states its purpose: it exists *because* L6 requires generated clients and auth.yaml
  intentionally excludes the SAS protocol surface.
- It documents **exactly one path**, `/oauth2/token`.

So there is a sanctioned, precedented home for `/userinfo`, and putting it there would satisfy L6 without any
hand-written fetch. **But the file forbids doing that casually, in its own words:**

> "Only endpoints a client calls programmatically belong here. `/oauth2/authorize` is a browser navigation,
> not a request, and is not documented. **Adding any further path is a decision of its own, not a drive-by.**"

`/userinfo` *is* called programmatically, so it qualifies on the stated test. But the sentence is explicit
that qualifying is not the same as being authorized, and T30's guardrails forbid scope beyond the task. A
contract change also regenerates the shared client in CI, which reaches past `frontend/`.

**This is an owner decision and is logged as OQ-T30-1 below rather than taken here.**

## 5 · Named tests

| Test | Cited by | Status |
|---|---|---|
| `profileIsReadFromUserinfoAndAccountStatusFromMe` | **T30** and **T31** | No file exists |
| `emailVerifiedClaimIsNotAccountState` | **T32** | No file exists |
| `authorizeRequestsExactlyTheDeclaredScopes` | built | Proves the SPA *asks* for three scopes. Nothing proved SAS *grants* them — **§1 now does** |
| `generatedClientCompiles` | built | Would cover a regenerated client if `/userinfo` were added |

**The T30/T31 split**, following the package's own precedent (T24 created
`returnTargetOutsideAllowlistFallsBackToHome` at 67 cases, T25 grew it to 184; T29 owns one axis of
`identityErrorsIndistinguishableAcrossAccountStates`, T58 extends, T136 gates):

- **T30 creates the file** and owns the `/userinfo` axis **and clause (c)**, the prohibition on reading PII
  or verification state from the access token.
- **T31 extends it** with the `GET /accounts/me` axis.

Phase 4 freezes this.

## 6 · Open questions

### OQ-T30-1 · May T30 add `/userinfo` to `contracts/api/oidc.yaml`? — **BLOCKING for clause (a)**

The file says adding a path is "a decision of its own, not a drive-by", and the change regenerates a shared
client. Three options, with the recommendation stated:

| | Option | Consequence |
|---|---|---|
| **A** | **Recommended.** Add `/userinfo` to `oidc.yaml`; T30 builds the reader against it and proves clause (c) | Follows the `/oauth2/token` precedent exactly. The reader is correct but returns only `sub` until the backend gap is closed, so the end-to-end assertion must be marked as pending that fix |
| **B** | T30 proves clause (c) only — nothing reads PII or verification state from the token — and defers (a) | Entirely within frontend scope, needs no contract change, and delivers the clause that carries the security weight. Leaves R20 (a) unowned |
| **C** | Defer T30 until `auth-service` ships a user-info mapper | Honest, and blocks a gatekeeper-phase task on another service's backlog |

### OQ-T30-2 · The two `auth-service` defects measured in §1 — **not T30's, needs an owner**

1. `/userinfo` returns only `sub` although `profile` and `email` are granted. No OIDC user-info mapper is
   configured. **This makes R20 clause (a) unbuildable end-to-end by anyone.**
2. Discovery advertises `scopes_supported: ["openid"]` while the server grants three, and omits
   `claims_supported` entirely.

Both are reproducible with the commands in §1. Neither has a task in `spec/frontend/`, and R20 cites
*backend R48*, so the auth package is the likely home.

### OQ-T30-3 · Does "profile data" have a consumer yet?

No screen displays a name or an email today. The authenticated route set exists (`/app/home`, `/app/account`)
but `/app/account` is not built. T30 may be building a reader with no reader of its own. Phase 2 decides
whether that is a module plus its test, or a test alone.

---

**Phase 1 complete.** Ready for Phase 2 — task implementation brief, written under Option A unless the owner
says otherwise, with clause (c) built regardless because it is buildable, valuable and unblocked.
