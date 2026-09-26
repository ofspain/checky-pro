# notification · T01 · Phase 0 — Repository Understanding

## 1. Architecture summary

`notification-service` is Themistra's fan-out layer (`ARCHITECTURE.md` §3.5): a purely **consuming**
Kafka service — no domain state of its own, no synchronous cross-service calls on the delivery path
(L2). It turns domain events into user-facing messages over email and in-app (SSE/websocket) at
launch, resolves each recipient's channel preferences (L6), and records every delivery attempt in an
append-only, dispute-grade delivery log (L3) — the evidence for "was the merchant notified?" in later
dispute resolution. It owns the `notifications` schema.

Six feature modules per `design.md` §6: `consumer` (idempotent Kafka listeners), `preference`
(per-account channel opt-outs), `template` (versioned per event+channel), `delivery` (the log +
orchestrator + bounded retry), `channel` (email + in-app behind one `NotificationChannel` interface),
`inapp` (the SSE stream + unread-read API), plus `common` for shared plumbing (config, security, error
handling) — the same package-by-feature + ArchUnit-boundary shape as `crypto-service`.

## 2. Existing code this task touches

**Nothing exists yet.** `services/notification/` currently contains only a `README.md` — no `pom.xml`,
no source, no tests. This is a from-scratch skeleton task, structurally identical to crypto-service's
own T01: add the module to the root reactor and author its `pom.xml`.

The root `pom.xml`'s `<modules>` block currently lists only `services/auth` and `services/crypto`, in
that order with an explicit "listed in dependency order" comment — `services/notification` needs to be
appended.

## 3. Established patterns to follow

`services/auth/pom.xml` is the direct precedent T01's own task statement names. Read in full. Relevant
shape for notification (per T01's own explicit inclusion/exclusion list):

- **Include:** `spring-boot-starter-web`, `-validation`, `-oauth2-resource-server` +
  `spring-security-oauth2-resource-server` (resource-server only — no issuer starter, matching
  crypto-service's own precedent, not auth's, since this service never issues tokens), `-data-jpa`,
  `flyway-core` + `flyway-database-postgresql`, `postgresql` (runtime), `spring-kafka`, `-actuator`,
  `micrometer-registry-prometheus` (runtime), `spring-boot-testcontainers` + `testcontainers:postgresql`
  + `testcontainers:kafka` + `testcontainers:junit-jupiter`, `archunit-junit5`, `awaitility`.
- **Include, new for this service:** the chosen email-transport client (O2/Q2 — see Known
  gaps/unknowns below; this is genuinely unresolved as of Phase 0, unlike crypto's own T01 which had a
  concrete answer for its own chain-client dependencies from the start).
- **Exclude (per T01's own text):** any outbox/producer library — this service is consume-only at
  launch (L2); no published-event contract exists to produce against unless O5 is later taken. auth's
  own pom has no distinct "outbox" dependency either (outbox is a table+relay pattern, not a library),
  so this exclusion is mostly about not adding speculative producer-side tooling, not skipping a real
  dependency.
- ShedLock (`shedlock-spring` + `shedlock-provider-jdbc-template`) will be needed later (Task 14, bounded
  retry scheduling) — auth's own pom already includes it for its own scheduled cleanup job, same version
  (7.7.0). Not required by T01 itself (no scheduled job yet), but worth citing now since T01 mirrors
  auth's dependency set structurally.
- `flyway-maven-plugin` (local-dev-only, no `<executions>` binding) with `<schemas>notifications</schemas>`,
  mirroring auth's/crypto's identical block exactly.

## 4. Testing conventions

Unit (plain JUnit, fixed `Clock`, capturing fake transport — no real email in CI) → ArchUnit + contract
→ integration (Testcontainers: Postgres + Kafka). Contract tests validate consumed payloads against
`contracts/events/{auth,payments}/*` — deserialization models are meant to be generated from
`contracts/`, never hand-written (agents.md's own standing rule, identical wording to crypto's).

## 5. Known gaps / unknowns

- **`contracts/events/payments/` does not exist.** Verified directly: `contracts/events/` currently has
  only `auth/` (three real schemas: `user-lifecycle.v1.schema.json`, `email-requested.v1.schema.json`,
  `security-audit.v1.schema.json`) and `chain/` (five crypto-service event schemas). No `payments/`
  directory, meaning the Payment Service itself hasn't been built and none of `payments.invoice.created`
  / `payment.seen` / `payment.finalized` / `receipt.issued`'s real schemas exist anywhere in this repo
  yet. This doesn't block T01 (pom/skeleton only), but it is a real, load-bearing gap for this task
  list's own Task 7 ("Payment event consumer") and Task 15 (contract tests against those same schemas)
  — I do not know whether those tasks are meant to proceed against a locally-authored placeholder schema,
  wait for the Payment Service to exist, or be scoped differently than `tasks.md` currently describes.
  Not this task's own problem to resolve, but worth surfacing now rather than silently discovering it
  mid-pipeline.
- **O2 (email transport vendor) has no recommended default**, unlike O1/O3/O5 which each have an
  explicit "Recommended: ..." in `design.md` §4b. Q2/O2 just say "propose SES vs SendGrid vs SMTP...
  recommend one." T01's own task statement requires adding "the chosen email-transport client" to the
  new `pom.xml` — meaning T01 cannot fully complete its own acceptance criteria without this decision
  being made first, unlike a typical "propose in a later task" open question. This is the single most
  consequential open question for this specific task, and Phase 1/2 will need to make and justify a
  concrete choice (or defer the dependency addition to a later task and flag that as a deviation) rather
  than silently pick one.
- Q1 (recipient contact resolution), Q3 (SSE vs WebSocket), Q4 (link base URL), Q7 (event→recipient
  matrix) all have `design.md`-recommended defaults or are scoped to later tasks (5, 13, 9, 7
  respectively) — none block T01 itself.
