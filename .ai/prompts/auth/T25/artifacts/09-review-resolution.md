<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T25 · Phase 9 — Review Resolution

**Human Approval gate. Decided by femi, 2026-08-15.** Consumes the self-review (`artifacts/07-self-review.md`) and independent review (`artifacts/08-independent-review.md`). Findings below are de-duplicated across both artifacts — Kimi (Phase 8) independently confirmed five of my own Phase 7 findings and surfaced two new ones (CSRF, credential trimming).

---

## Resolution Log

### 1. `JwtEncoder` bean throws on every token issuance during key rotation
*(Phase 7 Finding 1 = Phase 8 Finding 1, High/High)*

**ACCEPTED.** `token/JwksConfig.java` — the `jwtEncoder` bean now builds a `NimbusJwtEncoder`, calls `.setJwkSelector(List::getFirst)`, then returns it. This makes the encoder actually honour `SigningKeyMaterial`'s documented "CURRENT key is first" ordering instead of throwing when both CURRENT and PREVIOUS keys are present. Same object SAS's own grant paths will now use, so this closes the identical latent bug there too, at no cost.

### 2. TTL upper bound (525,600 minutes) is the wrong reference class
*(Phase 7 Finding 2 = Phase 8 Finding 2, Medium/Medium)*

**ACCEPTED.** `apikey/ApiKeyProperties.java` — `tokenTtlMinutes`'s `@Max` lowered from `525_600` to `1440` (24 hours). Still comfortably covers any reasonable operational TTL while failing an operator typo that would otherwise silently mint a multi-month-lived bearer token.

### 3. CSRF blocks every real (anonymous, session-less) caller of `POST /api-keys/token`
*(Phase 8 Finding 3, new — Medium confidence as reported, verified High during resolution)*

**ACCEPTED, scoped to T25's own endpoint only.** Verified against this codebase's own evidence, not just general Spring Security semantics: `SasLoginIntegrationTest` — the only test that exercises the real `@Order(2)` filter chain end-to-end — has to scrape a `_csrf` token off `/login` and submit it with the form, because `CsrfFilter` rejects session-less POSTs on this exact chain. `SecurityChainsConfig.java`'s CSRF ignore list only covered `/api/**`, which matches none of this service's actual paths, so a machine client calling `POST /api-keys/token` cold would get a 403 from `CsrfFilter` before ever reaching `ApiKeyController` — the endpoint was non-functional for its entire intended audience as implemented.

femi's decision (offered three options: T25-scoped fix / systemic fix covering all five affected public POST endpoints / defer entirely): **T25-scoped fix.** `SecurityChainsConfig.java` — despite being listed under the frozen brief's "Files NOT to Modify" — gets one additive line: `/api-keys/token` added alongside `/api/**` in `.csrf(csrf -> csrf.ignoringRequestMatchers(...))`. This is a genuine, gate-approved exception to that file-freeze, not a silent deviation.

**Not fixed, logged as a separate pre-existing gap:** the same defect affects `POST /accounts`, `/accounts/verify-email`, `/accounts/resend-verification`, `/accounts/password-reset-request`, and `/accounts/password-reset` — none of which are under `/api/**` either, and none of which have ever been tested through the real filter chain (their only tests bypass Spring Security via direct controller construction). This is out of T25's scope by femi's explicit choice; flagged here for a dedicated follow-up task, not silently left undiscovered.

### 4. Possible `NullPointerException` if `ExchangeResult.accountUuid()` is null
*(Phase 7 Finding 3 = Phase 8 Finding 4, Low/Low)*

**ACCEPTED.** `apikey/ApiKeyTokenIssuer.java` — `issue` now guards `accountUuid == null` and throws a dedicated `IllegalStateException("Exchanged API key has no resolvable owning account")` instead of risking a bare NPE at `.toString()`. Same externally-observable outcome (an opaque 500, never a 401, per D5) — this only makes the failure intentional and diagnosable rather than accidental. No change to `ApiKeyService` (frozen).

### 5. Credential extraction doesn't trim incidental whitespace
*(Phase 8 Finding 5, new, Low)*

