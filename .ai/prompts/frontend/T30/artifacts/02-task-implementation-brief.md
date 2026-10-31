# frontend · T30 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

---

## Task

T30 — *Profile from userinfo.* Profile data is read from `/userinfo` and never from the access token. Verifies
`profileIsReadFromUserinfoAndAccountStatusFromMe`. Cites **R20**.

## Purpose

Give the SPA one sanctioned way to obtain profile data — a typed `GET /userinfo` through a generated client —
and prove that no module anywhere in the bundle takes PII or verification state out of the access token or the
`id_token` instead. The second half is the security clause; the first half is what makes obeying it possible.

**Owner decision taken 2026-10-31 (OQ-T30-1, Option A):** `/userinfo` is added to `contracts/api/oidc.yaml`
and the reader is built against the regenerated client. The endpoint returns only `{"sub"}` on the running
stack today, which is an `auth-service` gap recorded as `spec/auth-service/design.md` **O6**; the reader is
therefore correct and exercised, and the end-to-end profile assertion is pinned as pending O6 rather than
faked.

## Scope

**In**
- `GET /userinfo` documented in `contracts/api/oidc.yaml`, with its response schema.
- The regenerated `@themistra/api-client`.
- A profile reader that calls it through the generated OIDC client, behind the tab's session boundary.
- Proof that no production module reads PII or verification state from the access token or `id_token`.
- The named test file, created by T30.

**Out**
- `GET /accounts/me` and account status — **T31**, which extends the same test file.
- `email_verified` as verification UI — **T32**, `emailVerifiedClaimIsNotAccountState`.
- Any screen that displays a profile. No authenticated screen exists; `/app/account` is unbuilt.
- Fixing O6. Backend, `auth-service`, not this package.
- Correcting `contracts/api/token-claims.md`'s claim about where email and name live — part of O6.
- TanStack Query and Zustand wiring (L13 names them; nothing wires them yet).

## Business Rules

- **R20(a)** — profile data SHALL be read from `/userinfo`, with scopes `openid profile email`.
- **R20(b)** — account status SHALL be read from `GET /accounts/me`. **T31's.**
- **R20(c)** — the SPA SHALL NOT read PII or verification state from the access token.

## Locked Decisions

- **L6** — every backend call goes through a client generated from `contracts/api/*.yaml`; one carve-out, the
  notification stream (R59). Enforced by `lintRejectsHandWrittenBackendFetch`.
- **L3** — tokens live in memory only.
- **L9** — single origin; relative paths only, no base URL and no env key.
- **L11** — keyboard, focus, live region, WCAG AA on every screen. No screen here, so this binds only if one
  is added.
- **L16** — sessionStorage holds only the listed items. **A profile is PII and is never persisted
  client-side** (`agents.md` §Storage).
- **L18** — single-flight 401 renewal belongs to the client boundary, not to a caller.

## Dependencies

- `contracts/api/oidc.yaml` and its generator script `pnpm --filter @themistra/api-client generate`.
- `api/client.ts`'s `createAuthApi` pattern and `AUTH_PUBLIC_OPERATIONS`.
- `api/sessionBoundary.ts` — `middleware(publicOperations)`.
- `api/rateLimitNotice.ts` — registered first, because openapi-fetch runs `onResponse` in reverse order.
- `auth/accessToken.ts` — the in-memory holder the boundary reads.
- **`spec/auth-service/design.md` O6** — blocks the end-to-end value assertion only, not the task.

## Inputs

The tab's in-memory access token, supplied by the session boundary as a bearer. Nothing else; `/userinfo`
takes no parameters.

## Outputs

A typed profile value: `sub`, and the optional standard OIDC claims the contract declares. Returned to a
caller in memory. Not stored, not logged, not placed in a URL.

## State Changes

**None.** No storage write, no navigation, no persisted state. The reader is a read.

## Files to Create

| File | Why |
|---|---|
| `frontend/src/profile/readProfile.ts` | The reader: one call through the generated OIDC client behind the boundary |
| `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` | The named test. **T30 creates it; T31 extends it** |

## Files to Modify

| File | Change |
|---|---|
| `contracts/api/oidc.yaml` | Add `GET /userinfo` and a `UserInfoResponse` schema. Its description already states the test a path must pass — called programmatically — and that adding one is a decision; the decision is recorded above |
| `libs/ts/api-client/src/generated/oidc.ts` | **Regenerated, never hand-edited** |
| `frontend/src/api/client.ts` | An OIDC client factory that registers the rate-limit notice and the session boundary, in that order. `GET /userinfo` is **not** public, so unlike `POST /oauth2/token` it needs a bearer and needs renewal |

## Files NOT to Modify

`spec/**` (the O6 entry is already written and is the one authorized exception, now closed). `contracts/api/auth.yaml` — `/userinfo` is SAS's surface, and auth.yaml says so in its own description.
`services/**` — O6 is not T30's. Any existing test file other than the one created here. `api/sessionBoundary.ts`, `api/crossTabRenewal.ts`, `api/rateLimitNotice.ts` — T30 consumes the boundary, it does not change it.

