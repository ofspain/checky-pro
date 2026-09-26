# notification · T01 · Phase 6 — Implementation Notes

## Files created

- `services/notification/pom.xml`
- `services/notification/src/main/java/com/themistra/notification/NotificationServiceApplication.java`
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`

## Files modified

- Root `pom.xml` — `<modules>` block, `services/notification` appended.

## What ran, in order, and what the real results were

1. Applied the root `pom.xml` edit.
2. Created `services/notification/pom.xml` exactly as pinned in Phase 5.
3. `mvn -pl services/notification -am dependency:resolve` — clean, every declared artifact resolved.
4. **Finding #4 settled empirically, before any Java was written:** `mvn -pl services/notification
   dependency:tree` shows `sesv2` resolves both `software.amazon.awssdk:apache5-client` and
   `software.amazon.awssdk:netty-nio-client` transitively (both `runtime` scope) — a working HTTP
   client is on the classpath with zero explicit declaration, exactly matching the sibling services'
   own `kms`/`s3` usage under the identical BOM version. No explicit HTTP-client artifact was added.
5. Created `NotificationServiceApplication.java` exactly as pinned.
6. `mvn -pl services/notification -am verify` — succeeded through `package` and
   `spring-boot:repackage`, confirming the exact gap crypto-service's own T01 hit does not recur here.
7. Created `T01SkeletonRegressionTest.java` per Phase 5's design.
8. First run: **5/6 passing, 1 failure** — a real, self-inflicted bug, not a design gap. The new
   `applicationClassIsBareWithOnlyTheMainMethod` test's negative assertions (`doesNotContain
   ("@ConfigurationPropertiesScan")` etc.) failed because the Application class's own Javadoc
   explains, in prose, why those annotations aren't present yet — and in doing so, names them,
   tripping a naive whole-file substring check against its own explanatory comment. Fixed by scoping
   the negative assertions to the code after the class-level Javadoc (`replaceFirst("(?s)/\\*\\*.*?\\*/", "")`),
   not the whole file. Re-ran: 6/6 passing.
9. `mvn -pl services/notification -am verify` (final) — 6 tests, 0 failures, clean repackage.
10. `mvn -q compile` (full reactor, all three modules) — clean, confirming the root pom edit didn't
    disturb `services/auth` or `services/crypto`.

## Disposition summary

| Check | Result |
|---|---|
| Root `pom.xml` registers `services/notification` | Done, after both existing modules |
| `services/notification/pom.xml` — exact dependency set | Done, all resolve cleanly |
| `sesv2`'s HTTP-client concern (Kimi Finding #4) | **Settled empirically: no explicit dependency needed** — verified via `dependency:tree` and a real `SesV2Client` construction test |
| `NotificationServiceApplication` — bare, real `main` method | Done, `mvn verify` succeeds through `repackage` |
| `T01SkeletonRegressionTest` | 6/6 passing, including one self-inflicted bug found and fixed during this same phase |
| Full reactor (auth + crypto + notification) | Compiles cleanly |
