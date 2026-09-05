<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T25 · Phase 7 — Self Review

Reviews the Phase 6 diff (`artifacts/06-implementation-notes.md`) against the frozen brief and `agents.md`. Findings only — no fixes applied here (Phase 9).

---

### Finding 1

**Issue:** The new `jwtEncoder` bean will throw `JwtEncodingException` (surfacing as an opaque 500 via `ApiExceptionHandler.onUnexpected`) for **every** token this service issues — not just `POST /api-keys/token` — during any window where both a CURRENT and a PREVIOUS signing key are configured (i.e., mid key-rotation).

**Severity:** High

**Evidence:**
- `token/JwksConfig.java:47-48` — `new NimbusJwtEncoder(jwkSource)`, no `setJwkSelector(...)` call.
- `NimbusJwtEncoder` (spring-security-oauth2-jose 6.5.2, confirmed the resolved version via `mvn dependency:tree`) ships a default `jwkSelector` that **throws** — not "picks the first" — whenever `selectJwk` finds more than one candidate JWK (`NimbusJwtEncoder.java`: the field initializer for `jwkSelector` throws `"Failed to select a key since there are multiple for the signing algorithm..."`; `selectJwk` only reaches that selector when `jwks.size() > 1`).
- `token/SigningKeyMaterial.java:73-76` — `RSAKey.Builder(...).privateKey(...).keyID(slot.kid()).build()` never calls `.keyUse(...)` or `.algorithm(...)` on **either** the CURRENT or PREVIOUS key.
- Traced through Nimbus's `JWKMatcher.matches` (nimbus-jose-jwt 10.9, sources verified): a `null` key use matches a `keyUses(SIGNATURE, null)` constraint, and a `null` key algorithm matches an `algorithms(RS256, null)` constraint — both keys pass identically once nothing else distinguishes them, and `createJwkMatcher` requests no `keyID` (the default `JwsHeader` carries none). Result: `jwkSource.get(...)` returns **both** keys whenever `previous().configured()` is true, and `NimbusJwtEncoder.selectJwk` has no basis to pick one.

This directly contradicts `SigningKeyMaterial`'s own class Javadoc ("the JWT encoder selects the first eligible key") and the frozen brief's D1 rationale that declaring this bean is "behaviourally identical to what SAS constructs internally today... so existing grants are unaffected." That conclusion is only true in the narrow sense that SAS's own pre-T25 fallback (`OAuth2ConfigurerUtils.getJwtEncoder` → the identical bare `new NimbusJwtEncoder(jwkSource)` when no bean exists) carries the **exact same latent flaw** — so T25 makes no *existing* grant worse than it already was. But it means the "safe because identical" argument is safe-by-shared-bug, not safe-by-design, and `ApiKeyTokenIssuer` now depends on this same landmine for its own correctness during any real key rotation.

**Recommendation:** On the bean declared in `JwksConfig.java`, call `.setJwkSelector(List::getFirst)` (or an equivalent explicit selector) so the encoder's actual behavior matches `SigningKeyMaterial`'s documented "CURRENT key is first" ordering contract. One line, confined to a file T25 already owns. Whether to also treat this as a fix for SAS's pre-existing grant paths (same object, so the same call fixes both) is a Phase 9 human-gate call, not something to decide silently here.

---

### Finding 2

