<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T25 · Phase 12 — Specification Verification

Compares the final implementation and tests (Phases 6–11) against `requirements.md`, `design.md`, `tasks.md`, and the frozen brief for **T25 only**. `spec/auth-service/` confirmed byte-for-byte unchanged since T25 began (`git diff 4a1abea...HEAD -- spec/auth-service/` — empty).

---

## Traceability Matrix — Requirements

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R31** — valid key → 10-min JWT, `sub`=account UUID, `scope`⊇`merchant.api`, `amr`∋`api_key` | Yes | `ApiKeyController.java:47-55`, `ApiKeyTokenIssuer.java:62-92` | `ApiKeyTokenIssuerTest.issueProducesTheFullL9ClaimSet`; `ApiKeyExchangeIntegrationTest.shouldExchangeValidApiKeyForMerchantJwt` (named, written, unexecuted — Docker) | No | No |
| **R32** — successful exchange updates `last_used_at` | Yes (pre-existing, T24-frozen: `ApiKeyService.java:196`) | `ApiKeyService.java:196` (unmodified by T25) | `ApiKeyExchangeIntegrationTest.lastUsedAtWrittenOnSuccessNeverOnRejection` (written, unexecuted) | No | No |
| **R33** — revoked/expired/malformed/hash-mismatched → uniform 401 | Yes | `ApiKeyExceptionHandler.java:22-27` maps `ApiKeyExchangeRejectedException`; `ApiKeyController.java:74-90` normalizes every header-level cause to the same single call | `ApiKeyExceptionHandlerTest` (2, executed, green); `ApiKeyControllerTest` (11 header-parsing tests, executed, green); `ApiKeyExchangeIntegrationTest.shouldRejectRevokedOrUnknownApiKeyWithUniform401` + `.missingWrongSchemeBlankAndOverLengthCredentialAllUniform401` (written, unexecuted) | No | **Yes, documented**: a genuinely `Bearer`-schemed request never reaches this uniform path at all — see Constraints/Deviations below |
| **R43** — every attempt audited, row + one outbox mirror | Yes (pre-existing `AuditService`/`ApiKeyService` calls, unmodified) | `ApiKeyService.java:158,166-167,192,198` (T24-frozen) | `ApiKeyExchangeIntegrationTest.auditRecordsOneSuccessRowAndOneOutboxMirrorOnSuccess` / `.auditRecordsOneFailureRowAndOneOutboxMirrorPerRejection` (written, unexecuted) | No | No |
| **R46** — 401 is `application/problem+json`, no stack trace/internal detail/existence hint | Yes | `ApiKeyExceptionHandler.java:22-27` (no `setDetail` call) | `ApiKeyExceptionHandlerTest.onExchangeRejectedReturnsUniform401` (asserts `detail`/`instance`/`properties` all null — executed, green) | No | No |
| **R48** — no PII beyond `email_verified` | Yes | `ApiKeyTokenIssuer.java:86` (`email_verified` claim only; no `email`/`name` claim ever added) | `ApiKeyTokenIssuerTest.issueProducesTheFullL9ClaimSet` (asserts `email`/`name` absent, exact 13-key claim set — executed, green) | No | No |

## Traceability Matrix — Locked Decisions

