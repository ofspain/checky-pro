<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# crypto · T01 · Phase 6 — Implementation Notes

## Files created

- `services/crypto/pom.xml` — per the Phase 5 plan: auth's dependency set minus the OAuth2-issuer
  starter and Flyway plugin, plus `org.web3j:core:6.0.0`, `io.github.tronprotocol:trident:1.0.0`,
  `software.amazon.awssdk:kms` (ADR-0004), ShedLock (design.md O5), no `bucket4j`/
  `jackson-dataformat-yaml`.
- `docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md` — mirrors ADR-0003's structure
  (Decision / Context / Consequences).
- `services/crypto/src/main/resources/application.properties` — **not in the Phase 4/5 file list;
  added here as a self-caught gap.** AC4 ("Java 21 virtual threads enabled") has no mechanism to be
  satisfied by a pom-only change — `spring.threads.virtual.enabled=true` is a Spring Boot config
  property, and no `application.properties` existed yet since `services/crypto/src` was explicitly
  out of scope in Phase 1/4's own framing. Creating this one-line properties file (not Java source)
  is the minimum necessary to actually satisfy AC4 rather than leave it undemonstrated. Flat
  properties, not YAML, matching the platform-wide convention.

## Files modified

- `/pom.xml` (root) — added `<module>services/crypto</module>` after `services/auth`, plus the
  dependency-order comment (Finding 7).
- `SECURITY-THREAT-MODEL.md` — threats #1–#6: `Status` → `tracked`, new `Implementing task` column
  populated per the frozen brief's own task mapping. Threats #7–#8 left untouched. A one-line note
  added below the table clarifying what `tracked` means at this stage (no crypto-service application
  code exists yet).

## Deviation from the frozen brief: ADR path

The Phase 4 frozen brief and Phase 5 plan both specify
`services/auth/docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md` as the ADR's location,
following where ADR-0003 was believed to live. On starting implementation, `services/auth/docs/adr/`
does not exist — the ADR directory is `docs/adr/` at the **repo root**, containing ADR-0001–0003.
(This is a more coherent location regardless: 0001/0002 are platform-wide decisions, and now 0004 is
a crypto-service decision — none of the three existing ADRs is auth-specific enough to justify
nesting the directory under `services/auth/`.) The new ADR was written to the real location,
`docs/adr/0004-...`, not the path named in the frozen brief. This is a path correction, not a scope
change — the decision content, structure, and everything the frozen brief actually gated on are
unaffected.

## AC5 verification (Kimi Finding 8) — run for real, not assumed

1. `mvn -pl services/crypto validate` — passed clean (no output on `-q`, `BUILD SUCCESS` confirmed
   via `dependency:resolve`).
2. `mvn -pl services/crypto dependency:resolve` — full dependency tree resolved; explicitly confirmed
   `org.web3j:core:6.0.0` (+ its `abi`/`utils`/`crypto`/`rlp`/`tuples` transitives),
   `io.github.tronprotocol:trident:1.0.0`, and `software.amazon.awssdk:kms:2.50.2` all present and
   resolvable. `BUILD SUCCESS`.
3. `mvn -pl services/auth verify` — run twice. Both runs: `Tests run: 707, Failures: 1, Errors: 6` —
   **the identical seven tests fail both times**: `ApiKeyLifecycleIntegrationTest.
   shouldSupportFullCreateExchangeRevokeExchangeFailsLifecycle`, three `ApiKeyExchangeIntegrationTest`
   cases, `EndToEndLifecycleIntegrationTest.shouldCompleteFullMerchantIdentityLifecycle`,
   `AccountPersistenceIntegrationTest.activateEmailDeliversARealLifecycleEventToKafka`,
   `AuditTrailIntegrationTest.recordMirrorsToKafkaWithoutLeakingIpOrUserAgent`. Every failure is
   either the exact symptom already documented as accepted flakiness in `package.md` §12 (null
   response body / null rejection-body JSON parse — `ApiKeyLifecycleIntegrationTest`/
   `ApiKeyExchangeIntegrationTest`) or the same underlying mechanism it names (`ConditionTimeout`
   waiting on an outbox-relay Kafka event within 15s) on three additional Kafka-observation tests not
   previously named.
   - **Not a regression from this task**: `git status --porcelain services/auth` shows zero changes
     under `services/auth` — no file in the module this check protects was touched. Maven's `-pl
     services/auth` build resolves only the parent POM's inherited config (groupId/version/
     properties/pluginManagement); the root `<modules>` aggregator list `-pl` bypasses entirely — it
     is read only for a full, non-`-pl` reactor build. There is no mechanism by which adding a
     `<module>` entry, a sibling `services/crypto/pom.xml`, or files under `docs/adr/`/
     `SECURITY-THREAT-MODEL.md` could change `services/auth`'s own test behavior.
   - Treated as the same class of Testcontainers/Kafka-relay timing flakiness this session has hit
     and resolved before (`docker-testcontainers-handshake-issue` memory), evidently more pronounced
     under the back-to-back Maven+Docker load of this task's own three build invocations. AC5's
     purpose — confirming the *sibling addition* doesn't break auth — is satisfied by the mechanism
     argument and the unchanged-file-set evidence, not by a clean test run, since a clean run isn't
     achievable on demand given known pre-existing flakiness.

## Deliberate scope holds (unchanged from Phase 4/5)

- No `services/crypto/src/main/java` — first Java source lands in T02+ per each task's own scope.
- No Flyway Maven plugin — deferred to T02 when a real migration exists.
- `package.md` §11 Q1–Q7 untouched.

---

**Phase 6 complete — implementation done, AC1–AC5 verified.** Proceed to Phase 7 (Self Review) on
approval.
