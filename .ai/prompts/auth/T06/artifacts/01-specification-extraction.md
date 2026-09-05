# auth · T06 — Phase 1: Specification Extraction

## Business Rules

- **R3.** When `AccountService.register` succeeds, the system emits an `auth.email.requested`
  outbox event with purpose `verify_email`, in the same transaction as account creation.
- **R4.** A caller submitting a valid, unused verification token to `POST /accounts/verify-email`
  within its TTL causes the account to transition to `ACTIVE`, emits `auth.user.registered`, and
  returns success.
- **R5** (widened — see rationale below). An invalid, expired, already-used, or
  deleted/suspended-account-owning token returns a uniform failure response revealing nothing
  about account or token state.
- **R6** (modified per human decision at Phase 0 — see rationale below). A caller submitting an
  email to `POST /accounts/resend-verification` causes a new verification token to be issued and
  `auth.email.requested` to be emitted **only if** that email belongs to a `PENDING_VERIFICATION`
  account; the response is identical regardless.
- **R44.** `EventTopics` routes `auth.email.requested` events to the `auth.email.requested` Kafka
  topic.

**Widening rationale:**
- **R5** is not in this task's header (only L5 is), but L5 — which *is* scoped — is R5's LOCKED
  counterpart, and `verify-email` is the endpoint that must actually *produce* the uniform HTTP
  response R5/L5 describe (T05 only produced a uniform `Optional<UUID>` at the service level, not
  an HTTP response). Excluding R5 here would leave the endpoint's failure-response shape
  unspecified.
