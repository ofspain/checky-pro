# crypto · T15 · Phase 3 — Design Challenge Findings

Reviewed: `artifacts/02-task-implementation-brief.md`, `spec/crypto-service/agents.md`,
`spec/crypto-service/design.md` (§4a, §4c, §5, §6), `spec/crypto-service/requirements.md` (R18/R19),
`spec/crypto-service/package.md` §8, `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql`,
and `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyExceptionHandler.java`.

---

### 1. T15 excludes address validation despite L8 making it mandatory

**Issue:** `design.md` §4a L8 is LOCKED: "Address validation is mandatory. EIP-55 checksum on all EVM
addresses; Base58Check on Tron ... Invalid addresses are rejected at the boundary." The TIB explicitly
puts `AddressValidator` out of scope, arguing that R18 says "register... and begin watching," not
"validate." This is a direct conflict with a LOCKED decision unless an explicit override is recorded in
`design.md` §4a.

**Severity:** High

**Evidence:** `spec/crypto-service/design.md:12-13` (L8); TIB Scope/Out section (`:36-38`) and
Dependencies (`:70`) exclude address validation; `WatchController` will be the boundary for this
feature.

**Recommended brief amendment:** Either (a) invoke `AddressValidator` in `WatchService.register` for
both `address` and `tokenContractAddress` (the latter is also an address-shaped value and L8's wording
is general), or (b) escalate to the author to add an explicit L8 override in `design.md` §4a for the
watch-registration endpoint, with rationale. Until one of these happens, the brief violates a LOCKED
decision.

---

### 2. `expectedAmount` validation is ambiguous about fractional base units

**Issue:** The TIB says `expectedAmount` must be a "well-formed positive decimal string" parsed to
`BigDecimal`. `design.md` §4c states monetary values are "decimal strings in token base units, never
JSON numbers," and the DDL stores `expected_amount NUMERIC(78, 0)` — i.e., integer base units, scale 0.
The TIB does not state that fractional values (e.g., `"1.5"`) are rejected, so a valid-looking request
could parse successfully and then fail at the database with a 500.

**Severity:** Medium

**Evidence:** TIB Inputs/Outputs (`:74-77`) and Constraints (`:166`); `design.md:82`;
`V1__chain_baseline.sql:14` (`expected_amount NUMERIC(78, 0) NOT NULL`).

**Recommended brief amendment:** Specify that `expectedAmount` is validated as a positive **integer**
decimal string in token base units (scale 0, no decimal point, no exponent), matching the DDL. Values
such as `"1.5"`, `"1e18"`, or `"-0"` must return 400.

---

### 3. POST is not idempotent and can create duplicate watches for one invoice

**Issue:** The TIB accepts that a retried `POST` creates a second, independent watch because the schema
has no unique constraint on `invoice_uuid`. This is a deliberate scope limit, but the operational
consequence — multiple watchers, multiple events, and multiple attestations for the same Payment
invoice — is not surfaced in the brief.

**Severity:** Medium

**Evidence:** TIB Out section (`:39-41`); `V1__chain_baseline.sql:9-10` (no unique constraint on
`invoice_uuid`).

