<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T31 · Phase 1 — Specification Extraction

## Business Rules

- **R41.** WHEN per-account request rates on login, `/oauth2/token`, password-reset confirmation,
  or MFA verification exceed configured thresholds, THEN the service SHALL reject with `429 Too
  Many Requests`.
- **R42.** WHERE the ingress edge provides IP-level rate limiting, THEN this service's own limits
  SHALL act as a per-account backstop, not the primary defense.

## Locked Decisions

**None.** Confirmed at Phase 0 and re-confirmed here — no `L`-numbered decision constrains this
task.

## Files involved

**Existing files to read/extend:**
- `token/SecurityChainsConfig.java` — both filter chains live here; `/login` is on chain 2
  (`applicationChain`), `/oauth2/token` is internal to chain 1's SAS configurer (no controller this
  service owns). Rate-limiting enforcement will very likely need to be a `Filter` registered
  against both chains, or a servlet-container-level filter outside either `SecurityFilterChain`
  bean, since the task spans routes this service doesn't uniformly control at the controller level.
- `account/AccountController.java` — owns `POST /accounts/password-reset` (the "password-reset
  confirmation" path), a controller method this service fully owns.
- `authn/LoginFailureHandler.java` — existing precedent for resolving "which account" from a raw
  `/login` POST (`request.getParameter("username")` → `AccountService.findLoginView(email)`).
- `authn/TotpAuthenticationProvider.java` — confirmed at Phase 0 to be where MFA (TOTP/recovery
  code) verification actually happens, inside the same `/login` POST — there is no separate
  MFA-verification route to instrument.
- `common/ProblemTypes.java` — a new URI constant for the 429 problem type is likely needed,
  matching every other rejection cause's existing pattern.

**New files the spec expects:** none named explicitly by `design.md`'s file tree — O2 frames this
task's own mechanics as an open decision for the implementer to propose, not a pre-specified class
list.

## Dependencies

- A rate-limiting library (Bucket4j) **or** a hand-rolled concurrent-map bucket implementation —
  O2's own stated preference is in-process, per-replica, not a shared/distributed store (the
  durable `lockout_state` table already covers the "must survive a restart" concern for the
  credential-guessing-specific case; R41's own buckets are a lighter, ephemeral backstop per R42's
  framing).
- `AccountService.findLoginView(String email)` (existing, `account` module) — for resolving an
  attempted account identity on `/login` before authentication completes.
- `Clock` (existing) — any bucket refill/window logic must be driven by the injectable clock, not
  wall-clock calls.
- `ProblemTypes` (existing, `common` module) — new constant for the 429 problem type.
- No new config-key names are specified anywhere in the spec package for this task (unlike T30's
  three pre-named `themistra.auth.cleanup.*` keys) — Phase 2 will need to name whatever
  configuration the chosen mechanism needs.

## Acceptance Criteria

- **AC1 (R41).** Exceeding the configured per-account request rate on the login path (`/login`,
  which also covers MFA verification per Phase 0's finding) results in `429 Too Many Requests`.
- **AC2 (R41).** Exceeding the configured per-account request rate on `/oauth2/token` results in
  `429 Too Many Requests`.
- **AC3 (R41).** Exceeding the configured per-account request rate on `POST /accounts/password-reset`
  results in `429 Too Many Requests`.
- **AC4 (R41).** A request rate within the configured threshold is never rejected by this
  mechanism (no false-positive throttling of normal traffic).
- **AC5 (R42).** The limiter is documented/positioned as a backstop, not a substitute for
  ingress-level IP limiting — no claim or behavior in this task should imply it's the primary
  defense layer.
- **AC6 (implied by this codebase's own conventions).** The 429 response body follows this
  service's established `application/problem+json` shape (even though it likely originates from a
  filter rather than a controller-thrown exception, unlike every other rejection cause in this
  codebase so far).

## Tests required

**Named test (`package.md` §8):** `shouldReturn429WhenPerAccountRateLimitExceeded`.

Implied boundary/behavioral tests:
1. A request within the threshold succeeds normally (no 429) — per path.
2. The request that crosses the threshold gets `429`, with the correct problem+json shape.
3. Different accounts' buckets are independent — one account being throttled must not affect
   another's requests on the same path.
4. Recovery: once the configured window/refill has elapsed, further requests from the
   previously-throttled account succeed again (proves it's a rate limiter, not a permanent block).
5. At least one HTTP-layer (`@SpringBootTest` + `TestRestTemplate`, Testcontainers-backed) test per
   path actually exercising the real filter chain, per this codebase's established pattern for
   proving filter-level behavior (`SasLoginIntegrationTest` precedent) — the task statement's own
   "Add 429 tests" phrasing implies this, not just a unit test of the bucket logic in isolation.

## Open Questions

- **OQ1 (genuine spec-level blocker, not just an implementation detail).** The exact
  requests-per-window thresholds for each path are **unspecified and explicitly unresolved** —
  `design.md` §4b-O2 asks the implementer to "propose thresholds... recommend values; proceed only
  if low-risk or after author approval," and `package.md` §11 Q2 is listed as an open question to
  the spec author with **no "Resolved" annotation**, unlike every other now-answered Q-item in that
  same list. This needs an explicit proposed-and-approved default at the Phase 3/4 gate — not
  something Phase 2 should silently invent without flagging it as exactly this.
- **OQ2.** R41 names four things ("login," `/oauth2/token`, "password-reset confirmation," "MFA
  verification") but Phase 0 traced only **three** actual HTTP paths — MFA verification happens
  inside the same `/login` POST as password verification, not a separate route. Does R41's intent
  mean: (a) rate-limit `/login` once, which incidentally also covers MFA attempts since they're the
  same request; or (b) something more granular the current implementation doesn't support without
  new instrumentation (e.g., distinguishing a TOTP-bearing login attempt from a password-only one
  for a *separate* bucket)? Not decided here — genuine design question for Phase 2/3.
- **OQ3.** How should a per-account bucket be keyed for `/oauth2/token`, given this service owns no
  controller for that endpoint and the account isn't resolvable until SAS internally processes the
  grant? Candidates (not decided here): a `Filter` that pre-parses the request body/token
  before SAS's own processing; keying on a coarser identifier (e.g., the presented token's hash)
  rather than a resolved account UUID; or accepting IP-level-only protection for this one path and
  documenting why per-account throttling isn't practical here without deeper SAS integration. R42's
  own "backstop, not primary defense" framing may make a coarser fallback acceptable for this one
  path specifically — Phase 2/3's call.
- **OQ4.** Library choice (Bucket4j vs. a hand-rolled concurrent-map implementation) — O2 offers
  both without mandating one; Phase 0 confirmed neither is currently cached/resolved in this repo,
  so this is a genuinely fresh choice with no existing-usage tiebreaker.

None of these four are blockers to producing a Phase 2 brief that proposes resolutions for Phase
3/4 to confirm or redirect — but OQ1 in particular is unusual among this pipeline's open questions
so far in being an *unresolved upstream spec question* (like T25's aud-claim filler value) rather
than purely an implementation-shape choice, so it deserves explicit, visible framing rather than
being bundled in with the others.

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