- **R6's "authenticated caller" wording is explicitly not followed as written.** Per the human
  decision recorded in Phase 0 (`00-repository-understanding.md`), `resend-verification` is public
  and email-identified — the same security model as `password-reset-request` (R12/R13, a sibling
  requirement not in this task's scope but the closest existing precedent for "public, email-in,
  uniform-ack-out, act-only-if-a-real-account-matches"). This is a deviation from R6's literal
  text, flagged here per the guardrail ("if one looks wrong, STOP and log it... never deviate
  silently") — already surfaced and confirmed with the human at Phase 0, not newly decided here.

## Locked Decisions

- **L5.** Enumeration-safe responses — `verify-email`'s failure path and `resend-verification`'s
  response (regardless of account existence/state) must both satisfy this.
- **L11** (widened — directly operative, not in the header but unavoidable). "Public endpoint
  discipline... any new public path must be added to `PublicEndpoints.java`." Both
  `POST /accounts/verify-email` and `POST /accounts/resend-verification` are new public paths
  (per the Phase 0 human decision) and must be registered there.

## Files involved

**Existing, to extend:**
- `account/AccountController.java` — add `POST /accounts/verify-email`,
  `POST /accounts/resend-verification`. Existing precedent:
  `register()`'s local `try/catch (DuplicateEmailException ignored)` — the pattern for keeping a
  distinguishing exception from ever reaching a generic handler on a public, enumeration-sensitive
  endpoint.
- `account/AccountService.java` — needs new methods wiring `VerificationTokenService` to
  `Account.activateEmail()` (for verify-email) and to `VerificationTokenService.issue` (for
  resend). `register(...)` needs its own new call to emit `auth.email.requested` after account
  creation, in the same transaction.
- `account/AccountExceptionHandler.java` — needs a uniform mapping for "verification token
  rejected" (R5) — distinct from its existing `AccountNotFoundException`/
  `InvalidAccountStateException` mappings, which are deliberately *not* uniform (they exist
  precisely to distinguish cases, unlike R5).
- `events/EventTopics.java` — add `"verification-token" -> "auth.email.requested"` (the map
  already has a comment marking exactly this).
- `common/PublicEndpoints.java` — add both new paths to `METHOD_SCOPED`.

**Existing, to read/reuse, not modify:**
- `account/VerificationTokenService` (T05) — `issue(accountUuid, Purpose.EMAIL_VERIFY)` for
  registration and resend; `consume(rawToken)` for verify-email (an `Optional<UUID>` — present
  resolves to the account UUID to activate, empty is R5's uniform-rejection signal).
- `account/Account.activateEmail()` — already exists, guarded
  (`PENDING_VERIFICATION → ACTIVE`), currently reachable only via the admin stand-in endpoint.
- `account/AccountRepository.findByEmail(String)` — already exists (case-insensitive via
  `citext`), needed to resolve `resend-verification`'s email input to an account.
- `events/OutboxPublisher.publish(...)`.

**New, per `design.md` §6's package map:**
- `account/dto/VerifyEmailRequest.java`
- `account/dto/ResendVerificationRequest.java`
- `account/event/EmailRequestedEventPayload.java`

**Not this task:** `PasswordResetRequest`/`PasswordResetConfirmRequest` (task 7),
`ChangePasswordRequest` (task 8), `PasswordPolicy` wiring (task 9 — registration's password
strength enforcement is explicitly out of scope here).

## Dependencies

- `VerificationTokenService.issue`/`consume` (T05).
- `AccountRepository.findByEmail`, `AccountService`'s existing account lookup/transition methods.
- `OutboxPublisher`, `EventTopics` (both existing, one line added to the latter).
- `common.ProblemTypes` — likely needs a new stable URI for the R5 uniform-rejection response
  (none of the existing five read as an obvious fit for "token invalid/expired/used,
  indistinguishably").
- Config: `themistra.auth.verification-token.ttl-minutes` already exists (T05) — no new key
  expected.
- **`package.md` §11 Q4 (cited, not fully resolved — see Open Questions):** *"Email link base URL.
  Verification and password-reset links need a base URL + path. Should this come from
  `SPA_REDIRECT_URI`/`AUTH_ISSUER_URI`, or does the Notification Service need a new
  `AUTH_EMAIL_LINK_BASE_URL` secret?"*
- **Contract note:** `contracts/events/auth/email-requested.v1.schema.json` is listed in this
  task's header but does not yet exist (`tasks.md` task 33 authors it later). T06 emits the event
  its shape anticipates but is not bound by a formal schema yet — `EmailRequestedEventPayload`'s
  exact fields are this task's own decision to make (Phase 2/5), not a contract to conform to.

## Acceptance Criteria

- **R3** — `POST /accounts` still returns the existing uniform `202` acknowledgement (unchanged
  from T02); in the same transaction, an `auth.email.requested` outbox row with purpose
  `verify_email` is created for the new account.
- **R4** — a valid, unexpired, unused token to `verify-email` transitions the account to `ACTIVE`,
  emits `auth.user.registered`, returns a success response.
- **R5** — an invalid/expired/used/deleted-or-suspended-account token returns the *same* response
  (status + body) as every other invalid reason — no timing, status, or body difference a caller
  could use to distinguish them.
- **R6 (as modified)** — `resend-verification` returns the identical acknowledgement whether the
  submitted email belongs to a `PENDING_VERIFICATION` account, a different-state account, or no
  account at all; a new token and `auth.email.requested` event are produced only in the first case.
- **R44** — `EventTopics.forAggregateType("verification-token")` returns `"auth.email.requested"`.
- **L11** — both new paths appear in `PublicEndpoints.METHOD_SCOPED`; the existing ArchUnit sweep
  (`only_token_module_references_public_endpoints` et al., per T03's Phase 0 notes) continues to
  pass.

## Tests required

**Named (`package.md` §8):**
- `shouldActivateAccountWithValidVerificationToken` — now gets its full, literal (HTTP +
  account-activation) realization, as anticipated at T05's Phase 0.
- `shouldResendVerificationOnlyForPending accounts` *(sic — space in "Pending accounts" preserved
  verbatim from the header)* — resend only actually issues for `PENDING_VERIFICATION` accounts;
  uniform response otherwise.
- `shouldEmitVerifyEmailEventOnRegistration` — R3.
- `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic` — R44; likely a small, direct
  `EventTopics` test (existing `EventTopicsTest.java` is the precedent to check at Phase 5).

**Boundary/implied tests:**
- `verify-email` with an expired token (TTL boundary, mirroring T05's own boundary tests but now
  through the endpoint).
- `verify-email` called twice with the same token (second call must hit the same uniform R5 path
  T05's `consume` already guarantees — this task just needs to not break that guarantee at the
  controller/response layer).
- `resend-verification` for an already-`ACTIVE` account — uniform response, no token issued.
- `resend-verification` for a nonexistent email — uniform response, no token issued (enumeration
  safety, mirroring `password-reset-request`'s established shape).
- Registration still returns its existing uniform ack even if outbox publication were to fail
  (transactional consistency — not a new requirement, but worth confirming R3's event emission
  doesn't change `register`'s existing external contract).

## Open Questions

- **Q4 (`package.md` §11, cited, likely non-blocking for T06 itself).** The email-link base URL
  question affects whoever builds the Notification Service's email templates, not this task's own
  endpoints or `AccountService` wiring — T06 only needs to emit *something* Notification Service
  can use (the raw token, at minimum) via `EmailRequestedEventPayload`. Not a blocker to start
  Phase 2, but the exact payload shape decision (raw token vs. a pre-built link) is a real,
  security-relevant design point deferred to Phase 2/3, since agents.md's "a credential in transit
  appears exactly once" principle is normally about HTTP responses, not async event payloads — this
  task is the first place that tension actually arises.
