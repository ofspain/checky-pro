# Design — Frontend (Checky Pro / Themistra PWA)

Scope tags: `[ALL]` platform-level, enduring across every phase. `[P1]`…`[P5]` phase-scoped. A phase
may never re-decide an `[ALL]` item.

## 4a. LOCKED decisions — implement exactly, do NOT deviate

- L1. **[ALL] Public OIDC client with PKCE.** The SPA authenticates as a public client of the auth service:
  authorization code with PKCE, no client secret in the bundle, `state` validated, exact-match redirect URIs,
  allowlisted post-login return targets. (agents.md identity rules; backend R14)
- L2. **[ALL] Interactive login is SAS-hosted.** Password and TOTP/recovery-code steps run in the
  Spring Authorization Server flow. Confirmed by the author (no branded SPA login handler at launch).
- L3. **[ALL] Tokens never touch `localStorage`, `sessionStorage`, logs, or analytics.** The mechanism is O1.
- L4. **[ALL] Enumeration-safe UI.** Copy, redirects, styling, and timing are identical across unknown, locked,
  suspended, deleted, and missing accounts and across bad, expired, and used tokens. No lockout timers.
  Confirmed by the author.
- L5. **[ALL] MFA-first for MERCHANT and ADMIN.** Enrollment completes before any authorization code. Recovery codes
  are shown exactly once. The enroll and disable contract is missing (Q1), so those screens stay DRAFT.
- L6. **[ALL] Generated client only.** Every backend call uses `libs/ts/api-client`, generated in CI from
  `contracts/api/*.yaml`. The client boundary owns 401 refresh-once, 429 backoff, and network states.
- L7. **[ALL] Money as decimal strings.** No JS `Number` arithmetic on monetary values.
- L8. **[ALL] Truthful state machine.** Payment states and reorg reversals are shown as they occur. A receipt
  appears only from `FINALIZED` or `ATTESTED`.
- L9. **[ALL] Single origin.** No per-service URLs. All traffic routes through the edge path map.
- L10. **[ALL] Secrets stay out of the bundle.** Only public configuration keys are baked in. Environment keys are
  listed by name in §4c, never by value.
- L11. **[ALL] Accessibility and i18n on every screen.** WCAG AA, keyboard operation, managed focus, live regions,
  and catalogue-only strings.
- L12. **[ALL] Phase additivity.** Phases 2 to 5 add routes and components and consume existing events and endpoints.
  They do not replace the Phase 1 shell, routing, or auth model.
- L13. **[ALL] Confirmed stack.** Vite; React and TypeScript; React Router for routing; TanStack Query for server
  state; Zustand for client state; `vite-plugin-pwa` (Workbox) for the service worker; Vitest for unit tests;
  Playwright for end-to-end tests. Confirmed by the author.
- L14. **[ALL] Auth-gated routing.** Public routes are the sign-in start, the OIDC callback, verify-from-link, and
  password-reset entry. Every other route redirects to sign-in when there is no valid session. The shell is
  rendered only after the session is established.
- L15. **[P1] Auth and account gatekeeper ships first.** The P1 payment surfaces are reachable only after the
  auth gate is complete. Confirmed by the author.

## 4b. OPEN decisions — need sign-off before the owning phase moves to READY FOR IMPL

- O1. **[ALL] Token storage mechanism (blocks the auth gate).** Options: (a) access token in memory with silent
  renewal through the SAS session, refresh held by the SAS-managed session cookie; (b) a backend-for-frontend holds
  the refresh token in an HttpOnly, SameSite cookie. Recommendation: (a), because it needs no new backend
  component. Must be confirmed, and the chosen mechanism must be verified against the SAS session behaviour before
  it is locked.
- O2. **[ALL] Design system.** Not chosen. Options: a component library (for example Radix primitives with an in-house
  theme) or a ready-made kit. Needs author choice. Any choice must meet L11.
- O3. **[ALL] i18n approach.** Not chosen. Options: a standard i18next-style catalogue, or a lighter custom one.
  Locale fallback rule to be confirmed.