**Issue:** `ApiKeyProperties.tokenTtlMinutes`'s upper bound (`@Max(525_600)`, one year) is copied verbatim from `VerificationTokenProperties`'s precedent, but that precedent governs a fundamentally different kind of token — a single-use, one-time link (email verification / password reset), where an operator accidentally setting a multi-month TTL is merely generous. This property governs a **repeatedly reissued bearer access token** (`agents.md`: "access-token TTL 10 minutes"; L8's own default is 10 minutes) — a misconfiguration here (e.g., an operator fat-fingering `10` as `10000`, still comfortably under the bound) would silently produce long-lived bearer tokens for every API-key exchange, a materially different risk class than a stale verification link.

**Severity:** Medium

**Evidence:** `apikey/ApiKeyProperties.java:20` (`@Min(1) @Max(525_600) long tokenTtlMinutes`) vs. `account/VerificationTokenProperties.java:17` (the precedent it was copied from, for a semantically different token).

**Recommendation:** Tighten the upper bound to something that still fails a genuinely absurd config (e.g., a few hours) while comfortably covering any reasonable operational TTL, rather than reusing a bound sized for a different token category. Bikeshed-adjacent — a Phase 9 judgment call on the exact number, not a blocker.

---

### Finding 3

**Issue:** A `NullPointerException` is reachable in `ApiKeyTokenIssuer.issue` if `ApiKeyService.exchange`'s success path ever returns an `ExchangeResult` with a `null` `accountUuid`.

**Severity:** Low (pre-existing invariant, not introduced by T25; documented residual)

**Evidence:** `apikey/ApiKeyTokenIssuer.java:68` (`.subject(accountUuid.toString())`, no null check) consumes `ApiKeyService.ExchangeResult.accountUuid()`, which on the success branch is populated via `resolveAccountUuidQuietly(matched.getAccountId())` — a method `ApiKeyService.java` itself documents as "best-effort... a missing account here (should not happen in practice) simply yields a null audit target rather than failing the exchange call itself." That same nullable value is also what `exchange` returns as the "successful" result.

**Recommendation:** No action required against `ApiKeyService` (frozen, T24, out of T25's authorized file set). If this is ever judged worth hardening, the guard belongs in `ApiKeyTokenIssuer.issue` (fail loudly and specifically rather than via a generic NPE), but the resulting outcome — an opaque 500, never a 401 — is already the correct externally-observable behavior for a "should not happen" internal-data-integrity case, consistent with D5's own reasoning for signing failures. Flagging for awareness, not requesting a fix.

---

### Finding 4 (informational, already disclosed in Phase 6)

**Issue:** A genuinely `Bearer`-schemed request to `POST /api-keys/token` never reaches `ApiKeyController` — it is intercepted by the pre-existing `BearerTokenAuthenticationFilter` on the `@Order(2)` chain (its token68 regex matches this service's key format) and rejected with a filter-level 401 (`WWW-Authenticate` header, no JSON body), not this module's uniform `application/problem+json` body.

**Severity:** Info / carried forward

**Evidence:** `artifacts/06-implementation-notes.md`, Deviations section — full trace already recorded there against `SecurityChainsConfig.java:71` and Spring Security's `DefaultBearerTokenResolver`/`BearerTokenAuthenticationEntryPoint`.

**Recommendation:** No new action here; restated so Phase 9's resolution and Phase 12's verification explicitly account for it rather than assuming Required Test #8's "same uniform 401" means byte-identical for the `Bearer` case specifically.

---

### Finding 5 (informational, already disclosed in Phase 6)

**Issue:** The `aud` claim's value (`"checky-api-key"`, mirroring `client_id`) is an implementation-time filler for an L9-mandated claim the frozen brief never explicitly fixed.

**Severity:** Info / carried forward

**Evidence:** `apikey/ApiKeyTokenIssuer.java:69`, `artifacts/06-implementation-notes.md`.

**Recommendation:** Explicit ratification (or override) at the Phase 9 gate, not a code change by itself.

---

## Non-Issues Considered and Ruled Out

- **Header parsing null-safety** (`ApiKeyController.extractCredential`): every `substring` call is guarded by a preceding bounds check (`separator <= 0`, `separator == authorization.length() - 1`); no `IndexOutOfBoundsException` or `NullPointerException` is reachable for any input, including an empty string, a lone scheme with no space, or a trailing-space-only header.
- **Transaction boundary (D5):** confirmed `ApiKeyController.exchange` calls the `@Transactional` `ApiKeyService.exchange` through the Spring proxy (different class, no self-invocation), so that transaction commits before `ApiKeyTokenIssuer.issue` runs — matches D5 exactly, no wrapper added.
- **Module boundaries (L12):** `ApiKeyTokenIssuer`'s import of `com.themistra.auth.authz.RoleService` is a service-class import, not an entity import, and is already precedented by `TokenClaimsCustomizer`. No `apikey` class imports `PublicEndpoints` (the one Javadoc `{@link}` reference in `ApiKeyController` creates no bytecode dependency, confirmed by `ArchitectureTest` still passing).
- **Enumeration-safety / secret handling:** no code path logs or echoes the presented credential, its hash, or an email; `ApiKeyExceptionHandler` sets no `detail`.
- **Thread-safety:** `ApiKeyController`, `ApiKeyTokenIssuer`, `ApiKeyExceptionHandler` all hold only `final` fields; no shared mutable state.
- **`ApiKeyTokenIssuer.issue`'s claim assembly inlined rather than the Phase 5 plan's separate `buildClaims` helper** — organizational deviation only, no behavioral difference; the method is still short and single-purpose. Not raised as a formal finding.

---

**Phase 7 complete — self-review written.** Proceed to Phase 8 (Kimi Independent Review) on approval.
