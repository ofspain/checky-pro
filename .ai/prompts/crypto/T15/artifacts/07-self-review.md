# crypto · T15 · Phase 7 — Self-Review

Reviewed the diff (Phase 6) against the frozen brief and `agents.md`: correctness, boundary conditions,
null-safety, thread-safety, transaction boundaries, module boundaries, idempotency, money types,
enumeration-safety/secret-handling, readability, complexity. Findings only — no fixes applied here.

An ad-hoc real-Postgres smoke test (run once Docker became available mid-pipeline, then deleted — not a
Phase 10 deliverable) already confirmed several things that would otherwise be open risks here: the
`UUID` column mapping, the fully-qualified-enum-literal JPQL syntax in `markUnregisteredIfRegistered`,
`V2`'s schema-wide sequence grant covering the two new tables' `IDENTITY` columns without a new grant,
and the full register → find → unregister → idempotent-re-delete → 404 flow, all against a real
Postgres 16 instance. Those are not re-flagged below. What that smoke test did **not** exercise is called
out explicitly (Findings 2/3), since it called `WatchService` directly, never through
`WatchController`/`WatchExceptionHandler`/Spring MVC.

---

### 1. `expectedAmount`'s regex has no upper bound on digit count — a value exceeding `NUMERIC(78,0)`'s
precision passes validation and fails at the database instead of returning `400`

- **Severity:** Medium
- **Evidence:** `watch/WatchService.java:21` (`EXPECTED_AMOUNT_PATTERN = Pattern.compile("^[1-9][0-9]*$")`),
  `:67-68` (`parseExpectedAmount`); `db/migration/V6...` schema reference:
  `expected_amount NUMERIC(78, 0)` (`V1__chain_baseline.sql:14`).
- **Recommendation:** This is the same class of gap Phase 3 Finding 7 already fixed for
  `address`/`tokenContractAddress` length (`@Size(max=128)`), but the same principle was not applied to
  `expectedAmount`'s own DB-enforced bound. Cap the regex at 78 digits: `^[1-9][0-9]{0,77}$`, so an
  oversized value is rejected with `400` structurally rather than reaching the database at all.

---

### 2. Controller-level HTTP response shape for both `400` and `404` remains empirically unverified

- **Severity:** Medium
- **Evidence:** `watch/WatchController.java` (no test yet exercises it), `watch/WatchExceptionHandler.java`
  (no test yet exercises it); the frozen brief's own Constraints section explicitly required this to be
  "confirmed by direct testing in Phase 6/7... mirroring this pipeline's established verify-don't-assume
  discipline" — that verification has not actually happened yet. The ad-hoc smoke test that did run
  called `WatchService` directly, bypassing the HTTP/MVC layer entirely.
- **Recommendation:** Treat this as still open, not assumed-correct, until Phase 10's planned
  `WatchControllerTest` (`@WebMvcTest`) actually exercises a `400` (bean-validation failure and
  `InvalidWatchRequestException`) and a `404` (`WatchNotFoundException`) through real Spring MVC
  dispatch and asserts the response `Content-Type` and body shape.

---

### 3. `WatchExceptionHandler` is package-private, deviating from this codebase's one precedent for this
exact pattern, and that deviation is itself unverified

- **Severity:** Low
- **Evidence:** `watch/WatchExceptionHandler.java:14,16` (`@RestControllerAdvice` / `class
  WatchExceptionHandler`, no `public` modifier) vs. `services/auth/.../apikey/ApiKeyExceptionHandler.java`
  (`public class ApiKeyExceptionHandler`), the one existing precedent for a per-module
  `@RestControllerAdvice` in this repository.
- **Recommendation:** Spring's classpath bean scanning does not require `public` visibility, and
  `@RestControllerAdvice` is not proxied the way `@Transactional`/AOP-advised beans are, so this is very
  likely fine — but "very likely" is not "verified," and it deviates from the one precedent that exists.
  Either explicitly confirm via Phase 10's `WatchControllerTest` that both exception mappings actually
  fire (which would resolve Finding 2 and this finding together), or match the established precedent's
  `public` visibility to remove the question outright at zero cost.

---

### 4. `markUnregisteredIfRegistered`'s `@Modifying` query does not clear the persistence context — a
latent stale-read trap for a future caller, not a current defect

- **Severity:** Low
- **Evidence:** `watch/WatchRepository.java:24-28` (`@Modifying @Query(...)`, no `clearAutomatically`).
- **Recommendation:** A bulk/modifying JPQL update bypasses the first-level cache; a caller that
  re-fetches the same `Watch` (e.g. via `findByWatchId`) within the *same* transaction as this update
  would see a stale, pre-update cached entity. No code path in this task does that today — `unregister`
  never reads the row back, and `register` never calls this method at all — so there is no current
  observable bug. Recommend adding `clearAutomatically = true` to `@Modifying` now anyway, since it is a
  one-line, zero-risk change that closes the gap before any future caller (a plausible one: a future
  endpoint returning the post-unregister watch state) can be silently bitten by it.

---

No correctness, boundary-condition, null-safety, thread-safety, module-boundary, idempotency, money-
type, or secret-handling defects found beyond the four items above. The atomic conditional `UPDATE`
approach (Phase 3 Finding 4) is race-safe by construction and was confirmed to work correctly end-to-end
against real Postgres. `AddressValidator`/`Clock` wiring, the `-1` cursor sentinel, and the `V6` grant
migration are all correct and already empirically verified.