| Decision | Honored? | Evidence | Test? | Deviation? |
|---|---|---|---|---|
| **L8** — 10-min RS256 JWT, full claim list | Yes, claim contract honored | `ApiKeyTokenIssuer.java:73-87` | `ApiKeyTokenIssuerTest` (executed) | **Documented, gate-approved**: assembly path is `ApiKeyTokenIssuer` directly, not `TokenClaimsCustomizer` (D1, frozen brief) |
| **L11** — `POST /api-keys/token` public, in `PublicEndpoints.java` | Yes | `PublicEndpoints.java:34` | `ApiKeyExchangeIntegrationTest.publicEndpointsRegistersApiKeysTokenAsPostOnly` (written, unexecuted) | No |
| **L7** — key format, SHA-256 at rest, never echoed | Yes (T24-frozen, unmodified) | `ApiKeyHasher.java`, `ApiKeyService.java` | `ApiKeyHasherTest` (executed, green) | No |
| **L9** — exact claim set, no email/name | Yes | `ApiKeyTokenIssuer.java:73-87` | `ApiKeyTokenIssuerTest.issueProducesTheFullL9ClaimSet` asserts `claims.getClaims().keySet()` exactly (executed, green) | No |
| **L12** — no cross-module entity imports | Yes | `ApiKeyTokenIssuer` imports `RoleService` (a service, not an entity); no `apikey` class imports `PublicEndpoints` outside a Javadoc `{@link}` (no bytecode dependency) | `ArchitectureTest` (pre-existing rule `only_token_module_references_public_endpoints`; not independently re-verified against Testcontainers this session, but confirmed by direct code inspection — the rule doesn't require a DB, only classpath analysis, and compiles/loads cleanly) | No |
| **L13** — no secret/key/signing material committed or logged | Yes | No `log.*` call anywhere in `apikey/ApiKeyController.java`, `ApiKeyTokenIssuer.java`, `ApiKeyExceptionHandler.java` | N/A (absence of logging, verified by inspection) | No |
| **L1** — no DDL in T25 | Yes | No new Flyway migration file created; `git diff` confirms zero changes under any `V*__*.sql` | N/A | No |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 | **Met** | `ApiKeyController.java:47`, public via `PublicEndpoints.java:34`; reachability + the Phase 9 CSRF fix proven by `ApiKeyExchangeIntegrationTest.reachableAnonymouslyAndApiKeySchemeAvoidsTheBearerFilter` (written, unexecuted) |
| AC2 | **Met** | `PublicEndpoints.java:34` — single new entry, POST-scoped |
| AC3 | **Met** | `JwksConfig.java:55-60` (RS256 default header, `JwksConfigTest` proves signing succeeds and picks the CURRENT key even with two keys present — executed, green) |
| AC4 | **Met** | `ApiKeyTokenIssuer.java:75` (`subject(accountUuid.toString())`) |
| AC5 | **Met** | `ApiKeyTokenIssuer.java:81` (`scope` as `List` → JSON array); `issueEchoesScopesVerbatimAsAJsonArray` (executed, green) |
| AC6 | **Met** | `ApiKeyTokenIssuer.java:84-85` |
| AC7 | **Met** | `ApiKeyTokenIssuer.java:70-71,90`; TTL arithmetic + non-default-TTL coverage in `ApiKeyTokenIssuerTest` (executed, green) |
| AC8 | **Met** | `ApiKeyTokenIssuer.java:82-83`; `RoleService.resolveEffectiveRoles` called fresh every time (`issueResolvesRolesFreshOnEveryCall`, executed, green); claim set bounded exactly to L9, no PII beyond `email_verified` |
| AC9 | **Met** | T24-frozen `ApiKeyService.java:196`, unmodified |
| AC10 | **Met, with one documented, unavoidable residual** — see Deviations below |
| AC11 | **Met** | `ApiKeyTokenResponse.java` carries only token/type/TTL; `responseEnvelopeHasExactlyTheThreeExpectedFieldsAndNoSecretMaterial` also checks `keyUuid` absence (written, unexecuted) |
| AC12 | **Met** | Single audit-writing code path (`ApiKeyController.java:74-90` routes every rejection through `ApiKeyService.exchange`); dedicated audit/outbox-count tests (written, unexecuted) |
| AC13 | **Met** | No migration added |
| AC14 | **Met** | No `apikey` class imports `PublicEndpoints` or a foreign entity (confirmed by inspection; `ArchitectureTest` itself not re-run this session — Docker) |
| AC15 | **Met** | `ApiKeyController.java:38-45` (no local catch around `issue`); `ApiKeyControllerTest.signingFailurePropagatesUncaught` (executed, green) proves the controller doesn't swallow it; the resulting 500-via-`ApiExceptionHandler.onUnexpected` path itself is not exercised by an executed test this session (would require either a live signing failure or a Docker-backed bean override) |

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes, for everything within T25's authorized scope. All production code and tests specified by the frozen brief and Phase 5 plan are written, reviewed (self-review + independent Kimi review, both resolved), and re-tested after every fix. The one category of incompleteness is environmental, not scope-related: this sandbox has no working Docker daemon, so every Testcontainers-backed test (`ApiKeyExchangeIntegrationTest`, and the pre-existing `ApiKeyServiceIntegrationTest`/`SasLoginIntegrationTest`/`ArchitectureTest` regression suite) is written and compiles cleanly but has never actually executed against real Postgres/Kafka or a real running filter chain. This is the same class of residual risk this pipeline has carried and explicitly accepted since T15/T16/T17/T20.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC15 all have direct code evidence, per the matrix above. AC10 and AC15 carry a documented nuance each (below), not a failure to satisfy the criterion's substance.

**(3) Does it violate any LOCKED decision?** No unauthorized violation. Two decisions were touched with explicit, on-the-record human gate approval rather than silently: **D1's assembly-path deviation from L8's literal wording** (`TokenClaimsCustomizer` path → `ApiKeyTokenIssuer` direct assembly, approved at Phase 4) and **a one-line exception to `SecurityChainsConfig.java`'s file freeze** (the CSRF ignore-list addition, approved at the Phase 9 gate after the finding was verified against this codebase's own test evidence, not just general framework theory). Both are recorded in their respective phase artifacts with reasoning, not treated as free passes for anything beyond the specific line each gate approved.

