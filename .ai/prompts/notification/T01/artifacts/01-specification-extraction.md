# notification · T01 · Phase 1 — Specification Extraction

## Business Rules

No individual R-numbered requirement targets this task specifically — like crypto-service's own T01,
this is a foundation/skeleton task, not a functional behavior. The governing text is `tasks.md`'s own
task 1 statement, verbatim.

## Locked Decisions

- **L2.** Consume-only at launch, no synchronous cross-service call — directly governs the task
  statement's own "no producer/outbox dependency at launch unless O5 is taken."
- **L10.** Secrets discipline — no email-transport credential, DB password, or key committed; validated
  `@ConfigurationProperties` fail startup on missing/invalid config in non-local profiles. Not
  implemented by this task (that's task 3), but the `validation` starter this task adds is the
  dependency that later enables it.
- **L11.** Module boundaries, package-by-feature, ArchUnit-enforced. Not implemented by this task
  (no packages exist yet), but the `archunit-junit5` test dependency this task adds is what later tasks
  build the actual boundary rule on top of.

L1, L3–L9 govern later tasks' own behavior (idempotency, delivery log, channels, preferences, retry,
in-app security, template versioning) — not implemented or constrained by a bare pom/skeleton.

## Files involved

**New, this task's own deliverable:**
- `services/notification/pom.xml` — does not exist yet (only `services/notification/README.md` is
  present today).
- A bare `@SpringBootApplication` class (e.g. `NotificationServiceApplication`), even though the task
  statement's own text only mentions the POM. **Required by hard-won precedent, not optional**:
  crypto-service's own T01 discovered that `mvn verify` fails at the `spring-boot-maven-plugin:repackage`
  goal ("Unable to find main class") when a pom declares that plugin but zero `@SpringBootApplication`
  classes exist — caught only because Phase 12 actually ran `mvn -pl services/crypto verify` instead of
  trusting `validate`/`test` alone. Adding the skeleton class in this same task avoids rediscovering that
  gap the hard way.

**Modified:**
- Root `pom.xml` — `<modules>` block, appending `services/notification` (currently lists only
  `services/auth`, `services/crypto`, in that order, with an explicit "dependency order" comment).

**Read-only, precedent to mirror exactly:**
- `services/auth/pom.xml` — the direct, named precedent for dependency selection and structure
  (resource-server-only subset, no issuer starter; JPA/Flyway/Postgres; Kafka; actuator/Prometheus;
  Testcontainers Postgres+Kafka; ArchUnit; Awaitility; the local-dev-only `flyway-maven-plugin` block).

**Not touched:** any file under `spec/`; `services/auth`, `services/crypto` source; `contracts/`.

## Dependencies

No new classes/services/repositories/entities (none exist yet in this module). Config keys: none bound
yet (task 3's own scope) — this task only adds the `validation` starter dependency that later enables
`@ConfigurationProperties` validation. Contracts: none consumed by this task itself (task 5/6/7's own
scope) — `archunit-junit5`/Testcontainers dependencies are added now so later tasks don't need a second
pom edit.

## Acceptance Criteria

1. **AC1.** Root `pom.xml`'s `<modules>` block includes `services/notification`, consistent with the
   existing "listed in dependency order" comment convention (no `libs/java/*` module exists yet for
   either sibling service, so this is purely an append, not a reordering).
2. **AC2.** `services/notification/pom.xml` exists, includes exactly the dependency set the task
   statement names (web, validation, resource-server, data-jpa, flyway, postgres, spring-kafka,
   actuator, prometheus, testcontainers, archunit, awaitility), excludes any producer/outbox-specific
   library, and includes the chosen email-transport client dependency — **the specific client is not
   yet decided** (Phase 0's own flagged gap: O2 has no `design.md`-recommended default, unlike O1/O3/O5).
   Phase 2 must make and justify a concrete choice.
3. **AC3.** A bare `NotificationServiceApplication` class exists, so `mvn -pl services/notification
   verify` succeeds through the `repackage` goal, not just `compile`/`test-compile`.
4. **AC4.** `mvn -pl services/notification -am verify` succeeds (compile + package; no tests exist yet
   to run, matching crypto's own T01 precedent where the skeleton task itself authors no test beyond the
   regression guards for its own ACs).

## Tests required

None from `package.md` §8 (all 19 named tests there target later functional tasks). Following
crypto-service's own T01 precedent, this task should still author its own narrow regression-guard tests
(mirroring `T01SkeletonRegressionTest`'s style) proving: the root pom registers the new module, the new
pom declares the required dependencies and excludes the issuer starter, and the skeleton class exists
and is annotated correctly — a "this content must be present/absent" scan, not a structural bytecode
analysis, since there is no production code yet for ArchUnit to analyze.

## Open Questions

1. **O2/Q2 (email transport vendor) has no recommended default and is a genuine blocker for this
   task's own AC2** — `design.md` §4b only says "propose SES vs SendGrid vs SMTP... recommend one,"
   unlike O1/O3/O5's explicit "Recommended: ..." text. Phase 2 must resolve this with a concrete,
   justified recommendation (not deferred to a later task), since the task statement itself requires
   adding "the chosen email-transport client" now. Not a blocker for Phase 1's own extraction, but the
   single most consequential decision Phase 2 needs to make.
2. **`contracts/events/payments/` does not exist yet** (Phase 0 finding — Payment Service hasn't been
   built). Not a blocker for this task (no contract is consumed by a bare pom/skeleton), but carried
   forward as a known, real gap for whichever future task in this list first needs it (task 7, task 15).
