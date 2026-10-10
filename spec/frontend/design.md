# Design — Frontend (Checky Pro / Themistra PWA)

Scope tags: `[ALL]` platform-level, enduring across every phase. `[P1]`…`[P5]` phase-scoped. A phase may never re-decide an
`[ALL]` item. `agents.md` is authoritative for every rule restated here; this file adds the decisions and the verbatim blocks.

## 4a. LOCKED decisions — implement exactly, do NOT deviate

- L1. **[ALL] Public OIDC client with PKCE.** Authorization code with PKCE; no client secret in the bundle; `state` and `nonce`
  validated; exact-match redirect URIs; allowlisted post-login return target, falling back to the home route. (agents.md;
  backend R14; `RegisteredClientSeeder` registers `checky-spa` with `ClientAuthenticationMethod.NONE`)
- L2. **[ALL] Interactive login is SAS-hosted.** Password, TOTP, and recovery-code steps run in the Spring Authorization Server
  flow. The SAS MFA step is built (auth T20, `TotpAuthenticationProvider`). Confirmed by the author.
- L3. **[ALL] Tokens in memory only.** Access tokens are never written to storage, logs, or analytics. Renewal follows D-012 in
  `services/auth/docs/architecture/auth-decisions.md` as written: "rotating refresh token via the OIDC client, SAS httpOnly
  session cookie enables silent re-auth … refresh rotation + family reuse detection". Whether SAS issues that refresh token to
  the `checky-spa` client is open (Q5). No spec depends on refresh-token behaviour until Q5 closes.
- L4. **[ALL] Enumeration-safe UI, without carve-out.** Copy, redirects, styling, and timing are identical across unknown or
  missing, locked, suspended, and deleted accounts and across bad, expired, and used tokens. No lockout timers. An admin
  status view would reveal account state inside an authenticated screen. Showing it requires an ADR (Q14), and R27's status
  view is blocked until one exists.
- L5. **[ALL] MFA-first for MERCHANT and ADMIN, bootstrap undecided.** SAS refuses authorization to MERCHANT or ADMIN accounts
  without a confirmed enrollment, with the same error as a wrong password (backend T20). It does not enroll them. The SPA's
  wizard covers voluntary enrollment and management only, and the self-service endpoints (auth T19) are not built. How an
  unenrolled privileged account gets enrolled is open (Q13). The options are: enroll while USER before MERCHANT is granted; a
  pre-auth enrollment step in SAS (new backend work); or admin-assisted enrollment. The decision is not made here.
- L6. **[ALL] Generated client only.** Every backend call uses `libs/ts/api-client`, generated in CI from `contracts/api/*.yaml`.
  The single exception is the notification stream (R59, O7), which is a named exception and never carries a token in a URL.
- L7. **[ALL] Money as decimal strings and base units.** No JS `Number` on monetary values. Base units are scaled by token
  decimals with string or bigint shifting before display (R37).
- L8. **[ALL] Truthful state machine.** States and reorg reversals are shown as they occur. `HELD` may be entered from any state,
  including on a pre-attestation compliance block with no receipt (R56). A verifiable receipt appears only from `ATTESTED`.
- L9. **[ALL] Single origin.** No per-service URLs. All traffic routes through the edge path map, including the SAS endpoints on
  the same origin.
- L10. **[ALL] Secrets stay out of the bundle.** Only the public configuration keys listed in §4c are baked in (R58).
- L11. **[ALL] Accessibility and i18n on every screen.** WCAG AA, keyboard operation, managed focus across redirect and MFA steps,
  live regions for async results, catalogue-only strings.
- L12. **[ALL] Phase additivity.** Phases 2 to 5 add routes and components that consume existing events and endpoints. They do not
  replace the Phase 1 shell, routing, or auth model.
- L13. **[ALL] Confirmed stack.** Vite; React and TypeScript; React Router; TanStack Query; Zustand; `vite-plugin-pwa` (Workbox);
  Vitest; Playwright. Confirmed by the author.
