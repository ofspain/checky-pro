# auth · T06 — Phase 0: Repository Understanding

## 1. Architecture summary

Same service as T03/T05: `services/auth`, Spring Boot 3.5.4 / Java 21, package-by-feature,
Flyway-owned schema, one Postgres schema (`auth`). Relevant subset for T06:

- **`account` module** owns `AccountController` (currently `POST /accounts` and `GET /accounts/me`
  only), `AccountService`, `AccountExceptionHandler`, and — as of T03/T05 — `PasswordPolicy` and
  `VerificationTokenService`, neither of which is wired into the controller/service yet.
- **`events` module**: `OutboxPublisher.publish(aggregateType, aggregateId, eventType,
  schemaVersion, payload)` is the only sanctioned way to emit an event — appends to the outbox in
  the caller's own transaction (no dual-write). `EventTopics.forAggregateType(...)` maps aggregate
  type → Kafka topic and **throws `IllegalStateException` for an unmapped type** — it is not a
  silent no-op. `EventTopics.java` currently has a literal comment marking this task's expected
  change: `// "verification-token" -> "auth.email.requested" joins this table when the account
  email-verification stage lands.`
- **`common.PublicEndpoints`**: the CI-enforced exhaustive unauthenticated-path list. Currently
  only `POST /accounts` (method-scoped). Nothing under `/accounts/verify-email` or
  `/accounts/resend-verification` is listed yet.

## 2. Existing code this task touches

**Task statement, verbatim:** *"Extend `AccountController` with `POST /accounts/verify-email` and
`POST /accounts/resend-verification`. Emit `auth.email.requested` via `OutboxPublisher`. Update
`EventTopics` with the `verification-token` aggregate mapping."*

**Already built (by T03/T05), to be wired here for the first time — nothing about them changes,
they are called, not modified:**
- `account.VerificationTokenService` — `issue(accountUuid, purpose)`,
  `verify(rawToken)`/`consume(rawToken)` returning `Optional<UUID>`. `issue` throws
  `AccountNotFoundException` for an unresolvable account UUID (an internal-caller error signal,
  per its own javadoc — not the R5 uniform-rejection path).
- `account.VerificationToken.Purpose` enum (`EMAIL_VERIFY`, `PASSWORD_RESET`) — T06 uses
  `EMAIL_VERIFY` only; `PASSWORD_RESET` is task 7's.
- `account.PasswordPolicy` — **not used by T06.** Registration's password-policy enforcement is
  task 9's scope ("Password policy enforcement... registration, change-password, and
  password-reset"), not this task's.

**Existing, to extend:**
- `account.AccountController` — add `POST /accounts/verify-email`,
  `POST /accounts/resend-verification`. Existing pattern:
  `POST /accounts` catches `DuplicateEmailException` locally to stay enumeration-safe rather than
  letting `AccountExceptionHandler` see it.
- `account.AccountService` — `register(...)` currently does **not** emit any event (its own
  javadoc: *"No event is published here: per target-design §9, `auth.user.registered` fires at
  email-confirmation time (`activateEmail`), not at initial signup. The `auth.email.requested`
  event... belongs to the not-yet-built verification-token flow"* — this task is exactly that).
  `activateEmail()` already exists and is guarded (`PENDING_VERIFICATION → ACTIVE`), currently only
  reachable via the admin endpoint per `AdminAccountController`'s own javadoc (*"verification flow
  is not yet built, so this admin action stands in for it until it is"*) — T06 is what replaces
  that stand-in with the self-service path R4 describes.
- `account.AccountExceptionHandler` — currently maps `AccountNotFoundException` (404) and
  `InvalidAccountStateException` (409). Neither is R5's uniform-failure shape; a new mapping (or
  reuse) for "invalid/expired/used verification token" needs a uniform, non-distinguishing
  response.
- `events.EventTopics` — add `"verification-token" -> "auth.email.requested"` to the map (the
  literal next step, per the file's own comment).
- `common.PublicEndpoints` — at least `POST /accounts/verify-email` needs to be added; see Known
  gaps below for `resend-verification`'s status.

**New, per `design.md` §6's package map (T03's Phase 0 already surfaced this map; only the
verify/resend-relevant subset applies to T06):**
- `account/dto/VerifyEmailRequest.java`
- `account/dto/ResendVerificationRequest.java`
- `account/event/EmailRequestedEventPayload.java`

**Not this task:** `PasswordResetRequest`/`PasswordResetConfirmRequest` (task 7),
`ChangePasswordRequest` (task 8), `PasswordEncoderFacade` (optional, unassigned).

## 3. Established patterns to follow

