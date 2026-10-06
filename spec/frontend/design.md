# Design — Frontend (Checky Pro / Themistra PWA)

Scope tags: `[ALL]` platform-level, enduring across every phase. `[P1]`…`[P5]` phase-scoped. A phase may never
re-decide an `[ALL]` item.

## 4a. LOCKED decisions — implement exactly, do NOT deviate

- L1. **[ALL] Public OIDC client with PKCE.** Authorization code with PKCE, no client secret in the bundle, `state` and
  `nonce` validated, exact-match redirect URIs, and an allowlisted post-login return target that falls back to the home
  route when the target is outside the allowlist. (agents.md identity rules; backend R14; `RegisteredClientSeeder`
  registers `checky-spa` with `ClientAuthenticationMethod.NONE`)
- L2. **[ALL] Interactive login is SAS-hosted.** Password, TOTP, and recovery-code steps run in the Spring Authorization
  Server flow. Confirmed by the author.
- L3. **[ALL] Tokens never touch `localStorage`, logs, or analytics.** Access tokens live in memory only. The session
  renewal mechanism is closed by D-012 in `services/auth/docs/architecture/auth-decisions.md`: silent re-authorization
  through the SAS httpOnly session cookie. Whether SAS issues a rotating refresh token to this public client is open
  (Q5); no spec may depend on refresh-token families until Q5 closes.
- L4. **[ALL] Enumeration-safe UI.** Copy, redirects, styling, and timing are identical across unknown or missing,
  locked, suspended, and deleted accounts, and across bad, expired, and already-used tokens. No lockout timers. Confirmed
  by the author. The privileged admin views of R27 carry a named carve-out, since an administrator must see status.
- L5. **[ALL] MFA-first for MERCHANT and ADMIN.** Enrollment completes before any authorization code is issued. First-login
  enrollment runs inside the SAS-hosted flow. The SPA wizard covers only voluntary enrollment and management by an
  authenticated user (closes former O5). Recovery codes are shown exactly once.
- L6. **[ALL] Generated client only.** Every backend call uses `libs/ts/api-client`, generated in CI from
  `contracts/api/*.yaml`. The client boundary owns single-flight 401 renewal, fixed 429 handling, and network states.
- L7. **[ALL] Money as decimal strings and base units.** No JS `Number` arithmetic on monetary values. Token base units are
  scaled by token decimals with string or bigint shifting before display (R37).
- L8. **[ALL] Truthful state machine.** Payment states and reorg reversals are shown as they occur, including `HELD` after
  attestation. A verifiable receipt appears only from `ATTESTED`.
- L9. **[ALL] Single origin.** No per-service URLs. All traffic routes through the edge path map, including the SAS-hosted
  endpoints on the same origin.
- L10. **[ALL] Secrets stay out of the bundle.** Only public configuration keys are baked in. Environment keys are listed by
  name in §4c, never by value.
- L11. **[ALL] Accessibility and i18n on every screen.** WCAG AA, keyboard operation, managed focus across redirect and MFA
  steps, live regions for async results, and catalogue-only strings.
- L12. **[ALL] Phase additivity.** Phases 2 to 5 add routes and components that consume existing events and endpoints.
  They do not replace the Phase 1 shell, routing, or auth model.
- L13. **[ALL] Confirmed stack.** Vite; React and TypeScript; React Router; TanStack Query for server state; Zustand for client
  state; `vite-plugin-pwa` (Workbox) for the service worker; Vitest for unit tests; Playwright for end-to-end tests.
  Confirmed by the author.
- L14. **[ALL] Public route set.** The public routes are exactly: sign-in start; OIDC callback; verify-from-link; resend
  verification; registration and its acknowledgement; password-reset request and its acknowledgement; password-reset from
  token; and the sign-out landing. Every other route redirects to sign-in without a session. A named test asserts this exact
  set. A new public route requires an ADR. Capability links (R54) are the only permitted extension, under their own rules.
- L15. **[P1] Auth and account gatekeeper ships first.** Payment surfaces are reachable only after the auth gate is complete.
  Confirmed by the author.
- L16. **[ALL] Carve-out for PKCE and resume state.** `state`, `nonce`, `code_verifier`, and the post-login return target live
  in `sessionStorage`, are single-use, and are deleted at the callback. This carve-out to L3 applies to nothing else. The
  mid-task input of R29 also lives in sessionStorage, with a 15-minute TTL.
- L17. **[ALL] Service worker boundaries.** Navigations to the identity and API paths listed in `agents.md` bypass the navigation
  fallback and are never answered from cache. API routes use NetworkOnly.
- L18. **[ALL] Single-flight renewal and cross-tab coherence.** At most one renewal runs per tab. Tabs coordinate through a
  BroadcastChannel or a Web Locks leader so that renewal and sign-out propagate across tabs.

## 4b. OPEN decisions — need sign-off before the owning phase moves to READY FOR IMPL

