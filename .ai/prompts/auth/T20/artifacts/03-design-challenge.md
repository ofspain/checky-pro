# auth · T20 · Phase 3 — Design Challenge

Consumed `artifacts/02-task-implementation-brief.md`, `artifacts/01-specification-extraction.md`, the spec package (`requirements.md`, `design.md`, `tasks.md`, `agents.md`), and the existing `token`/`authn`/`authz`/`mfa` code the brief touches.

No explicit conflicts with the LOCKED decisions (L6, L9, L10, L12) were found at the level the brief already states. The issues below are unstated assumptions, ambiguous rules, and gaps that should be resolved before the brief is frozen in Phase 4.

---

1. **Issue · The login-challenge presentation (O4) is a true blocker, not a deferrable detail.**
   **Severity:** High.
   **Evidence:** The TIB lists O4 as an explicit "Blocker" open question. `design.md` §4b-O4 says only "Login page presentation ... default Spring Security form or a minimal custom Thymeleaf template." Until it is decided whether the TOTP/recovery code is collected as a second field on `/login`, a second `POST` to `/login`, or a separate `/mfa-challenge` endpoint, the file list, `PublicEndpoints` entries, session-state design, and test approach are all undetermined.
   **Recommended brief amendment:** Lock one option in Phase 4 before freezing. For example: extend the default `/login` form with an optional `mfaCode` field; a non-empty field after a successful password submission triggers the MFA provider. State the chosen option, any new endpoint/template files, and how the partially-authenticated principal is held between steps.

2. **Issue · R24's "must complete enrollment" requirement assumes self-service endpoints that are out of scope.**
   **Severity:** High.
   **Evidence:** R24 requires a `MERCHANT`/`ADMIN` without confirmed TOTP enrollment to complete enrollment before an authorization code is issued. The TIB's **Out** section explicitly excludes the self-service enrollment/confirm/disable endpoints (task 19). There is no described in-flow enrollment mechanism, redirect target, or task dependency that would let T20 satisfy R24 on its own.
   **Recommended brief amendment:** Either (a) add the necessary task-19 controller/DTOs as a T20 dependency/scope inclusion, (b) specify that R24 is implemented as a hard block with a redirect to a task-19 endpoint to be built later, or (c) implement enrollment initiation inside the SAS flow. Do not leave R24 satisfiable while its only completion path is excluded.

3. **Issue · R26/R27 responsibility is in tension with `tasks.md` task 21 and the brief's resolution is not locked.**
   **Severity:** Medium.
   **Evidence:** The task header scopes R26/R27, and the TIB puts `TokenClaimsCustomizer` changes in scope. `tasks.md` sequences a separate "Token claim updates" task (21) after this one. The TIB resolves the tension by treating the header as authoritative, but flags it as unresolved. This determines whether T20's tests must assert exact claim values or only that the MFA outcome is available for a later consumer.
   **Recommended brief amendment:** Explicitly state whether T20 emits the final `amr`/`acr` values itself (overriding the task-21 split) or only exposes the MFA outcome to `TokenClaimsCustomizer` for task 21 to consume. If T20 emits the claims, update `tasks.md` or note the scope override; if not, remove R26/R27 from T20's ACs and named-test obligations.

4. **Issue · No mechanism is specified for `TokenClaimsCustomizer` to read the MFA outcome.**
   **Severity:** Medium.
   **Evidence:** `TokenClaimsCustomizer.customizeAccessToken` receives a `JwtEncodingContext` whose principal comes from the SAS authorization. The TIB names `TotpStepUpAuthenticationToken` as the carrier but does not say how that token becomes visible to the customizer (as the principal, via `SecurityContextHolder`, or via authorization attributes). Without this, R26/R27 cannot be implemented or tested.
   **Recommended brief amendment:** Specify the integration point: e.g., "`TotpAuthenticationProvider` authenticates the `TotpStepUpAuthenticationToken`; SAS propagates it as the OAuth2 principal, and `TokenClaimsCustomizer` reads `context.getPrincipal()` to decide `amr`/`acr`."

