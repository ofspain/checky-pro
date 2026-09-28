<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T05 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T05 — Contact projection (Q1/O1) |
| **Spec section** | Consumers & idempotency |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + T05 test files |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T05 regression-guard tests against the acceptance criteria and task statement.

---

## Gap 1 · No automated negative-proof that the `WHERE` guard is required

**Why it matters:** The core correctness property of T05 — that an out-of-order (older `occurredAt`) event does not overwrite a newer projection — rests on the native query containing `WHERE notifications.contact_projection.updated_at <= EXCLUDED.updated_at`. The Phase 10 artifact documents a one-time manual mutation test, but if a future refactor accidentally removes or weakens the guard, the build will not fail until someone re-runs that manual check.

**Suggested test:** Add a plain JUnit test that reads `ContactProjectionRepository.java` as text and asserts the `@Query` value contains `"ON CONFLICT"`, `"DO UPDATE"`, and the full `"WHERE notifications.contact_projection.updated_at <= EXCLUDED.updated_at"` clause. This is a cheap, permanent regression guard for the exact property the integration test depends on.

---

## Gap 2 · Equal-timestamp tie-breaking is not tested

**Why it matters:** The brief explicitly chose `<=` over `<` so that two events with genuinely equal `occurredAt` resolve to the later-processed call winning. This is a subtle, intentional semantic decision. The current tests cover insert, update (strictly later), and stale-rejection (strictly older), but never the boundary where `occurredAt == updated_at`. A future edit changing `<` to `<=` or vice versa would not fail any existing test, yet it would change observable behavior.

**Suggested test:** Add an integration test that calls `upsertEmail` twice with the same `accountUuid` and the same `occurredAt` but different emails, and assert the second call is accepted (returns `true`) and the row reflects the second email. This documents and locks the `<=` tie-breaking semantics.

---

## Gap 3 · No test directly exercises the repository method's return value

**Why it matters:** `ContactProjectionRepository.upsertEmail` returns `int` (`1` accepted, `0` rejected). The updater maps that to `boolean`. The unit test verifies the mapper, and the integration test verifies end-to-end behavior, but there is no test that directly invokes the repository method and asserts it returns `0` on a stale write. If a future change to the repository return type or Spring Data binding silently broke the `int` mapping, the tests might still pass because the updater's `boolean` result could be derived from a different signal.

**Suggested test:** Add an integration test that calls `repository.upsertEmail` directly with an older `occurredAt` after a newer one has been written, and assert it returns `0`. This proves the affected-row-count semantics come from the native query itself, not from the updater's interpretation.

---

## Gap 4 · No test proves `citext` preserves case on read-back

**Why it matters:** The `email` column is `CITEXT`, which is case-insensitive for comparison but should still preserve the original case when stored. The integration tests use lowercase emails, so they do not exercise the case-preservation path. A misconfigured mapping or binding that silently lowercased the stored value would not be caught.

**Suggested test:** Add an integration test that upserts an email with mixed case (e.g., `"Owner@Example.COM"`) and asserts `repository.findById(...).getEmail()` returns the exact same mixed-case string. This documents that `CITEXT` preserves case while remaining case-insensitive for comparison.

---

## Gap 5 · No test verifies stale-write rejection via JDBC directly

**Why it matters:** The integration test `olderCallDoesNotOverwriteANewerProjection` uses the entity/read path to assert the row was not overwritten. This is sufficient for the acceptance criterion, but it couples the stale-write proof to Hibernate's read path. A direct JDBC assertion on `email` and `updated_at` would be a stronger, persistence-layer-independent proof.

**Suggested test:** In the stale-write integration test, also open a raw `Connection` as `notification_app` and query `notifications.contact_projection` directly for the row, asserting the columns match the newer values. This mirrors the direct-JDBC style already used in `NotificationBaselineMigrationIntegrationTest`.

---

## Gap 6 · No test verifies `display_name` stays null after a stale-write attempt

**Why it matters:** AC4 requires every row created or updated by this task to have `display_name = NULL`. The current tests assert `getDisplayName()` is null after insert and after a successful update, but `olderCallDoesNotOverwriteANewerProjection` does not re-assert `display_name` is still null after the stale-write attempt. A pathological bug where the stale-write path somehow set `display_name` would not be caught.

**Suggested test:** Add an assertion in `olderCallDoesNotOverwriteANewerProjection` that `row.getDisplayName()` remains null after the rejected stale write.

---

## Gap 7 · `ContactProjectionUpdaterUnitTest` does not verify the updater is a Spring bean

**Why it matters:** The unit test mocks the repository and tests the updater in isolation. The integration test proves the updater can be autowired, but there is no small, fast test that verifies the class has the annotations needed for component scanning (`@Service` and constructor injection). A refactor that removed `@Service` would only be caught by the slower integration test.

**Suggested test:** This is already covered by the integration test autowiring the updater, so it is a low-priority gap. If desired, add a small ArchUnit or reflection test in the unit-test class that asserts `ContactProjectionUpdater` is annotated with `@Service` and has exactly one constructor.

---

## Summary

The T05 test suite now covers the two required files, delegation/return-value mapping, transaction join/rollback, insert/update/out-of-order behavior, and the `display_name` null requirement. The strongest remaining gap is **Gap 1**: the manual mutation test that proves the `WHERE` guard is necessary is not encoded as an automated regression guard. **Gap 2** documents and locks an intentional but untested tie-breaking decision. Gaps 3–6 tighten precision around repository return values, `citext` behavior, JDBC-level assertions, and AC4 coverage. Gap 7 is a minor speed/coverage trade-off already addressed by the integration test.
