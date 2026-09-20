# crypto · T04 · Phase 7 — Self Review

Reviewed the Phase 6 diff (`common/ClockConfig.java`, `events/*.java`,
`V3__crypto_app_outbox_grant.sql`, `application.properties`, `CryptoServiceApplication.java`)
against the frozen brief and `agents.md`. No rewrites performed — findings only, fixes are Phase 9.

---

## Finding 1 — `spring.jpa.hibernate.ddl-auto` (and `open-in-view`) were never configured, and `OutboxEvent` is the first JPA entity in this service

**Issue:** `services/crypto/src/main/resources/application.properties` has no
`spring.jpa.hibernate.ddl-auto` or `spring.jpa.open-in-view` setting anywhere. Every prior crypto
task (T01–T03) had zero `@Entity` classes, so this omission was inert until now — `OutboxEvent` is
the first entity this service has ever mapped, making this the first point where the gap has any
practical consequence. `services/auth` explicitly sets `spring.jpa.hibernate.ddl-auto=validate` (a
safe, read-only startup check that Hibernate's entity mapping matches the real Flyway-owned schema)
and `spring.jpa.open-in-view=false`. Without an explicit value, Spring Boot falls back to its own
computed default (`none` for a non-embedded database like Postgres) — which happens to be scan, not
"validate," meaning a mapping mistake (e.g. a typo'd `@Column(name=...)`) would surface only as a
runtime SQL error on first actual use, not a clean failure at boot.

**Severity:** Medium — not a correctness bug in the mapping itself (which was checked by hand against
`V1__chain_baseline.sql`'s `outbox` column list and appears correct), but a real gap in this task's
own "validated, fail-fast" spirit that every other config surface in this service (T03's
`@ConfigurationProperties`, T02's Flyway-owns-schema discipline) has consistently pursued.

**Evidence:**
- `services/crypto/src/main/resources/application.properties` — no `spring.jpa.*` property exists
  anywhere in the file (confirmed via direct grep).
- `services/auth/src/main/resources/application.properties:30-32` — explicit
  `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.properties.hibernate.default_schema=auth`,
  `spring.jpa.open-in-view=false`.
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxEvent.java` — the first `@Entity`
  anywhere under `services/crypto/src/main/java`.

**Recommendation:** Add `spring.jpa.hibernate.ddl-auto=validate` and `spring.jpa.open-in-view=false`
to `application.properties`, matching auth's precedent. (Crypto already sets its schema via
`spring.datasource.hikari.connection-init-sql=SET search_path TO chain, public` rather than
`hibernate.default_schema` — that T02-established mechanism doesn't need to change, only the two
missing properties above.)

---

## Finding 2 — `OutboxRelay` is the first code in this service to combine a blocking call with a `@Scheduled` virtual thread, exactly the scenario T01's design-challenge gate deferred as "revisit-if-regressed"

**Issue:** `application.properties:6` (`spring.threads.virtual.enabled=true`) was accepted at T01's
own Phase 4 gate with an explicit caveat: "the risk of carrier-thread pinning elsewhere (JDBC, KMS
calls) was accepted... as a revisit-if-regressed item, not a blocker." `OutboxRelay.relayOne`
(`OutboxRelay.java:66-72`) is the first code in this service that actually does both a blocking
network call (`kafkaTemplate.send(...).get()`) and a JDBC write (`repository.save(event)`) inside a
method that Spring Boot 3.2+'s virtual-thread scheduling infrastructure will run on a virtual thread
by default when that property is set. This isn't a new defect this task introduced — it's the first
real trigger of a risk the team already knowingly accepted — but it's worth surfacing explicitly now
that the trigger condition actually exists, rather than letting it go unnoticed until a real
production symptom appears.

**Severity:** Low-Medium (informational) — not something this task's own scope requires fixing;
flagging so it's visible to whoever is watching for the "regression" signal T01 anticipated.

**Evidence:**
- `services/crypto/src/main/resources/application.properties:3-6` (T01's own comment, carried
  forward unchanged through T02/T03/T04).
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxRelay.java:66-69` — `.get()`
  (blocking) immediately followed by `repository.save(event)` (JDBC), inside `relayOne`, called from
  the `@Scheduled` `relay()` method.

**Recommendation:** No action required for this task. Worth a one-line note if/when this service's
own performance or thread-dump monitoring is set up, so a carrier-thread-pinning symptom (if one ever
appears) is traced back to this documented, pre-accepted risk rather than investigated as a surprise.

---

## Not flagged (checked and found correct)

- `fixedDelayString` (not `fixedRate`) on `OutboxRelay.relay()` — confirmed this guarantees
  non-overlapping executions per replica (Spring reschedules only after the previous run completes),
  so no intra-process overlapping-invocation race exists, independent of the virtual-thread question
  in Finding 2.
- `KafkaProducerConfig`'s explicit `ProducerFactory`/`KafkaTemplate` beans correctly suppress Spring
  Boot's own autoconfigured `KafkaTemplate<Object,Object>` bean via `@ConditionalOnMissingBean`
  (type-matched, not name-matched) — no bean-definition conflict, and the generic-type ambiguity
  amendment #3 was accepted to avoid is genuinely resolved, not just cosmetically avoided.
- `V3__crypto_app_outbox_grant.sql`'s claim that `outbox`'s identity sequence is already covered by
  `V2`'s schema-wide `GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA chain` — verified: `V1` (which
  implicitly creates `outbox`'s identity sequence) runs before `V2`, so the sequence exists by the
  time `V2`'s schema-wide grant executes. No new sequence grant needed.
- Null-safety, module boundaries (L15 — `ClockConfig` in `common/`, everything else in `events/`),
  and the five `Objects.requireNonNull` calls in `OutboxPublisher.publish(...)` — all as designed,
  nothing to flag.
- `mvn -pl services/crypto -am compile` and `test-compile` both clean, no warnings.