5. **Issue · The contract for the two new `MfaService` methods is not defined.**
   **Severity:** Medium.
   **Evidence:** The TIB says `MfaService` needs "(a) confirmed-enrollment status check and (b) TOTP-code verification at login without `confirm`/`disable`'s side effects" but gives no names, signatures, exceptions, return values, or audit obligations. Test authors cannot write assertions against an unspecified contract.
   **Recommended brief amendment:** Add concrete method signatures and behavior. Example: `boolean hasConfirmedTotpEnrollment(UUID accountUuid)`; `void verifyTotpCode(UUID accountUuid, String submittedCode)` throws `MfaNotEnrolledException` / `InvalidTotpCodeException`, records `mfa.failed` on failure, and is side-effect-free on success.

6. **Issue · Enumeration safety for MFA-step failures is assumed but not designed.**
   **Severity:** Medium.
   **Evidence:** L5 names login/registration/reset/verification endpoints but not the MFA step; the TIB correctly extends enumeration safety to it via `agents.md`. However, there is no design for how a wrong TOTP/recovery code surfaces identically to a password failure. A typed exception from `TotpAuthenticationProvider` could produce a different status/body/timing if not explicitly mapped.
   **Recommended brief amendment:** State that MFA failures must be handled by the same failure handler as password failures (`LoginFailureHandler` or a shared successor) and that the response is indistinguishable from "bad credentials," including constant-time comparison and no per-factor error text.

7. **Issue · TOTP replay resistance and `MfaEnrollment.lastUsedAt` are not mentioned.**
   **Severity:** Medium.
   **Evidence:** T18 explicitly deferred replay resistance to task 20. `MfaEnrollment` has `recordUse(Instant)` and a `lastUsedAt` column built for this purpose. The TIB does not say whether task 20 will use them. Without replay tracking, the same valid TOTP code can be reused within the 90s tolerance window across repeated login attempts.
   **Recommended brief amendment:** Decide and document: either implement replay resistance (record the last accepted time step, reject codes at or before it, and call `recordUse`) or explicitly accept the replay window and state the security rationale.

8. **Issue · The security posture of any new MFA challenge endpoint is unresolved.**
   **Severity:** Medium.
   **Evidence:** A user who has passed the password step but not yet completed MFA is in a semi-authenticated state. `PublicEndpoints.java` and the application chain only understand fully public or fully JWT-authenticated paths. If O4 produces a separate `/mfa-challenge` endpoint, it does not fit either bucket and could be incorrectly rejected or left open.
   **Recommended brief amendment:** Resolve O4 first, then explicitly state whether a new path is added, how it is secured (e.g., a session-bound `MfaPendingAuthentication` is required), and whether it belongs in `PublicEndpoints` or a new allowlist.

9. **Issue · Account-state preconditions for the new login-time MFA methods are not specified.**
   **Severity:** Low.
   **Evidence:** The password step already gates account status, but a lockout-elapsed account can have `AccountStatus.LOCKED` and still reach the MFA step. `MfaService.verifyRecoveryCode` has no status check (a T18-deferred finding), and the new TOTP verify method's status policy is not stated.
   **Recommended brief amendment:** State whether the new methods trust the password step's gating entirely or perform their own `ACTIVE` check, and what exception they throw if the check fails.

10. **Issue · Refresh-token grant `amr`/`acr` behavior is unspecified.**
    **Severity:** Low.
    **Evidence:** `TokenClaimsCustomizer` runs for every access token, including refresh-token grants. After T20, refreshing a password-only token for an account that now has confirmed MFA could still emit `amr: ["pwd"]` / `acr: pwd`, or could re-evaluate the requirement. R26/R27 speak only of the interactive login event.
    **Recommended brief amendment:** State whether refresh tokens preserve the original authentication's `amr`/`acr` or re-evaluate MFA status at refresh time, so tests can assert the correct behavior.
