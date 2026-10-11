# payment · T01 · Phase 10 — Test Generation (Test Manifest)

New file: `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`
(8 test methods, plain JUnit, no Spring context, no Docker). No production code was changed.

## Test → Acceptance Criterion mapping

| Test method | Verifies |
|---|---|
| `rootPomRegistersPaymentServiceAfterTheThreeExistingServicesWithOrderingComment` | AC1 |
| `paymentPomDeclaresTheExactFoundationDependencySet` | AC2 |
| `paymentPomExcludesKmsChainClientsAndDeferredDependencies` | AC3 |
| `applicationClassIsBareWithStandardMainMethod` | AC4 |
| `buildPluginsAndFinalNameAreRegistered` | AC5 |
| `sharedDependencyVersionsStayAlignedWithAuthService` | Phase 3 findings 2 & 9 (BOM/plugin version alignment with `services/auth/pom.xml`) |
| `s3ClientCanActuallyBeConstructed` | Phase 3 finding 8 (real client construction, not just dependency-tree resolution) |
| `noSecretOrCredentialStringOutsideTheDocumentedFlywayLocalDevCarveOut` | L13, with the one documented, frozen-brief-approved carve-out |

## Real bugs found and fixed while writing these tests (not hidden)

Two of the eight negative-proof assertions failed on first run, plus one latent failure that
would have surfaced next — not because the production poms were wrong, but because the pom's own
explanatory comments name the excluded coordinate verbatim for a human reader
(`"ShedLock (net.javacrumbs.shedlock:*) lands at task 8..."`, `"No <executions> binding..."`,
`"jackson-dataformat-yaml lands at task 24..."`), which a plain substring `doesNotContain` check
cannot distinguish from a real dependency/tag. Fixed by stripping XML comments
(`stripXmlComments`, a small regex helper) before the three negative-proof checks that scan for
excluded coordinates/tags, rather than diluting the pom's own documentation to dodge the test. Ran
the full suite after the fix: **8/8 pass.**

## Mutation check (non-vacuity proof)

Temporarily changed the AWS SDK BOM version in `services/payment/pom.xml` from `2.50.2` to
`2.50.1`, re-ran the suite: **2 failures**
(`paymentPomDeclaresTheExactFoundationDependencySet`, `sharedDependencyVersionsStayAlignedWithAuthService`),
confirming both the exact-version and alignment assertions catch a real regression rather than
passing vacuously. Reverted the mutation (`git status`/`git diff --stat` confirm the file is back
to its committed state); re-ran: **8/8 pass** again.

## Final state

`mvn -pl services/payment test` → `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
