<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# crypto · T01 · Phase 5 — Implementation Plan

Four files: two new, two modified. No Java source. Plan specifies exact content per the frozen brief.

## Files to create

### `services/crypto/pom.xml`

Full content plan, based on a fresh read of `services/auth/pom.xml` (not memory):

- **Parent**: identical block (`com.themistra:checky-pro:0.0.1-SNAPSHOT`, `relativePath ../../pom.xml`).
- **artifactId/name**: `crypto-service`. **description**: reflects this service's actual purpose
  (blockchain verification + KMS attestation), not copied from auth's OIDC-issuer description.
- **properties**: `testcontainers.version = 1.21.4` — kept proactively. Auth's own comment explains
  this pins past a real Docker-API-handshake bug in Spring Boot 3.5.4's managed Testcontainers
  version; the same root cause applies to any service on the same Boot version, not an auth-specific
  fix.
- **dependencyManagement**: AWS KMS BOM (`software.amazon.awssdk:bom:2.50.2`, same version, `import`
  scope) — needed because KMS is included below.
- **dependencies, kept from auth (justified per-item, not blanket-copied)**:
  `spring-boot-starter-web`, `-validation` (baseline for any Spring Boot REST service);
  `spring-boot-starter-oauth2-resource-server` + `spring-security-oauth2-resource-server` **only**
  (Finding 2 — no `-authorization-server` starter, crypto never issues tokens);
  `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-database-postgresql`, `postgresql` (runtime)
  (same persistence stack, `chain` schema per `design.md` §4c);
  `spring-kafka` (outbox → Kafka, same as auth);
  `software.amazon.awssdk:kms` (Finding 3 — now commented with the new ADR-0004 reference, not
  auth's ADR-0003);
  `spring-boot-starter-actuator`, `micrometer-registry-prometheus` (runtime) (same observability
  baseline, `agents.md`'s own explicit requirement);
  `shedlock-spring` + `shedlock-provider-jdbc-template` (version `7.7.0`, matching auth's pinned
  version) — kept because `design.md` O5 explicitly names ShedLock-leased shards as one option for
  crypto's own multi-replica watcher assignment, a real, named near-term need for this service, not
  a blind copy.
- **dependencies, deliberately NOT carried from auth**: `com.bucket4j:bucket4j_jdk17-core` (auth's
  own R41 rate-limiting need; no rate-limiting requirement exists anywhere in
  `spec/crypto-service/`); `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` (auth added
  this specifically for its own T33 OpenAPI contract test — crypto's equivalent is T23, not T01; add
  it there, not preemptively here).
- **dependencies, new for this service**: `org.web3j:core:6.0.0` (verified live against Maven
  Central, Phase 4); `io.github.tronprotocol:trident:1.0.0` (verified live against Maven Central,
  Phase 4 — the current official Tron-maintained Java gRPC client).
- **test dependencies, kept**: `spring-boot-starter-test`, `spring-security-test` (still relevant —
  crypto's own resource-server JWT validation, R27, needs the same test support auth uses for its
  security config), `spring-boot-testcontainers`, Testcontainers `postgresql`/`kafka`/
  `junit-jupiter`, `archunit-junit5` (version `1.3.0`, L15's own enforcement mechanism), `awaitility`
  (version `4.2.2` — if anything, more central to crypto's own async watcher/reorg testing than to
  auth's).
- **build**: `spring-boot-maven-plugin` only. **No Flyway Maven plugin** (Finding 6 — deferred to
  T02, when a real migration exists to run it against).
- **finalName**: `crypto-service`.

### `services/auth/docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md`

Mirrors ADR-0003's own structure (context / decision / consequences), scoped to crypto-service's
`attest` module: KMS SDK use is confined to code that will live under
`com.themistra.crypto.attest` (no such code exists yet — this ADR authorizes the *pom-level*
dependency ahead of the module that will use it, matching how ADR-0003 itself was written before
`MfaSeedEncryption` existed). Cites L11 (`agents.md`) as the constraining decision this ADR
operationalizes, and notes the IAM-level backstop (`agents.md`: "only the Crypto Service role may
call `kms:Sign`") as the second, independent enforcement layer beyond the ArchUnit package-ban a
later task (T25) will add.

## Files to modify

### `/pom.xml` (root)

Add `<module>services/crypto</module>` immediately after `<module>services/auth</module>`, plus a
comment above the `<modules>` block noting: modules are listed in dependency order; `libs/java/*`
modules must precede any service that depends on them once populated (Finding 7).

### `SECURITY-THREAT-MODEL.md`

Threats #1–#6: `Status` column value changes from `designed` to `tracked`. New column
`Implementing task` added, values: #1 → T09 (quorum evaluator), #2 → T11 (token allowlist), #3 → T14
+ T18 (finality policies + reorg detector), #4 → T20 (KMS signer), #5 → T08 + T09 (observation log +
quorum decision persistence), #6 → T13 (address-poisoning detector). Threats #7/#8 (Payment/Auth
concerns) left untouched — out of this task's `#1–#6` scope.

## Public / private methods, entities, repositories, services

None — no Java code in this task.

## Tests required

None. AC5 (Kimi Finding 8) is a manual build verification, not a new test.

## Execution order

1. Verify `org.web3j:core:6.0.0` and `io.github.tronprotocol:trident:1.0.0` are still resolvable
   (re-confirm at implementation time, not just Phase 4's live check, in case either published a
   newer patch in the interim).
2. Write `services/crypto/pom.xml` per the plan above.
3. Write `services/auth/docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md`.
4. Edit root `pom.xml` — add the module entry + ordering comment.
5. Edit `SECURITY-THREAT-MODEL.md` — threats #1–#6.
6. `mvn -pl services/crypto validate` (or `compile`, since there's no source yet, `validate` is the
   meaningful check) — confirm the new pom itself is well-formed and its dependencies resolve.
7. `mvn -pl services/auth verify` — confirm the sibling module's own build is genuinely unaffected
   (AC5, Kimi Finding 8) — run for real, not assumed.

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
