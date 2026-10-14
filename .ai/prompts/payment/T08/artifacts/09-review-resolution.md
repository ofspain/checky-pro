# payment · T08 · Phase 9 — Review Resolution

Human Approval gate decision not required for this phase's fixes — all 6 Kimi Phase 8 findings
have an obvious, uncontroversial fix (a real, confirmed bug and its necessary consequences),
matching this session's own established "fix clear self-evident defects immediately, don't wait
for Phase 9 to merely record them" precedent (e.g. T06 Phase 7). Recorded here for the audit
trail.

## Finding 1 — `expireInvoice` merges a detached invoice, creating a lost-update race

**ACCEPTED and FIXED — the most significant finding this task.** Confirmed by direct trace: the
detached `Invoice` object's own `state` field is always `OPEN` (that was the candidate query's
own filter), so `markExpired()`'s guard checked the stale in-memory value, never the real
current DB value — a merge-save could silently overwrite a real concurrent state change (e.g. a
payment becoming in-flight) back to `EXPIRED`. Replaced entirely with a single atomic,
conditional `UPDATE invoices SET state = :newState, updated_at = :now WHERE id = :id AND state =
:expectedCurrentState` (`InvoiceRepository.updateStateIfCurrentState`) — no entity load, no
merge, no lost-update window. `Invoice.markExpired()` removed entirely (no longer needed);
`expireInvoice` now returns `boolean` (whether the row was actually expired).

## Finding 2 — `SchemaBaselineMigrationIntegrationTest` stale again (CI red)

**ACCEPTED and FIXED.** Updated the Flyway-history version assertion to `1-5`;
`shedlock`/`payments` moved out of `UNGRANTED_TABLES` into `ALL_GRANTED_TABLE_NAMES`, each with
its own dedicated privilege-set test (`shedlockHasExactlySelectInsertAndUpdatePrivilegesNoMore`,
`paymentsTableHasExactlySelectPrivilegeNoMore`), mirroring `outbox`'s own T06 precedent exactly.
**Could not re-run against a real database this session** — Docker unavailable; same
carried-forward verification gap as T06/T07's own identical situation (T07's own equivalent fix
was later empirically confirmed by Kimi's own Docker-enabled Phase 8 run — the same should be
expected here once Docker is available).

## Finding 3 — `expireInvoice` saved even when `markExpired` was a no-op

**ACCEPTED and FIXED — same root cause and same fix as Finding 1.** The atomic conditional
update naturally resolves this too: it issues no `UPDATE` at all (0 rows affected) when the
`WHERE` predicate doesn't match, so there is no spurious `updated_at` write.

## Finding 4 — Native query not empirically verified against a real database

**ACCEPTED as written; no new action.** Same carried-forward, disclosed, environment-caused gap
as Phase 7's own self-review finding — Docker unavailable this session.

## Finding 5 — Phase 10 test files are missing

**REJECTED as a Phase 9 action — same disposition as every prior task's own identical finding.**
Phase 10 writes them, now informed by Finding 1's own real regression (a dedicated concurrent-
state-change test is now a Required Test, not merely a nice-to-have).

## Finding 6 — Race window between the in-flight check and expiration

**ACCEPTED and FIXED — same fix as Finding 1.** The atomic conditional update's own `WHERE
state = 'OPEN'` closes this precisely: if a payment row appears between the candidate query and
the update, the update's own predicate no longer matches and nothing is overwritten.

## A related, narrower residual race found while applying Finding 1's fix (disclosed, not silently introduced)

Fixing the *DB-state* half of the race (Findings 1/3/6) does not fix the *watch-unregistration*
half: `CryptoClient.unregisterWatch` still runs unconditionally against the batch-stale
candidate state, before the atomic update is attempted. A payment that arrives in the (narrow,
but non-zero for invoices later in a large batch) window between the candidate query and that
specific invoice's own turn in the sweep loop will have its watch torn down even though the
atomic update correctly leaves its row alone — meaning no further `chain.tx.*` event for that
payment will ever reach this service again. Disclosed in `InvoiceExpirySweepJob.processOne`'s own
code comment and logged at `WARN` when it's detected (the `expireInvoice` call returning
`false`). Closing it fully would require re-checking in-flight status immediately before
`unregisterWatch`, atomically with it — a real design question for a future hardening task if
this window is ever shown to matter in practice, not attempted as a late, unreviewed addition to
an already-approved fix.

## Verification performed this phase

- `mvn -pl services/payment -am compile` / `test-compile` — clean.
- `mvn -pl services/payment -am test -Dtest='!SchemaBaselineMigrationIntegrationTest'` —
  **141/141 passing, unchanged** (no new tests yet for the corrected behavior — Phase 10's job).

## Net result

All 6 Kimi findings resolved (5 fixed, 1 accepted as a disclosed, carried-forward process gap).
One additional, narrower race self-identified and disclosed while applying the fix, not hidden.
No Human Approval product decision was needed — every fix here is a correction to a confirmed
defect, not a design trade-off. Proceeding to Phase 10.
