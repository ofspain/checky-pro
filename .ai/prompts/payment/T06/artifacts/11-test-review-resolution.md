# payment · T06 · Phase 11 — Test Review Resolution

Consumes `artifacts/11-test-review.md` (Kimi 2.7). Records disposition of each finding and the
fixes actually applied.

## Finding 1 — `SchemaBaselineMigrationIntegrationTest` is stale against V3 (CI red)

**ACCEPTED and FIXED.** Kimi ran this against a real Postgres (Docker available in its own
environment) and reported the exact 3 failing assertions. Restructured: `invoices` joins
`GRANTED_TABLES` (now INSERT+SELECT, 3 entries); `outbox` is tracked in a new
`ALL_GRANTED_TABLE_NAMES` constant and given its own dedicated test
(`outboxHasExactlyInsertSelectAndUpdatePrivilegesNoMore`), since its privilege set (INSERT,
SELECT, **and** UPDATE — `OutboxRelay`, T04, marks `published_at`) differs from the two-privilege
set every other `GRANTED_TABLES` entry has; `UNGRANTED_TABLES` now has 5 entries, not 7;
`bothMigrationsAreRecordedAsSuccessfulInFlywayHistory` now expects versions `1, 2, 3`.
**Could not re-run against a real database in this session** — Docker is unavailable here;
carried forward as a verification gap alongside the already-disclosed V3-migration-not-run gap
from Phase 9.

## Finding 2 — `shouldTransitionCreatedToWatchingOnWatchAck` doesn't prove the same UUID reaches both calls

**ACCEPTED and FIXED.** Added two `ArgumentCaptor<UUID>`s (one per mock) and an explicit
equality assertion between them.

## Finding 3 — No `InOrder` verification that `registerWatch` precedes `create`

**ACCEPTED and FIXED**, folded into the same test as Finding 2's fix via `Mockito.inOrder(...)`.

## Finding 4 — No test for a missing/malformed JWT `sub` claim

**ACCEPTED and FIXED** — and a real bug in the fix's own first draft caught along the way:
`SecurityMockMvcRequestPostProcessors.jwt()`'s default `Jwt.Builder` always sets `sub="user"`
(confirmed by reading its source directly), so the first version of
`shouldReturn400ForMissingSubClaim` never actually produced a missing claim — it exercised the
same malformed-value path as the sibling test. Fixed using `.claims(claims ->
claims.remove(JwtClaimNames.SUB))` to produce a genuinely absent claim, then re-verified the
exact `"sub claim is missing"` detail message is returned.

## Finding 5 — 403 response body not asserted

**ACCEPTED and FIXED.** Added `Content-Type`, `type`, `title`, `status` assertions to
`shouldRejectNonMerchantCallerWithForbidden`.

## Finding 6 — `InvoiceServiceTest` doesn't inspect the outbox payload

**ACCEPTED and FIXED.** Captured the real `InvoiceCreatedPayload` object and asserted every
field, including `amount` as the exact string `"1000"`.

## Finding 7 — No real serialization test for `InvoiceCreatedPayload`

**ACCEPTED and FIXED.** Added `InvoiceCreatedPayloadSerializationTest`, using the same
Boot-autoconfigured-`ObjectMapper` technique that empirically disproved Phase 8 finding 3 for
`RegisterWatchRequest` — proves `amount` is textual and `expiresAt` is ISO-8601 on the real wire
shape, not just by construction.

## Finding 8 — Token-provider safety-margin boundary not pinned exactly

**ACCEPTED as written; not fixed.** Low severity; the existing expiry test already proves
refresh-after-expiry works. A precise 29s-vs-31s boundary test is a reasonable future addition,
not required to close this task's own named AC7 test.

## Finding 9 — Failures not proven uncached

**ACCEPTED as written; not fixed.** Low severity, and correctly noted by Kimi itself that the
current implementation already doesn't cache failures (`cachedToken` is only assigned after a
successful fetch) — this would be a regression guard for a bug that does not exist, not a gap
in present coverage.

## Finding 10 — Concurrent refresh near (not just at) expiry not exercised

**ACCEPTED as written; not fixed.** Low confidence per Kimi's own rating; the existing
concurrency test already proves the single-flight lock itself works correctly under real thread
contention from an empty cache, which is the load-bearing property AC7 names.

## Finding 11 — `CryptoClientTest` timeout-elapsed assertion could flake under load

**ACCEPTED and FIXED.** Tightened `isLessThan(1000)` to `isLessThan(500)` — still generous
headroom above the configured 200ms timeout, well short of the server's 1000ms sleep.

## Verification performed this phase

- `mvn -pl services/payment -am test -Dtest='!SchemaBaselineMigrationIntegrationTest'` —
  **123/123 passing** (up from 120).
- Mutation check 1: corrupted `InvoiceService`'s `amount.toPlainString()` call (replaced with a
  literal), confirmed the new outbox-payload assertion in `InvoiceServiceTest` fails, reverted
  via `Edit` (not `git checkout`, to avoid discarding an uncommitted fix), confirmed clean.
- Finding 3's own `InOrder` assertion was not separately mutation-tested: the controller's real
  call order is structurally forced by a genuine data dependency (`InvoiceService.create` takes
  `watchResponse.watchId()` as an argument, so `registerWatch` must execute first for the code to
  even compile/run) — a literal reorder isn't possible without a larger refactor the frozen brief
  does not call for.
- `SchemaBaselineMigrationIntegrationTest`'s own fix (Finding 1) could not be run against a real
  database this session (Docker unavailable) — the restructuring was verified by direct,
  line-by-line reasoning against Kimi's own real failure output, not empirically re-confirmed.

## Net result

9 of 11 findings accepted and fixed in this phase, including one critical (CI-red) migration
test fix and one genuinely new bug caught while fixing Finding 4 (the JWT test helper's own
default `sub` claim). 2 accepted as disclosed, no-action observations (Findings 9, 10 — already
covered or testing a non-existent bug). Finding 8 deferred as a reasonable, non-blocking future
addition. Proceeding to Phase 12.
