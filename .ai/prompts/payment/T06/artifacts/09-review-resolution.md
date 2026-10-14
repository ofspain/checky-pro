# payment · T06 · Phase 9 — Review Resolution

Human Approval gate. Decision made by the human (approved 2026-10-14); this artifact records it.

## Finding 1 — Missing Flyway grant migration for `invoices` and `outbox`

**ACCEPTED and FIXED.** Added `V3__payment_app_invoice_and_outbox_grants.sql`: `INSERT, SELECT`
on `invoices` (no `UPDATE` yet — no task in this service's own scope updates an invoice row
until task 8's expiry sweep); `INSERT, SELECT, UPDATE` on `outbox` (`OutboxPublisher` inserts,
`OutboxRelay` selects/updates — both built at T04, but T04's own tests were all mocked-repository
unit tests, so this gap existed unnoticed since T04 until T06's first real caller surfaced it).
**Not run against a real database this session** — Docker is unavailable in this environment;
carried forward as a verification gap alongside the already-carried-forward `Invoice`/`Outbox`
JPA-mapping verification gaps from T04/T05.

## Finding 2 — `client-secret` placeholder not recognized by the secret-scan test

**ACCEPTED and FIXED.** Extended `ApplicationPropertiesContentTest.noUndocumentedSecretAndNoYamlFileExists`
with a second, `client-secret`-specific check (exactly one occurrence, matching the documented
local-dev value) — the generic `doesNotContainPattern` catch-all stays a real catch-all for
anything *not* already an accounted-for carve-out, rather than being widened to quietly also
exempt this one.

## Finding 3 — `Instant` fields may serialize as numeric timestamps

**REJECTED — empirically disproved, not just argued.** Built and ran a minimal standalone
check: registered `JacksonAutoConfiguration` in a bare `AnnotationConfigApplicationContext`,
fetched the resulting `ObjectMapper` bean, and serialized a real `Instant` — result:
`"2026-07-13T00:00:00Z"`, not a numeric epoch value. Spring Boot's own `JacksonAutoConfiguration`
disables `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` by default; this is why no sibling
service needed to set it explicitly in `application.properties` either (confirmed: none do).
`crypto-service`'s own test-time `.disable(...)` call Kimi cited as evidence is in a hand-built,
non-Spring-Boot-autoconfigured `ObjectMapper` used for a contract-comparison test — a different
object entirely from the one that actually serializes HTTP responses/`RestClient` bodies in the
real running application. No change made.

## Finding 4 — Orphan watch on a post-`registerWatch` failure

**ACCEPTED as written; no new action.** Already an explicitly disclosed, accepted limitation in
the frozen brief (Phase 4) — re-confirmed here, not re-litigated.

## Finding 5 — `ProblemDetail` missing a `type` field

**ACCEPTED and FIXED.** Both `InvoiceExceptionHandler` and `ApiExceptionHandler` now call
`setType(URI.create("about:blank"))` on every `ProblemDetail`, matching
`ResourceServerConfig`'s own manual 401/403 handlers. **Deliberate departure from the verbatim-
mirror convention**, disclosed in both classes' own Javadoc: `crypto-service`'s own
`WatchExceptionHandler`/`ApiExceptionHandler` omit this; this service's own copy does not, since
agents.md's "stable type catalogue" rule is applied consistently here regardless of what the
mirrored template happens to do.

## Finding 6 — Controller/`CryptoClient`/token-provider tests not yet written

**REJECTED as a Phase 9 action — same disposition as every prior task's own identical
finding.** Phase 10 writes them.

## Finding 7 — Inherited `ApiExceptionHandler` 404/405-swallowing limitation

**ACCEPTED as written; no new action.** Already disclosed in Phase 7's own self-review,
re-confirmed here as the same accepted, shared-template limitation.

## Finding 8 — `CryptoClient.registerWatch` does not validate `response.status()`

**ACCEPTED and FIXED.** Added a check that `response.status()` equals `"REGISTERED"`
(case-insensitive, matching `crypto-service`'s own `WatchStatus.REGISTERED` literal, confirmed
by direct read of `Watch.java`), throwing `CryptoServiceUnavailableException` otherwise.

## Findings 9, 10, 11 — positive confirmations

**ACCEPTED as written — no action required.**

## Verification performed this phase

- `mvn -pl services/payment -am test -Dtest='!SchemaBaselineMigrationIntegrationTest'` —
  **99/99 passing**, unchanged (no new test files; all 4 fixes are production-code changes with
  no new assertions of their own yet — Phase 10 covers them).
- Direct empirical check disproving Finding 3 (see above).

## Net result

5 of 8 findings accepted and fixed in this phase (V3 migration, secret-scan test extension,
`ProblemDetail` type field on both handlers, `CryptoClient` status validation). 1 rejected with
direct empirical evidence (Finding 3). 2 accepted as already-disclosed, no-action observations
(Findings 4, 7). Finding 6 routes into Phase 10. Proceeding to Phase 10.
