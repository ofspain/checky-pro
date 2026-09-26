# notification · T01 · Phase 7 — Self-Review

## Files reviewed

- `pom.xml` (root)
- `services/notification/pom.xml`
- `services/notification/src/main/java/com/themistra/notification/NotificationServiceApplication.java`
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`

## Findings and dispositions

### 1. `postgresql` artifactId ambiguity in the dependency-presence test — fixed

`<artifactId>postgresql</artifactId>` appears twice in the pom (the runtime JDBC driver,
`org.postgresql:postgresql`, and the Testcontainers module, `org.testcontainers:postgresql`). The
original `notificationPomDeclaresTheRequiredDependenciesAndExcludesTheIssuerStarter` checked only the
bare artifactId string once via `.contains(...)` — a mutation removing either one specifically (while
the other remained) would not have failed this test. Added a `hasGroupAndArtifact` helper checking each
occurrence by its full groupId+artifactId pair. Re-ran: still 6/6 passing (confirms the fix didn't
change behavior for already-correct content, only tightens what a future regression would need to slip
past).

### 2. Root `pom.xml`, `services/notification/pom.xml`, `NotificationServiceApplication.java` — re-read
   in full, no further finding

All three match Phase 5's plan exactly. The Javadoc-vs-negative-assertion issue found and fixed during
Phase 6 itself (not re-flagged here) remains correctly resolved.

## Verification performed

- `mvn -pl services/notification -am test -Dtest=T01SkeletonRegressionTest` (re-run after the Finding #1
  fix) — 6/6 passing.
- `mvn -pl services/notification -am verify` — clean, same result as Phase 6's own final record.