- O4. **[ALL] Analytics.** Default is none at launch, to avoid PII risk. Any vendor needs its own review against L3
  and the privacy rule in `agents.md`.
- O5. **[P1] Forced MFA enrollment wizard placement.** Blocked on Q1: the enrollment endpoints must exist before the
  wizard's placement can be locked. Author chose a blocking, step-by-step wizard. Placement (inside the SAS step or
  as an SPA route after callback) is open until Q1 closes.
- O6. **[P2]–[P5] Evidence graph and dispute workspace rendering.** Not chosen. Candidate: a canvas or SVG graph library.
  The choice must not force a route-architecture change (L12).

## 4c. VERBATIM — contracts and fixed identifiers

**Auth REST paths** — verbatim from `contracts/api/auth.yaml`. These are the registration, verification, password,
session, API-key, and admin endpoints the SPA calls through the generated client:

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

**Not present in `auth.yaml` (do NOT invent; blocks the screens named):**
- `accounts/me/mfa/totp` (enroll), `accounts/me/mfa/totp/confirm`, `DELETE accounts/me/mfa/totp` (disable). Specified in
  backend R22, R23, R28. Blocked on Q1.

**OIDC endpoints** — standard Spring Authorization Server, owned by the auth service and not in `auth.yaml`:
`/.well-known/openid-configuration`, `/oauth2/authorize`, `/oauth2/token` (PKCE), `/oauth2/revoke`, `/userinfo`, and the
SAS-hosted `/login`. The contract for login and challenge is the SAS protocol plus auth task T20.

**Access-token claims** — `contracts/api/token-claims.md`. The SPA must not depend on any claim outside that table for a
given issuance path. Profile data comes from `/userinfo`.

**Environment keys — PROPOSED, not verbatim, pending author sign-off (Q6)**: `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID`,
`VITE_OIDC_REDIRECT_URI`, `VITE_API_BASE_PATH`. These names are a proposal by this package and are not yet sourced from any
existing config. Each would be public and baked at build time. No secret key may appear here.

**Application name on identity screens**: "Checky Pro" (confirmed by the author). Used in the OIDC client display name
and any SAS-branded copy.

## 5. Persisted and on-device state changes

- **Persisted to storage**: none for tokens (L3). Public preference only (theme, locale) in `localStorage`, which is
  permitted because it carries no secret.
- **In memory only**: the access token and session state, cleared on sign-out and tab close.
- **Service worker cache**: static shell and assets only. No cached API responses for account, token, receipt, or
  evidence data.
- **Server state**: query cache, invalidated on mutation and on session change.

## 6. Package and file map (planned, `frontend/`)

```
frontend/
├── src/
│   ├── app/                  shell, providers, router            (L13, L14)
│   ├── auth/                 OIDC client, session owner, guards  (L1–L3, O1)
│   ├── api/                  generated client wrapper, 401/429   (L6)
│   ├── features/
│   │   ├── account/          register, verify, reset, sessions   [P1] (R1–R30)
│   │   ├── mfa/              enrollment wizard, management       [P1] (R10–R12; Q1)
│   │   ├── api-keys/         create, list, revoke                [P1] (R24–R26)
│   │   ├── admin/            account admin surfaces              [P1] (R27)
│   │   ├── invoices/         invoices, detail                    [P1] (R31)
│   │   ├── payments/         state machine, receipts, history    [P1] (R32–R37)
│   │   ├── notifications/    SSE list, reconnect                 [P1] (R35)
│   │   ├── evidence/         upload, analysis, graph             [P2] (R38–R40)
│   │   ├── disputes/         claims, narratives, timeline        [P3] (R41–R42)
│   │   ├── reputation/       wallet profile, trust passport      [P4] (R43–R44)
│   │   └── fraud/            alerts, institutional API portal    [P5] (R45–R46)
│   ├── i18n/                 catalogue                           (L11, O3)
│   └── pwa/                  service worker config               (§5)
├── e2e/                      Playwright                          (L13)
└── libs/ts/api-client/       generated, not hand-edited          (L6)
```