- L14. **[ALL] SPA route set under `/app`.** The route paths are listed verbatim in §4c. Public: sign-in start, callback,
  registration, its acknowledgement, verify-from-link, resend verification, password-reset request and its acknowledgement,
  password reset from token, and the signed-out landing. Everything else requires a session. The `/app` prefix is disjoint from
  every edge-mapped backend prefix. A new public route, or capability links (R54), requires an ADR.
- L15. **[P1] Auth gate first.** Payment surfaces are reachable only after the auth gate is complete. Confirmed by the author.
- L16. **[ALL] The only sessionStorage carve-out.** sessionStorage may hold, and only hold: (a) `state`, `nonce`, `code_verifier`,
  and the post-login return target, single-use and deleted at callback; (b) non-sensitive mid-task form input for R29, with a
  15-minute TTL. Nothing else is stored client-side, and no PII is stored in either case. This list is the entire carve-out.
- L17. **[ALL] Service worker boundaries.** The navigation paths listed in `agents.md` bypass the fallback and are never answered
  from cache. API routes use NetworkOnly (R57).
- L18. **[ALL] Single-flight renewal and cross-tab coherence.** At most one renewal runs per tab. Tabs share renewal and sign-out
  through BroadcastChannel or a Web Locks leader (R21, R22).

## 4b. OPEN decisions — need sign-off before the owning phase moves to READY FOR IMPL

- O1. **Refresh-token issuance to the public client.** Mechanism closed by D-012 (silent re-auth through the SAS session); whether
  SAS issues rotating refresh tokens to `checky-spa` is open (Q5). Options if required: a custom token generator plus an ADR.
- O2. **Design system.** Not chosen (Q7). Must meet L11.
- O3. **i18n approach.** Not chosen (Q8). Locale fallback rule to confirm.
- O4. **Analytics.** Default none at launch (Q9). Any vendor needs a privacy review against L3 and agents.md.
- O5. **Privileged bootstrap path.** Open, see L5 and Q13.
- O6. **[P2]–[P5] rendering.** Not chosen. The choice must not force a route-architecture change (L12).
- O7. **Notification stream authentication.** ~~A browser `EventSource` cannot set an `Authorization` header. Options: (a) a
  fetch-based stream reader with an `Authorization` header, as a named exception to L6; (b) a short-lived stream ticket issued by
  the backend. The token must never appear in a URL. The transport is decided by the notification service (O3, Q3). Blocked on
  Q3.~~ **Resolved (2026-10-08): option (a).** The transport is confirmed SSE, already built
  (`services/notification` `InappStreamController`, `GET /notifications/stream`) — no backend ticket endpoint exists, and building
  one would be new backend work disproportionate to this already-closed question. The real, already-shipped mechanism is a
  standard `Authorization: Bearer <access_token>` header validated by the resource-server chain, identical to every other
  backend call — so the SPA opens this stream with a fetch-based reader (not the native `EventSource`, which cannot set
  headers), carrying the same in-memory access token the generated client already attaches, and reopens the stream after a
  token renewal exactly as R59 requires. No ticket endpoint, no token in the URL. See
  `contracts/api/notifications.yaml` for the documented contract.

## 4c. VERBATIM — routes, contracts, and fixed identifiers

**Auth REST paths** — verbatim from `contracts/api/auth.yaml`:

```
  /accounts:
  /accounts/me:
  /accounts/verify-email:
  /accounts/resend-verification:
  /accounts/password-reset-request:
  /accounts/password-reset:
  /accounts/me/password:
  /accounts/me/sessions:
  /accounts/me/sessions/{familyId}:
  /admin/accounts/{accountUuid}:
  /admin/accounts/{accountUuid}/activate:
  /admin/accounts/{accountUuid}/suspend:
  /admin/accounts/{accountUuid}/reinstate:
  /admin/accounts/{accountUuid}/unlock:
  /api-keys:
  /api-keys/{keyUuid}:
  /api-keys/token:
  /admin/accounts/{accountUuid}/roles:
  /admin/accounts/{accountUuid}/roles/{roleName}:
  /admin/accounts/{accountUuid}/role-templates/{templateName}:
  /admin/roles:
  /admin/role-templates:
  /admin/audit:
```