**Recommended brief amendment:** Document the risk explicitly in the brief (e.g., "A retried `POST`
without the returned `watchId` will create a new watch; the Payment Service must store the first
response's `watchId` and use it for `DELETE` and deduplication"). Add a required test that two identical
`POST` requests produce two distinct `watchId`s and two `Watch`/`ChainCursor` row pairs.

---

### 4. DELETE concurrency strategy is unspecified

**Issue:** Two concurrent `DELETE /internal/v1/watches/{watchId}` requests for the same `REGISTERED`
watch can both read `REGISTERED`, both update the row, and both return 204. The result is still correct,
but the `unregistered_at` timestamp may be set twice (to slightly different values), and the brief does
not specify whether this is acceptable or whether a row-locking/optimistic strategy is required.

**Severity:** Medium

**Evidence:** TIB State Changes (`:98-101`) and Constraints (`:171-172`); no concurrency discussion in
the brief.

**Recommended brief amendment:** Specify the implementation strategy: issue a single
`UPDATE watches SET status='UNREGISTERED', unregistered_at=? WHERE watch_id=? AND status='REGISTERED'`
and return 204 regardless of whether 0 or 1 rows were updated. This makes the operation naturally
idempotent and race-safe without an extra SELECT.

---

### 5. Timestamp source for `created_at` and `unregistered_at` is unspecified

**Issue:** `agents.md` requires use of `java.time` with an injectable `Clock`. The TIB only mentions the
injected `Clock` for the `expiresAt`-in-the-future check. The DDL provides `DEFAULT now()` for
`created_at` and `updated_at`, but relying on DB `now()` makes unit tests non-deterministic and
inconsistent with the codebase's Clock-based testing convention.

**Severity:** Low

**Evidence:** `agents.md:29`; TIB Dependencies (`:61-62`) and Constraints (`:166-167`);
`V1__chain_baseline.sql:17-18`, `:69` (`DEFAULT now()`).

**Recommended brief amendment:** State that `Watch.createdAt` and `Watch.unregisteredAt` are populated
from the injected `Clock` (and persisted explicitly), matching the `expiresAt` check. This keeps
timestamps deterministic under a fixed `Clock` in tests.

---

### 6. `EXPIRED` status exists in schema and enum but has no transition in this task

**Issue:** `WatchStatus` is required to include `EXPIRED`, and the DDL CHECK constraint allows it, but
nothing in this task ever sets a watch to `EXPIRED`. The TIB does say `DELETE` on an `EXPIRED` watch
returns 204, but there is no required test for that path, and the brief does not explain how `EXPIRED`
will be introduced.

**Severity:** Low

**Evidence:** TIB Files to Create (`:106`) lists `WatchStatus.java` with `REGISTERED`, `UNREGISTERED`,
`EXPIRED`; TIB Outputs (`:86-89`) mentions `EXPIRED` in DELETE semantics; Required Tests (`:150-161`)
do not list an `EXPIRED` DELETE case.

**Recommended brief amendment:** Add a sentence in Scope or State Changes stating that `EXPIRED` is not
set by this task; it will be introduced by a future scheduler/watcher task. Add a required test that
manually seeds an `EXPIRED` watch and asserts `DELETE` returns 204 with no state change.

---

### 7. No structural upper-bound on `address` / `tokenContractAddress` length

**Issue:** The DDL caps both address columns at `VARCHAR(128)`. The TIB only requires "required fields"
structural validation. An address longer than 128 characters would pass bean validation and fail at
the database, producing a 500 instead of a 400.

**Severity:** Low

**Evidence:** TIB Inputs/Outputs (`:74-77`); `V1__chain_baseline.sql:12-13`.

**Recommended brief amendment:** Add `@Size(max=128)` (or equivalent) to `RegisterWatchRequest.address`
and `tokenContractAddress` so oversize inputs are rejected structurally with 400. If Finding 1 is
accepted and `AddressValidator` is invoked, length validation should still remain as a first-layer
guard.

---

### 8. 1:1 mapping between `Watch` and `ChainCursor` is implicit

**Issue:** The TIB says "one `ChainCursor` row per registration" and that inserts are atomic, but it
does not explicitly state that the new `ChainCursor` row must have `watch_id` set to the generated
`watchId` and `chain` set to the request chain. The DDL declares `chain_cursors.watch_id` as a plain
`UUID` (no `NOT NULL`, no unique constraint), so the schema does not enforce this relationship.

**Severity:** Low

**Evidence:** TIB Scope (`:19-20`) and Dependencies (`:65-69`); `V1__chain_baseline.sql:63-70`.

**Recommended brief amendment:** Add an explicit acceptance criterion or constraint: "The
`ChainCursor` inserted during registration has `watch_id` equal to the generated `watchId` and `chain`
equal to the request chain; both inserts occur in the same transaction." This makes the placeholder
row's relationship to the watch unambiguous for the future task that will advance the cursor.

---

### 9. `WatchExceptionHandler` should be ordered highest-precedence

**Issue:** The TIB says the handler mirrors `services/auth`'s per-module pattern. The auth handler
carries `@Order(Ordered.HIGHEST_PRECEDENCE)` because a catch-all `Exception.class` handler elsewhere
would otherwise shadow its mappings. The TIB does not explicitly require the same ordering for the
watch handler.

**Severity:** Low

**Evidence:** `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyExceptionHandler.java:19-20`;
TIB Files to Create (`:114`) lists `WatchExceptionHandler.java` with no ordering detail.

**Recommended brief amendment:** State that `WatchExceptionHandler` must be annotated with
`@Order(Ordered.HIGHEST_PRECEDENCE)` (or equivalent) so its `WatchNotFoundException` and validation-error
mappings reliably outrank any global fallback handler.
