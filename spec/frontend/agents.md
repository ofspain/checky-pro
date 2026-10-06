# agents.md — Frontend (Checky Pro / Themistra PWA)

Standing, durable rules for `frontend/`. Authoritative for this package. A feature spec never restates these; it references
this file and records only what is specific to the feature. No spec may override a rule tagged `[ALL]` without a new ADR in
`docs/adr/`.

## Platform rules (identical across every phase)

**Scope and stack**
- React + TypeScript, mobile-first PWA, served from one origin behind the edge. Confirmed stack: Vite, React Router, TanStack
  Query, Zustand, `vite-plugin-pwa` (Workbox), Vitest, Playwright.
- No native code. No React Native dependencies unless an ADR adds a native shell.

**SPA routes (LOCKED, `[ALL]`)**
- All SPA routes live under the `/app` prefix, which is disjoint from every edge-mapped backend prefix. A backend path is never
  a SPA route. The exact public and authenticated route paths are listed in `design.md` §4c.

**Identity and session (LOCKED, `[ALL]`)**
- The SPA is a **public OIDC client** of the auth service (Spring Authorization Server). Authorization code with PKCE. No
  client secret in the bundle. `state` and `nonce` are validated on every callback. Redirect URIs are exact-match whitelisted.
  The post-login return target is checked against an allowlist. If it is outside the allowlist, the SPA falls back to the
  home route, never to an arbitrary URL.
- Interactive login (password, TOTP or recovery-code step) happens in the SAS-hosted flow. The SAS MFA step is built (auth
  T20). The SPA renders no password form. Confirmed by the author.
- Access tokens live in memory only. The renewal mechanism is the one selected in
  `services/auth/docs/architecture/auth-decisions.md` D-012: a PKCE public client whose renewal uses a rotating refresh
  token via the OIDC client, with the SAS httpOnly session cookie enabling silent re-authentication. Whether SAS issues
  that refresh token to the `checky-spa` client, which is registered with `ClientAuthenticationMethod.NONE`, is open (Q5).
  No spec may depend on refresh-token behaviour until Q5 closes. If renewal fails, or a session is found invalid, the SPA
  transitions to a clean re-auth and never leaves a half-authenticated session.
- Sign-out ends the SAS session through the OIDC end-session endpoint with an allowlisted `post_logout_redirect_uri`, and
  clears in-memory token state. Sign-out propagates to every open tab.
- The requested scopes are `openid profile email`. Account status is read from `GET /accounts/me`. The access-token
  `email_verified` claim is never treated as account verification state.

**Enumeration safety in the UI (LOCKED, `[ALL]`)**
- Copy, redirect targets, visual error styling, and response timing are identical for unknown, missing, locked, suspended,
  and deleted accounts, and for bad, expired, and already-used tokens. This mirrors `spec/auth-service/agents.md`.
- No client-side account-state gating. No lockout timers. Uniform copy only. Confirmed by the author.
- The SPA cannot control server response timing. The timing requirement applies to the SPA's own rendering only.

**MFA (LOCKED, `[ALL]`)**
- A MERCHANT or ADMIN account must hold a confirmed TOTP enrollment before the SAS flow issues an authorization code. The
  SAS flow refuses such accounts without enrollment and returns the same error as a wrong password (backend T20). It does
  not enroll them.
- The SPA's own enrollment wizard covers voluntary enrollment and management by an already-authenticated user. The
  self-service enrollment endpoints (auth T19) are not built.
- How an unenrolled privileged account gets enrolled is an open decision (Q13). No path is assumed.
- Recovery codes are shown exactly once.

**API access (LOCKED, `[ALL]`)**
- Every backend call goes through the generated TypeScript client `libs/ts/api-client`, generated in CI from
  `contracts/api/*.yaml`. No hand-written `fetch` to backend routes. The only exception is named in `design.md` L6 (the
  notification stream, R59).
- The client boundary owns single-flight 401 renewal, fixed generic 429 handling, and network failure states. Screens do not
  reimplement them. 429 handling shows fixed copy with no countdown and no automatic replay of non-idempotent POST requests.
- Errors are RFC 9457 `application/problem+json`. The UI maps problem types to copy and never shows stack traces or internal
  detail.

**Money and state (LOCKED, `[ALL]`)**
- Monetary amounts cross the wire as decimal strings and are never parsed to JS `Number`. Payment amounts are token base units.
  Every display scales the base unit by its token's decimals using string or bigint shifting. Showing the raw base unit as a
  human amount is a defect.
- The payment state machine is shown truthfully: `CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED → ATTESTED`, with reorg
  reversals (`CONFIRMING → SEEN`, `SEEN → WATCHING`). `HELD` may be entered from any state, including on a compliance block
  before attestation. A verifiable receipt appears only from `ATTESTED`. `FINALIZED` is shown as "finalized, receipt pending".
  `HELD` is never labelled "pending".

**Storage, logging, privacy (LOCKED, `[ALL]`)**
- Tokens are never written to `localStorage`, logs, or analytics, and are never placed in a URL.
- The only sanctioned client-side storage for sensitive data is sessionStorage, and only for the PKCE and resume state listed
  in `design.md` L16. Nothing else is persisted client-side. No PII is ever persisted client-side.
- Never log or send to analytics: tokens, PINs, recovery codes, API keys, passwords, or PII.
- Secrets and environment values never enter the bundle. Only public configuration is baked in.
- The service worker never answers an identity or API navigation from cache (see the service-worker rule below). Sensitive
  response bodies (account, token, receipt, evidence) are never cached.

**Service worker (LOCKED, `[ALL]`)**
- Navigation requests to these paths are never answered from cache and never receive the application shell: `/oauth2/`,
  `/connect/logout`, `/login`, `/logout`, `/error`, `/.well-known/`, `/userinfo`, `/accounts`, `/api-keys`, `/admin`, `/api/`.
- API routes use NetworkOnly.
- The worker uses one worker-wide update policy: prompt-to-reload. `skipWaiting` and `clients.claim` are not used. Rollback
  is handled as stated in `package.md` §10.

**Accessibility and i18n (LOCKED, `[ALL]`)**
- Every screen, in every phase, is keyboard-operable, manages focus across redirect and MFA steps, announces async results
  through a live region, and meets WCAG AA contrast.
- Every user-facing string is in the catalogue. No hardcoded copy in components.

**Phase discipline (LOCKED, `[ALL]`)**
- Later phases consume earlier ones. They never rewrite them. Phases 2 to 5 re-use the Phase 1 shell, routing, and auth model.
- Nothing outside the public routes is reachable without a valid session.

**Process**
- Trunk-based. `main` stays deployable. Material design changes require an ADR in `docs/adr/`.
- Frontend specs are authored with the `spec-authoring` skill and `references/frontend.md`.
