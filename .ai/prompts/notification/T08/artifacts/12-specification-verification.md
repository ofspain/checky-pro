# notification · T08 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **AC1** — `ChannelPreference` maps exactly onto the 6 existing `channel_preferences` columns | Yes | `ChannelPreference.java` — `id`, `accountUuid`, `category`, `channel`, `enabled`, `updatedAt` | Validated by Hibernate's own `ddl-auto=validate` in every Testcontainers run; `channelPreferenceEntityMapsAllSixColumnsCorrectly` asserts all 6 getters directly | No | No |
| **AC2** — stored `enabled` value returned for a non-`SECURITY`/`EMAIL` pair | Yes | `PreferenceResolver.java` — `repository.findBy...().map(ChannelPreference::isEnabled)` | `shouldResolveChannelPreferencesPerRecipient`, `shouldSuppressChannelWhenRecipientOptedOut`, `storedRowTakesPrecedenceOverTheDocumentedDefault`, `securityInAppStillHonoursAStoredOptOutUnlikeSecurityEmail` | No | No |
| **AC3** — documented default returned when no row exists, for all 6 named pairs | Yes | `PreferenceResolver.java` — `DEFAULTS` map, `defaultFor(...)` | `shouldFallBackToDefaultPreferenceWhenNoneSet` (all 6), `noRowFallsBackToTheDocumentedDefault`, `defaultsMapMatchesTheDesignDocVerbatimTableExactly` (drift protection) | No | No |
| **AC4** — `SECURITY`+`EMAIL` resolves `true` unconditionally, never queries the repository | Yes | `PreferenceResolver.java` — early `return true` before any repository call | `securityEmailNeverQueriesTheRepository`, `securityEmailIsTrueEvenWithARealStoredFalseRow` (real adversarial row), `resolveContainsTheSecurityEmailEarlyReturnBeforeAnyRepositoryCall` (static-scan, source-order proof) | No | No |
| **AC5** — `V6` grants `SELECT` only; `INSERT`/`UPDATE`/`DELETE` denied | Yes | `V6__notification_app_channel_preferences_grant.sql` | `notificationAppCanSelectButNotInsertUpdateOrDeleteOnChannelPreferences` | No | No |
| **AC6** — any pair outside the 6 defaults resolves `false` (no row); a stored row on such a pair still wins | Yes | `PreferenceResolver.java` — `defaultFor` returns `false` for unknown keys; stored-row branch is unconditional on pair validity | `unrecognizedPairsResolveFalseWhenNoRowExists`, `webhookAndPushResolveFalseWithNoRow`, `unknownCategoryResolvesFalseWithNoRow`, `storedRowOnAnUnsupportedChannelTakesPrecedenceOverItsMissingDefault` | No | No |
| **AC7** — case-insensitive `category`/`channel` matching | Yes | `PreferenceResolver.java` — `toUpperCase(Locale.ROOT)` before every lookup | `categoryAndChannelAreUppercasedBeforeTheRepositoryQuery` (unit, argument capture), `lowercaseQueryArgumentsMatchAnUppercaseStoredRow` (real DB match) | No | No |
| L6 (preference resolution with a safe default) | Yes | Whole `PreferenceResolver` design — stored row → documented default → `false`, never "send everywhere," never throws for a valid, non-null input | Covered by all of the above | No | No |
| L2 (consume-only, no synchronous cross-service call) | Yes | `PreferenceResolver`/`ChannelPreferenceRepository` never call another service; pure local `channel_preferences` read | Implicit — no network client exists anywhere in this task's own files | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes, against T08's own literal scope (`tasks.md` task 8:
`ChannelPreference` + `PreferenceResolver` honouring opt-outs, applying the documented default,
enforcing the `SECURITY`-email floor). All 4 production files delivered (`ChannelPreference`,
`ChannelPreferenceRepository`, `PreferenceResolver`, `V6`) plus 2 test files and 1 modified
integration test file (20 new tests across Phases 10-11: 148 total, up from T06's own 128).
`T01SkeletonRegressionTest.java` and `NotificationBaselineMigrationIntegrationTest.java` were
modified exactly as the frozen brief authorized (the latter via Finding #5's own carve-out).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC7 all hold, each with
direct evidence and automated coverage, including the task's own two most novel/adversarial
properties independently empirically verified before being trusted: the hard floor against a real
stored `enabled=false` row (not merely the "no row" case the default table alone would cover), and
case normalization actually reaching a real stored uppercase row from a lowercase query argument
(not just the argument passed to a mocked repository).

**(3) Does it violate any LOCKED decision?** No. L6 holds throughout — every fallback path is
"documented default" or `false`, never "send everywhere" and never an unhandled exception for a
valid (non-null) input. L2 holds — no network call anywhere in this task's own code.

**(4) Remaining risks?**
- **`PreferenceResolver` has zero callers in production code today** — `DeliveryOrchestrator`
  (task 11), the only intended caller, doesn't exist yet. Every test invokes `resolve` directly;
  its correctness through a real request path (a consumed event → resolved preference → actual
  suppression/delivery) is unverified by construction, since no such path exists in this codebase
  yet. Not a defect — the same "seam built ahead of its caller" shape as T06's own
  `NotificationDispatcher`.
- **`category`/`channel` remain plain `String`, not typed enums** — Kimi's own Phase 3 Finding #7,
  explicitly left to Phase 5 as an informational trade-off, not forced. The DB `CHECK` constraint
  is the only enforcement of the value space; a caller typo (e.g. `"Email"` misspelled as `"Emial"`)
  would silently resolve via the `false` unknown-pair fallback rather than fail loudly. Acceptable
  under L6 ("never fail"), but worth revisiting once `DeliveryOrchestrator` exists and its own
  calling convention is known.
- **Whitespace-padded input is not trimmed** (Kimi Phase 11 Gap #4, documented not fixed) — same
  category of risk as the enum question above: a caller bug would silently fall back to `false`
  rather than fail loudly. No real caller exists yet to produce this bug.
- `WEBHOOK`/`PUSH` channel implementations don't exist yet (tasks not yet reached in `tasks.md`) —
  `PreferenceResolver`'s own `false`-for-unsupported-channel behavior is forward-compatible (a
  stored row still wins, per `storedRowOnAnUnsupportedChannelTakesPrecedenceOverItsMissingDefault`),
  so no rework is anticipated when those channels eventually land — only the `DEFAULTS` map would
  need new entries if/when `design.md` §4c is updated with real defaults for them.

## `package.md` §9 whole-service checklist — items relevant to T08

- [x] All §3 acceptance criteria have a passing named test from §8 — the 3 literally-named tests
  (`shouldResolveChannelPreferencesPerRecipient`, `shouldSuppressChannelWhenRecipientOptedOut`,
  `shouldFallBackToDefaultPreferenceWhenNoneSet`) all pass.
- [x] Every §4a LOCKED decision implemented as written — L6/L2 both hold.
- [x] Channel-preference resolution honours opt-outs and falls back to a documented default — this
  checklist item is T08's own direct deliverable; fully satisfied.
- [ ] Every other checklist item (delivery log, secret-safe logging, in-app auth, retry/dead-letter,
  contract validation) — **out of scope**, unrelated to this task's own files.

## Cross-task regression check

Full `services/notification` suite: 148 tests, 0 failures. `T01SkeletonRegressionTest` (9 tests,
its own 23-file authorized list, updated this task) and `NotificationBaselineMigrationIntegrationTest`
(16 tests, `channel_preferences` now in grant-proof coverage) both still pass alongside T02's,
T03's, T04's, T05's, and T06's own test classes, all unmodified by this task except the two
disclosed, justified edits — confirms T08's changes didn't regress anything the prior five
implementation tasks established.

## Spec status

`spec/notification-service/package.md`'s header is unchanged — the version/status bump is task 20,
matching the established precedent (T02-T06 all left it untouched). Not touched here.

---

**PASS** — all 7 acceptance criteria satisfied with direct evidence and automated coverage (148
tests, both of the task's own genuinely adversarial properties empirically verified via scratch
tests before being made permanent), no LOCKED decision violated, task boundary held throughout.
Three residual risks are disclosed above, none blocking: `PreferenceResolver` has no real caller
yet (by design, task 11's own scope), the string-typed category/channel and untrimmed-input
behaviors are both caller-bug-shaped risks with no real caller to trigger them today, and
`WEBHOOK`/`PUSH` support is forward-compatible but incomplete pending a future `design.md` update.