## Acceptance Criteria

- **AC1** — `contracts/api/oidc.yaml` declares `GET /userinfo` as an authenticated operation (no `security: []`), with a response schema whose only required property is `sub`, every other claim optional. (R20a, L6)
- **AC2** — the generated client exposes it, and `generatedClientCompiles` still passes. The generated file is byte-identical to what the generator produces from the contract. (L6)
- **AC3** — `readProfile` reaches `/userinfo` **only** through the generated client: no `fetch`, no `XMLHttpRequest`, no string-built request. (L6, R20a)
- **AC4** — the call carries the in-memory access token as a bearer, supplied by the session boundary, and a 401 is handled by the boundary's single-flight renewal rather than by the reader. (L3, L18)
- **AC5** — **no production module reads PII or verification state from the access token or the `id_token`**: no decode, no `JSON.parse` of a JWT segment, no `atob`, and no read of `email`, `name`, `given_name`, `family_name`, `preferred_username`, `picture` or `email_verified` from a token. Proven over the whole comment-stripped tree against an allowlist with reasons. (**R20c** — the security clause)
- **AC6** — the profile is never persisted: absent from `sessionStorage`, `localStorage`, IndexedDB, `document.title`, `window.name`, the URL and every console channel, after a successful read. (L16, `agents.md` §Storage)
- **AC7** — the profile is never logged or sent to analytics. (`agents.md` §Storage)
- **AC8** — the reader surfaces a failed read without exposing problem detail or a stack trace, and without copy that names a cause. (RFC 9457 rule, L4)
- **AC9** — the response is parsed as "`sub` present, everything else optional". A response of exactly `{"sub":"…"}` is a **success**, not an error — this is what the live server returns today under O6.
- **AC10** — **every instrument is shown to report DIRTY first**: each absence scan fails on a synthetic violation, and a scan that reads nothing is refused rather than reported clean. (T22-D14)
- **AC11** — O6 is pinned, not papered over: one case asserts the reader's behaviour on a full profile fixture *and* records that the live endpoint returns only `sub`, so closing O6 makes a stated assertion start passing rather than silently changing meaning.

## Required Tests

Named test: `profileIsReadFromUserinfoAndAccountStatusFromMe` (T30 creates; T31 extends).

1. **AC1/AC2** — the contract declares the operation authenticated; the generated client exposes it; regeneration is reproducible.
2. **AC3/AC4** — the reader calls the generated client, the bearer arrives, and a 401 goes to the boundary. Control: a hand-written `fetch` to `/userinfo` is rejected by the L6 lint rule.
3. **AC5** — the token-PII scan over the comment-stripped tree, with a per-claim control and a per-decode-form control (`atob`, `JSON.parse`, a split on `.`).
4. **AC6/AC7** — the leak sweep over every surface and every console channel, with a sentinel per surface. Reuse T29's `encodings()` normalisation: a profile surviving in a URL is the likely shape, and a raw-to-raw comparison reads clean on it.
5. **AC8** — a failed read shows no detail, no stack, no cause.
6. **AC9/AC11** — a `{"sub"}`-only response succeeds; a full-profile fixture is read correctly; the O6 pin.
7. **AC10** — one dirtying violation per instrument, and the empty-tree refusal.

## Constraints

- **Security.** R20(c) is the point of the task. PII and verification state come from the server's own
  endpoints, never from a token the client holds — a token's claims are whatever the issuer put there for its
  own purposes, and `token-claims.md` documents `email_verified` as reflecting *scope authorization*, not
  whether an email is verified. A profile is PII: in memory only, never persisted, never logged, never in a URL.
- **Module boundaries.** The reader lives in `profile/` and depends on `api/` for transport. It does not reach
  into `auth/accessToken.ts` itself — the boundary owns the bearer. Screens do not reimplement the boundary.
- **Null handling.** Every profile claim except `sub` is optional and absent in practice today. The reader
  must not treat absence as failure (AC9), and must not substitute a value from a token (AC5).
- **Thread-safety / transactions.** Not applicable: single-threaded browser, no transaction.
- **Performance.** One request, no polling, no retry of its own — renewal is the boundary's.
- **Generated code.** `generated/oidc.ts` is never hand-edited; a difference between it and the generator's
  output is a defect.

## Open Questions

**No blockers.** OQ-T30-1 is decided (Option A, above). OQ-T30-2 is recorded as `spec/auth-service/design.md`
O6 and constrains only AC11's framing, not the task. OQ-T30-3 is answered by Scope: no screen is built, so the
reader has no consumer yet and T30 delivers a reader plus its proof.

---

**Phase 2 complete.** Ready for **Phase 3 — Kimi 2.7's design challenge.**
