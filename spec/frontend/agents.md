# agents.md — Frontend (Checky Pro / Themistra PWA)

Standing, durable rules for `frontend/`. Authoritative for this package. A feature spec never restates
these; it references this file and records only what is specific to the feature. A spec that must
override a rule here says so explicitly in its §4a (LOCKED), and no spec may override a rule tagged
`[ALL]` without a new ADR.

## Platform rules (identical across every phase)

**Scope and stack**
- React + TypeScript, mobile-first PWA, served from one origin behind the edge. Confirmed stack:
  Vite, React Router (routing), TanStack Query (server state), Zustand (client state),
  `vite-plugin-pwa` (Workbox service worker), Vitest (unit), Playwright (end-to-end).
- No native code. No React Native dependencies unless an ADR adds a native shell.

**Identity and session (LOCKED, `[ALL]`)**
- The SPA is a **public OIDC client** of the auth service (Spring Authorization Server). Authorization
  Code with PKCE. No client secret in the bundle. `state` is validated on every callback. Redirect URIs
  are exact-match whitelisted. The post-login return target is validated against an allowlist, never
  taken as an arbitrary URL, to prevent open redirects.
- Interactive login (password step, TOTP/recovery-code step) happens in the SAS-hosted flow. The SPA does
  not render a proprietary password form. Confirmed by the author.
- Access tokens last 10 minutes and live in memory only. Renewal follows the pure public-client model selected
  in `services/auth/docs/architecture/auth-decisions.md` D-012: silent re-authorization through the SAS httpOnly
  session cookie. Whether SAS also issues a rotating refresh token to a public client is unconfirmed (`package.md`
  Q5); until Q5 closes, no spec may depend on refresh-token families. If a renewal fails, or a session is reused,
  the SPA transitions to a clean re-auth and never leaves a half-authenticated session.
- Signing out ends both the SAS session (OIDC RP-initiated logout with an allowlisted `post_logout_redirect_uri`) and
  the in-memory token state. Revocation through `/oauth2/revoke` applies to any token the SPA still holds.
- The OAuth scopes requested are `openid profile email`. Account status is read from `GET /accounts/me`. The
  access-token `email_verified` claim is never treated as account verification state.
- Access tokens carry exactly the claims in `contracts/api/token-claims.md` and no PII. Profile data comes
  from `userinfo`, never from the token body.
- **Tokens are never written to `localStorage`, `sessionStorage`, logs, or analytics.** The storage
  mechanism is an open decision (`design.md` O1) and must not default to `localStorage`.

**Enumeration safety in the UI (LOCKED, `[ALL]`)**
- Copy, redirect targets, visual error styling, and response timing are indistinguishable for unknown,
  locked, suspended, deleted, and missing accounts, and for bad, expired, and already-used tokens. This
  mirrors `spec/auth-service/agents.md`, where enumeration safety is a hard rule.
- No client-side account-state gating. No wait-time lockout timer, because it reveals state. Confirmed by
  the author: uniform copy only, no exceptions.

**MFA-first for privileged accounts (LOCKED, `[ALL]`)**
- A MERCHANT or ADMIN account completes TOTP enrollment before any authorization code is issued. First-login forced
  enrollment runs inside the SAS-hosted flow (backend T19/T20), because the SPA holds no bearer token before a code is
  issued. The SPA's own enrollment wizard covers only voluntary enrollment and management by an already-authenticated
  user. Recovery codes are shown exactly once.
- The enrollment and management endpoints are not yet in `contracts/api/auth.yaml`. Screens that depend on
  them stay DRAFT behind a `Q#` blocker. No endpoint is invented.

**API access (LOCKED, `[ALL]`)**
- Every backend call goes through the generated TypeScript client `libs/ts/api-client`, generated in CI from
  `contracts/api/*.yaml`. No hand-written `fetch` to backend routes. No hand-rolled schema.
- The client boundary owns 401 interception through single-flight renewal: at most one renewal runs per tab at a time,
  and cross-tab coordination ensures parallel 401s and sibling tabs never present a superseded token. On failure it
  routes to clean re-auth, never to a loop. It also owns network failure states. 429 handling is fixed, generic copy
  with no countdown and no automatic replay of non-idempotent POSTs. Individual screens do not reimplement these.
- Service worker navigations to identity and API paths (`/oauth2/`, `/login`, `/.well-known/`, `/userinfo`, `/accounts`,
  `/api-keys`, `/admin`, `/api/`) are never answered from cache and bypass the navigation fallback.
- Errors are RFC 9457 `application/problem+json`. The UI maps problem types to copy and never displays
  stack traces or internal detail.

**Money and state (LOCKED, `[ALL]`)**
- Monetary amounts cross the wire as decimal strings and are never parsed to JS `Number`. Payment amounts are token
  base units (for example `NUMERIC(78,0)` plus `token_decimals`). Every display scales the base unit by its token's
  decimals using string or bigint decimal shifting. Rendering the raw base unit as a human amount is a defect.
- The payment verification state machine is shown truthfully: `CREATED → WATCHING → SEEN → CONFIRMING →
  FINALIZED → ATTESTED`, with reorg reversals (`CONFIRMING → SEEN`, `SEEN → WATCHING`) and `HELD`, which may be entered
  from `ATTESTED` on a post-attestation reorg. A verifiable receipt is displayed only from `ATTESTED`, because the
  signed receipt is created at attestation (payment-service R24). `FINALIZED` is shown as "finalized, receipt pending".
  `HELD` is never labelled "pending".

**Storage, logging, privacy (LOCKED, `[ALL]`)**
- Never log or persist to analytics: tokens, PINs, recovery codes, API keys, passwords, or PII.
- Secrets and environment values never enter the bundle. Only public configuration is baked in.
- Sensitive response bodies (account, token, receipt, evidence) are never cached by the service worker.

**Accessibility and i18n (LOCKED, `[ALL]`)**
- Every screen, in every phase, is keyboard-operable, has managed focus across redirect and MFA steps,
  announces async results through a live region, and meets WCAG AA contrast.
- Every user-facing string is in the catalogue. No hardcoded copy in components.

**Phase discipline (LOCKED, `[ALL]`)**
- Later phases are consumers of earlier ones, never rewrites. Phase 2 to 5 surfaces re-use the Phase 1 shell,
  routing, and auth model. A later phase may add routes and components, but may not change platform rules.
- The Phase 1 auth and account gate ships first. Nothing outside the public routes is reachable without a
  valid session.

**Process**
- Trunk-based. `main` stays deployable. Material design changes require an ADR in `docs/adr/`.
- Frontend specs are authored with the `spec-authoring` skill; this package is the first to use
  `references/frontend.md`.