- O1. **Refresh-token issuance to the public client (closed by D-012 for the mechanism; open for refresh tokens).** The
  renewal mechanism is closed: silent re-authorization through the SAS session (L3). Whether SAS also issues rotating refresh
  tokens to `checky-spa` is unconfirmed (Q5). Options if refresh tokens are required: a custom token generator plus an ADR.
- O2. **Design system.** Not chosen. Options: a component library with an in-house theme, or a ready-made kit. Must meet L11.
- O3. **i18n approach.** Not chosen. Options: a standard catalogue library, or a lighter custom one. Locale fallback rule to be
  confirmed.
- O4. **Analytics.** Default none at launch. Any vendor needs its own privacy review against L3 and `agents.md`.
- O5. **Closed.** Forced enrollment placement is locked by L5.
- O6. **[P2]–[P5] evidence graph and dispute workspace rendering.** Not chosen. The choice must not force a route-architecture
  change (L12).

## 4c. VERBATIM — contracts and fixed identifiers

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

**Not present in `auth.yaml` (do NOT invent; each blocks the screens named):**
- MFA enroll (`accounts/me/mfa/totp`), confirm (`accounts/me/mfa/totp/confirm`), disable (`DELETE accounts/me/mfa/totp`), and
  recovery-code regeneration (`accounts/me/mfa/recovery-codes`). Specified in backend R22, R23, R28 and auth T19. Blocked on Q1.
- The SAS MFA step (auth T20) is part of the SAS protocol, not `auth.yaml`. Blocked on Q1.
- A 429 response and a `Retry-After` header. Not documented. Blocked on Q12.
- A key prefix field on `ApiKeyMetadata`. Not present. Blocked on Q12.

**OIDC endpoints** — standard Spring Authorization Server, owned by the auth service and not in `auth.yaml`:
`/.well-known/openid-configuration`, `/oauth2/authorize`, `/oauth2/token` (PKCE), `/oauth2/revoke`, `/userinfo`, the OIDC
end-session endpoint, and the SAS-hosted `/login`.

**Requested scopes**: `openid profile email`.

**Access-token claims** — `contracts/api/token-claims.md`. The SPA depends on no claim outside that table for a given issuance
path. Profile data comes from `/userinfo`; account status comes from `GET /accounts/me`.

**Environment keys — PROPOSED, not verbatim, pending sign-off (Q6)**: `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID`,
`VITE_OIDC_REDIRECT_URI`, `VITE_API_BASE_PATH`. Public and baked at build time. No secret key may appear here.

**Application name on identity screens**: "Checky Pro" (confirmed by the author).

## 5. Persisted and on-device state changes

- **Tokens**: in memory only (L3). Cleared on sign-out and tab close.
- **PKCE and resume state**: sessionStorage, single-use, deleted at callback (L16).
- **Mid-task input (R29)**: sessionStorage, 15-minute TTL (L16). Never tokens, passwords, recovery codes, or API keys.
- **Public preferences** (theme, locale): localStorage, permitted because they carry no secret.
- **Service worker cache**: static shell and assets only. No API responses. Identity and API navigations excluded (L17).
- **Server state**: query cache, invalidated on mutation and on session change.

## 6. Package and file map (planned, `frontend/`)

```
frontend/
├── src/
│   ├── app/                  shell, providers, router              (L13, L14)
│   ├── auth/                 OIDC client, session owner, guards    (L1–L3, L16, L18)
│   ├── api/                  generated client wrapper, 401/429     (L6, L18)
│   ├── features/
│   │   ├── account/          register, verify, reset, sessions     [P1] (R1–R20, R30)
│   │   ├── mfa/              voluntary enrollment, management      [P1] (R11–R13; Q1)
│   │   ├── api-keys/         create, list, revoke                  [P1] (R24–R26)
│   │   ├── admin/            role-scoped admin surfaces            [P1] (R27)
│   │   ├── invoices/         invoices, detail                      [P1] (R31)
│   │   ├── payments/         state machine, receipts, history      [P1] (R32–R34, R36–R37)
│   │   ├── wallets/          monitoring setup, unknown-token state [P1] (R38–R39; Q2)
│   │   ├── notifications/    SSE list, reconnect                   [P1] (R35)
│   │   ├── evidence/         upload, analysis, graph, tx-hash      [P2] (R40–R43)
│   │   ├── disputes/         claims, narratives, timeline          [P3] (R44–R47)
│   │   ├── reputation/       wallet profile, trust passport, graph [P4] (R48–R50)
│   │   ├── fraud/            alerts, cross-chain, API portal       [P5] (R51–R53)
│   │   └── links/            capability links                      (R54; Q11)
│   ├── i18n/                 catalogue                             (L11, O3)
│   └── pwa/                  service worker config                 (L17)
├── e2e/                      Playwright                            (L13)
└── libs/ts/api-client/       generated, not hand-edited            (L6)
```