- **Enumeration safety at the controller, not just the service**: `AccountController.register`'s
  existing `try/catch (DuplicateEmailException ignored)` is the precedent — a public endpoint
  swallows a distinguishing exception locally rather than letting a generic handler map it to a
  response that could differ in status/body/timing from the "success" case.
- **Outbox-in-transaction**: any new `OutboxPublisher.publish(...)` call must happen inside the
  same `@Transactional` method as the state change it accompanies (`AccountService`'s existing
  `publishLifecycleEvent` private helper is the pattern to mirror, not necessarily reuse directly
  since this is a different aggregate type).
- **`EventTopics` fails loudly on an unmapped type** — if T06 publishes with aggregate type
  `"verification-token"` before updating the map, the service throws at runtime, not silently
  drops the event. The map update and the first publish call must land together.
- **`Clock`-sourced timestamps**, validated `@ConfigurationProperties`, package-private
  repositories — all as established since T02/T03.

## 4. Testing conventions

Unit tests: plain JUnit 5 + Mockito, no Spring context, fixed `Clock`, consistent with every prior
task. Given this task adds real HTTP endpoints for the first time in this chain (T03/T05 were
domain-only), a `@WebMvcTest`-style or `MockMvc`-based controller test may be warranted — `agents.md`
doesn't prescribe this explicitly, but `AccountControllerTest` already exists in the codebase
(T02-era) and is the precedent to check when this reaches Phase 2/5.

## 5. Known gaps / unknowns

**(a) R6 requires an "authenticated caller" whose account is `PENDING_VERIFICATION` — but
`Account.canAuthenticate()` currently returns `true` only for `ACTIVE` accounts, and nothing in the
codebase yet enforces this at the SAS authentication-provider level** (`grep` for
`canAuthenticate()` usage finds only its own definition — no caller exists yet). I do not know
whether:
- a `PENDING_VERIFICATION` account is actually able to obtain a JWT today (if SAS's real
  authentication provider isn't built yet, login might currently succeed for any account
  regardless of status, making R6's "authenticated caller" trivially satisfiable for now but
  fragile once lockout/authentication-provider tasks land and start enforcing `canAuthenticate()`), or
- `resend-verification` needs to be a public, token-possession-secured endpoint instead (more
  consistent with `verify-email`'s own R4/R5 wording, which never says "authenticated caller" —
  only "a caller submits a valid... token"), or
- this is a genuine, deliberate design point requiring `canAuthenticate()` (or the SAS provider) to
  be loosened for `PENDING_VERIFICATION` specifically, which would be a change well outside this
  task's stated scope ("Extend `AccountController`... Emit... Update `EventTopics`").

This is a real blocker for Phase 1/2 scoping, not a speculative concern — the answer changes
whether `resend-verification` needs `Authentication` (like `GET /accounts/me` does today) or a
request-body token, and whether it belongs in `PublicEndpoints`.

**Resolved (human-confirmed):** `resend-verification` is public, not authenticated — the same
security model as `verify-email`. Since no token exists yet at this point (that's what the
endpoint generates), identification is by **email address**, mirroring `password-reset-request`'s
existing shape (R12/R13: caller submits an email; the system returns the identical acknowledgement
regardless of whether that email exists or its state, and only actually issues a token and emits
`auth.email.requested` when a matching `PENDING_VERIFICATION` account exists). This is an explicit,
human-approved reading that supersedes R6's literal "authenticated caller" wording — flagged here
as a deviation, not applied silently. `resend-verification` therefore also needs an entry in
`PublicEndpoints`, alongside `verify-email`.

**(b) `PublicEndpoints`'s current list (L11, LOCKED) does not yet include `/accounts/verify-email`
or `/accounts/resend-verification`.** Reading L11's exact wording — *"Any new public path must be
added to `PublicEndpoints.java`"* — this reads as an ongoing registration requirement, not a frozen
enumeration; adding `verify-email` (and possibly `resend-verification`, pending (a) above) is the
expected extension mechanism, not a deviation from the LOCKED decision. Flagged for confirmation
at Phase 1, not treated as a conflict requiring escalation.

**(c) I do not know the exact response shape for `verify-email` beyond "success" and "uniform
failure."** `AccountExceptionHandler`'s existing mappings (404/409) are not R5-uniform (they *are*
distinguishing responses by design, for the account-not-found/invalid-transition cases they exist
for). A new, single failure response shape for verification-token rejection needs a decision on
status code and problem type — `common.ProblemTypes` doesn't yet have an obvious candidate beyond
the generic ones (`VALIDATION_ERROR`, `NOT_FOUND`, `CONFLICT`, `INVALID_STATE`, `INTERNAL_ERROR` —
none obviously right for "token invalid/expired/used, indistinguishably").