**ACCEPTED.** `apikey/ApiKeyController.java` — `extractCredential` now calls `.trim()` on the substring after the scheme separator, before the blank/length checks. RFC 7235's `credentials` grammar separates scheme and credential with `1*SP` (one or more spaces); a real `ck_live_<suffix>.<secret>` key contains no internal whitespace (L7), so trimming cannot create a false accept — it only helps a compliant client whose header has an extra separator space. Read as consistent with the frozen brief's "credential taken verbatim" (referring to case-sensitivity, contrasted against the case-insensitive scheme match), not in tension with it.

### 6. `aud` claim mirrors `client_id` (`checky-api-key`)
*(Phase 7 Finding 5 = Phase 8 Finding 6, Info/Info)*

**RATIFIED AS-IS. No code change.** L9 mandates the claim; the frozen brief never fixed its value (unlike `client_id`, fixed by D2); there is no `RegisteredClient` to derive it from (D2 declines to seed one). femi confirmed the implemented default (`aud = client_id = "checky-api-key"`) stands.

### 7. `Bearer`-schemed requests get a different rejection body than every other cause
*(Phase 7 Finding 4 = Phase 8 Finding 7, Info/Info)*

**No code change — already correct, by design.** `Bearer` is intentionally never accepted (D4); a `Bearer`-schemed request is intercepted by the pre-existing `BearerTokenAuthenticationFilter` before reaching `ApiKeyController`, producing a filter-level 401 with no JSON body, not this module's uniform `application/problem+json`. **Action carried to Phase 10:** the required test for "wrong scheme (including Bearer) → same uniform 401" must assert status-only equivalence for the `Bearer` case, not a byte-identical body, and should say so in its Javadoc so a future maintainer doesn't mistake this for a bug to chase.

### 8. `SasLoginIntegrationTest` / full Testcontainers suite not executed this session
*(Phase 8 Finding 8, new — procedural, not a code defect)*

**Acknowledged, no action possible this session.** Docker remains unavailable (`docker info` fails). Whoever has Docker access should run `SasLoginIntegrationTest` specifically (now doubly relevant: it verifies both D1's original claim and the Finding 1 selector fix don't disturb SAS's own grants) plus the full suite before this task is considered fully proven end-to-end.

---

## Build Verification (post-resolution)

`mvn -q -pl services/auth -am compile` — clean, exit 0.

Targeted regression (`TokenClaimsCustomizerTest`, `ApiKeyHasherTest`, `PublicEndpointsTest` — the three pure-unit tests unaffected by Docker that exercise files this phase touched): all pass, 12/12, 0 failures.
- `TokenClaimsCustomizerTest`: 8/8 — confirms the `JwtEncoder` selector change doesn't disturb `TokenClaimsCustomizer`.
- `ApiKeyHasherTest`: 3/3 — unaffected by the controller/issuer changes.
- `PublicEndpointsTest`: 1/1 — unaffected (its assertions don't touch `/api-keys/token` or CSRF).

Full Testcontainers-backed suite (`ApiKeyServiceIntegrationTest`, `AuthServiceApplicationTests`, `SasLoginIntegrationTest`, etc.) remains blocked on Docker being down — same pre-existing environment gap noted at every phase since Phase 6, not a new regression from this phase's changes.

---

## Files Touched This Phase

- `token/SecurityChainsConfig.java` — CSRF ignore list, +1 line (gate-approved exception to the file freeze).
- `token/JwksConfig.java` — `jwtEncoder` bean now sets an explicit `jwkSelector`.
- `apikey/ApiKeyProperties.java` — `tokenTtlMinutes`'s `@Max` lowered.
- `apikey/ApiKeyTokenIssuer.java` — explicit null guard on `accountUuid`.
- `apikey/ApiKeyController.java` — `extractCredential` trims the credential.

No file under `spec/` touched. No public API, class name, or method signature changed — every fix is additive/internal, per this phase's rule against refactoring.

---

**Phase 9 complete — human sign-off given, accepted comments applied.** Proceed to Phase 10 (Test Generation).