**Not present in `auth.yaml` (do NOT invent; each blocks what it names):**
- `accounts/me/mfa/totp` (enroll), `accounts/me/mfa/totp/confirm`, `DELETE accounts/me/mfa/totp`, `accounts/me/mfa/recovery-codes`.
  Specified in backend R22, R23, R28 and auth T19. T19 is not built. Blocked on Q1.
- A 429 response and a `Retry-After` header. Not documented. Blocked on Q12.
- A key-prefix field on `ApiKeyMetadata`, and an MFA-not-confirmed problem type. Not documented. Blocked on Q12.

**OIDC and SAS endpoints** (standard Spring Authorization Server, owned by the auth service, not in `auth.yaml`):
`/.well-known/openid-configuration`, `/oauth2/authorize`, `/oauth2/token` (PKCE), `/oauth2/revoke`, `/userinfo`, `/connect/logout`
(end-session, SAS default), `/login`, `/logout`, `/error`.

**SPA route set** — PROPOSED paths, pending author confirmation (Q6). Every path sits under `/app`:
- Public: `/app/sign-in`, `/app/callback`, `/app/register`, `/app/register/sent`, `/app/verify`, `/app/resend-verification`,
  `/app/reset/request`, `/app/reset`, `/app/signed-out`.
- Authenticated: `/app/home`, `/app/account`, `/app/account/sessions`, `/app/account/mfa`, `/app/api-keys`, `/app/admin`,
  `/app/invoices`, `/app/payments`, `/app/notifications`; and in later phases `/app/evidence`, `/app/disputes`, `/app/reputation`,
  `/app/fraud`.

**Requested scopes**: `openid profile email`.

**Access-token claims** — `contracts/api/token-claims.md`. The SPA depends on no claim outside that table for a given issuance path.

**Environment keys — PROPOSED, pending sign-off (Q6)**: `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID`, `VITE_OIDC_REDIRECT_URI`,
`VITE_API_BASE_PATH`. Public, baked at build time, never a secret.

**Application name on identity screens**: "Checky Pro" (confirmed by the author).

## 5. Persisted and on-device state changes

- **Tokens**: in memory only (L3). Cleared on sign-out and tab close.
- **sessionStorage**: only the L16 items. Nothing else.
- **localStorage**: public preferences (theme, locale) only.
- **Service worker cache**: static shell and assets only (L17). No API responses.
- **Server state**: query cache, invalidated on mutation and on session change.

## 6. Package and file map (planned, `frontend/`)

```
frontend/
├── src/
│   ├── app/                  shell, providers, router under /app   (L13, L14)
│   ├── auth/                 OIDC client, session owner, guards    (L1–L3, L16, L18)
│   ├── api/                  generated client wrapper, 401/429     (L6, L18)
│   ├── features/
│   │   ├── account/          register, verify, reset, sessions     [P1] (R1–R20, R29–R30)
│   │   ├── mfa/              voluntary enrollment, management      [P1] (R11–R13; Q1)
│   │   ├── api-keys/         create, list, revoke                  [P1] (R24–R26)
│   │   ├── admin/            role-matrix admin surfaces            [P1] (R27; Q14)
│   │   ├── invoices/         invoices, detail, address warnings    [P1] (R31, R55)
│   │   ├── payments/         state machine, receipts, history      [P1] (R32–R34, R36–R37, R56)
│   │   ├── wallets/          monitoring setup, unknown token       [P1] (R38–R39; Q2)
│   │   ├── notifications/    stream, reconnect                     [P1] (R35, R59; Q3, O7)
│   │   ├── evidence/         upload, analysis, graph, tx-hash      [P2] (R40–R43)
│   │   ├── disputes/         claims, narratives, timeline          [P3] (R44–R47)
│   │   ├── reputation/       wallet profile, passport, graph       [P4] (R48–R50)
│   │   ├── fraud/            alerts, cross-chain, API portal       [P5] (R51–R53)
│   │   └── links/            capability links                      (R54; Q11)
│   ├── i18n/                 catalogue                             (L11, O3)
│   └── pwa/                  service worker, denylist              (L17, R57)
├── e2e/                      Playwright                            (L13)
└── libs/ts/api-client/       generated, not hand-edited            (L6, R58 scan)
```
