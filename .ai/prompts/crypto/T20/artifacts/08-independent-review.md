<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review) -->

# crypto · T20 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T20 — KMS signer — single path |
| **Spec section** | Screening, attestation, key custody |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Verdict:** The implementation meets the frozen brief's acceptance criteria and the standing
rules in `agents.md`. The Phase 7 self-review's critical `@Autowired` finding is fixed and now
locked by a regression test. No logic bugs, security defects, or LOCKED-decision deviations were
found. Three low/medium findings are noted below: one architectural-rule precision issue, one
test-fragility issue, and one resource-lifecycle inconsistency with the cited precedent.

A targeted test run (`mvn -pl services/crypto test
-Dtest=KmsSignerTest,KmsSignerArchitectureTest,KmsSignerLocalStackIntegrationTest,KmsSignerSpringWiringTest`)
passed 11/11. The run emitted ArchUnit ASM warnings because the local JDK is Java 26 and the pinned
ArchUnit 1.3.0 bytecode parser targets lower class-file major versions; this is an environment
artifact that does not affect the Java 21 CI target.

---

## 1. ArchUnit KMS-SDK exception is package-agnostic

- **Issue:** `onlyKmsSignerMayUseTheKmsSigningSdk` allows *any* class whose simple name starts with
  `KmsSigner` anywhere in `com.themistra.crypto` to depend on the KMS SDK. The frozen brief Phase 3
  Finding #2 disposition and the Scope section require that **only `attest.KmsSigner` itself** may
  call `KmsClient.sign(...)` or construct a `SignRequest`. A future class named, for example,
  `com.themistra.crypto.watch.KmsSignerWatcher` could import `KmsClient`/`SignRequest` without
  tripping the current rule, even though it lives outside `attest` and is not the single approved
  signer. The first ArchUnit rule (`noClassOutsideAttestMayReferenceKmsSigner`) blocks depending on
  `KmsSigner` itself, but it does not block a direct KMS SDK dependency.
- **Severity:** Medium (the security goal is still enforced by human review of new class names, but
  the structural guarantee is weaker than the brief specifies)
- **Evidence:** `attest/KmsSignerArchitectureTest.java:58-65` — the predicate is
  `haveSimpleNameNotStartingWith("KmsSigner")` with no `resideInAPackage(...)` constraint.
- **Recommendation:** Tighten the predicate so the `KmsSigner*` exception applies only inside
  `com.themistra.crypto.attest..`. For example:
  ```java
  noClasses()
      .that().haveSimpleNameNotStartingWith("KmsSigner")
          .or().resideOutsideOfPackage("com.themistra.crypto.attest..")
      .should().dependOnClassesThat().resideInAPackage("software.amazon.awssdk.services.kms..")
  ```
  This still allows `KmsSignerTest` and `KmsSignerLocalStackIntegrationTest` (both in `attest` and
  named `KmsSigner*`) while closing the loophole for a `KmsSigner*` class outside `attest`.
- **Confidence:** High.

## 2. Structural source-scan tests assume the module working directory

- **Issue:** `KmsSignerTest` reads `KmsSigner.java` via `Path.of("src/main/java/...")`, which is
  relative to the current working directory. Maven Surefire defaults the CWD to the module base
  directory (`services/crypto`), so the tests pass in the standard build. If a developer or an
  alternative runner executes the tests from the repo root or any other directory, the files will
  not be found and the tests will fail. This is the same fragility as any source-relative test.
- **Severity:** Low
- **Evidence:** `attest/KmsSignerTest.java:125-126` and `157-162`.
- **Recommendation:** Resolve the source path relative to the test class location instead of the
  process CWD, or read the source from the classpath. For example:
  ```java
  Path moduleRoot = Path.of(KmsSignerTest.class.getProtectionDomain()
          .getCodeSource().getLocation().toURI())
      .resolve("../..").toAbsolutePath().normalize();
  Path source = moduleRoot.resolve("src/main/java/com/themistra/crypto/attest/KmsSigner.java");
  ```
  This keeps the tests green regardless of where they are launched from.
- **Confidence:** Medium.

## 3. `@AnalyzeClasses` package and canary import package are independent string literals

- **Issue:** The package scanned by the `@AnalyzeClasses` annotation
  (`"com.themistra.crypto"`) and the package imported by the plain-`@Test` canary are written as
  two identical string literals. If one is ever changed without the other, the `@ArchTest` fields
  and the canary would silently check different class sets. This is exactly the drift risk the auth
  service's `ArchitectureTest` addresses with a shared `ANALYZED_PACKAGE` constant (Kimi Phase 11
  Gap 1).
- **Severity:** Low
- **Evidence:** `attest/KmsSignerArchitectureTest.java:48` and `69-70`.
- **Recommendation:** Extract a `static final String ANALYZED_PACKAGE = "com.themistra.crypto"` and
  reference it in both places. `@AnalyzeClasses(packages = ANALYZED_PACKAGE)` is valid because the
  attribute accepts a compile-time constant string array.
- **Confidence:** Medium.

## 4. `KmsSigner` never closes its `KmsClient`

- **Issue:** `KmsSigner` builds a real `KmsClient` in its public constructor and holds it for the
  lifetime of the Spring singleton, but it does not implement `DisposableBean` or otherwise close
  the client on context shutdown. AWS SDK v2 clients own connection pools and I/O resources; the
  SDK documentation recommends closing them when no longer needed. The cited precedent
  `services/auth/.../MfaSeedEncryption` implements `DisposableBean` and closes its `KmsClient` in
  `destroy()`. `ObservationSnapshotStoreConfig`/`ObservationSnapshotStore` take a different path and
  do not close their `S3Client`, so the codebase has mixed precedent, but the class explicitly
  modelled on `MfaSeedEncryption` should follow its lifecycle pattern too.
- **Severity:** Low
- **Evidence:** `attest/KmsSigner.java:68-96` — no `implements DisposableBean`, no `close()` call.
  Contrast with `services/auth/src/main/java/com/themistra/auth/mfa/MfaSeedEncryption.java:120-125`.
- **Recommendation:** Make `KmsSigner` implement `DisposableBean` and close `kmsClient` in
  `destroy()`. The test-seam constructor should still accept the client as-is; unit/integration tests
  can either ignore the lifecycle or reuse the same client across tests (as the LocalStack test
  already does).
  ```java
  @Override
  public void destroy() {
      kmsClient.close();
  }
  ```
- **Confidence:** Medium.

---

## Confirmed dispositions (no new findings)

- **Phase 7 critical finding (`@Autowired` missing)** is fixed and regression-tested in
  `KmsSignerSpringWiringTest`. Confirmed by direct test execution.
- **`SignatureResult` package-private visibility** is correct and is enforced by the Java compiler;
  no additional ArchUnit rule is required by the frozen brief (matches self-review Finding #5).
- **No `Logger` field, no key-material API imports, no host-only literals** in `KmsSigner` are
  confirmed by the structural tests.
- **LocalStack integration test** is present, verifies a real asymmetric `Sign` round-trip, and
  empirically confirms that `kmsKeyId()` comes from the KMS response rather than the configured
  input value.
- **Phase 6 redesign (folding `KmsSignerConfig` into `KmsSigner`)** is well-justified: a separate
  config class would itself need to depend on `KmsClient` and would trip the very ArchUnit rule this
  task exists to enforce.