**(4) Remaining risks:**
- **Unexecuted integration suite (highest-priority residual).** Nothing Testcontainers-backed has run this session. Two things specifically need confirming the moment Docker is available: `SasLoginIntegrationTest` (proves the Phase 9 `jwkSelector` fix doesn't disturb any existing SAS grant), and `ApiKeyExchangeIntegrationTest` itself (the two named tests, audit/outbox counts, and the CSRF/D4 filter-chain regression all depend on a real server and real Postgres).
- **`Bearer`-scheme 401 is not byte-identical to the uniform body** (AC10/R33 nuance). A `Bearer`-schemed request is intercepted by the pre-existing `BearerTokenAuthenticationFilter` before `ApiKeyController` ever runs, producing a 401 with no JSON body — status-uniform, not byte-uniform. This is unavoidable without modifying frozen `SecurityChainsConfig` further, already disclosed at Phase 6/7/8/9, and the corresponding test (`reachableAnonymouslyAndApiKeySchemeAvoidsTheBearerFilter`) asserts status-only equivalence for exactly this reason.
- **The four sibling public POST endpoints remain exposed to the same CSRF gap T25 fixed for itself.** `/accounts`, `/accounts/verify-email`, `/accounts/resend-verification`, `/accounts/password-reset-request`, `/accounts/password-reset` are still not CSRF-exempt and have never been tested through the real filter chain. femi explicitly chose to scope the Phase 9 fix to T25's own endpoint only; this is a known, named, not-yet-ticketed gap for a follow-up task, not an oversight.
- **`aud` = `client_id` = `"checky-api-key"` is a filler value**, not one the frozen brief specified. Ratified at Phase 9 as reasonable, but if a downstream resource server ever validates `aud` against a specific expected audience, this value should be revisited then.
- **AC15's actual 500 path (signing failure → `ApiExceptionHandler.onUnexpected`) is proven only at the unit level** (the controller doesn't swallow the exception); no executed test drives a real signing failure through to an actual HTTP 500. Kimi (Phase 11) rated this low-priority/acceptable given how hard a genuine signing failure is to simulate without bean overriding; not blocking.

---

## Verdict

**PASS** — every requirement, LOCKED decision, and acceptance criterion has direct code evidence and either an executed passing test or a written-but-Docker-blocked test with a clear, honest account of why it hasn't run; the only gaps are pre-existing/disclosed environmental limitations and explicitly out-of-scope follow-up items, not defects in T25's own delivered work.

---

**Phase 12 complete — PASS.** Proceed to Phase 13 (PR / Commit Preparation).
