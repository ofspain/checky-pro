# notification · T11 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T11 — Delivery orchestrator + log |
| **Consumes** | All prior T11 artifacts (Phases 0-11) |
| **Produces** | `artifacts/12-specification-verification.md` |

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R10** — opt-out suppresses delivery on that channel and records the suppression | Yes | `DeliveryOrchestrator.java:157` `dispatchOneChannel` — `preferenceResolver.resolve(...)`, `if (!enabled) { save(...,"SUPPRESSED",...); return; }` | `DeliveryOrchestratorTest.shouldSuppressChannelWhenRecipientOptedOut`, `DeliveryOrchestratorTest.bothChannelsSuppressedRecordsTwoSuppressedRowsAndNeitherRendersNorSends`, `DeliveryOrchestratorIntegrationTest.shouldSuppressChannelWhenRecipientOptedOut` (real DB) | No | No |
| **R11** — every delivery attempt appends a row capturing recipient, channel, source event key, template version, outcome, timestamp | Yes | `DeliveryOrchestrator.java:218` `save(...)` — all 6 fields passed to `DeliveryLog`'s constructor on every code path (`SENT`/`FAILED`/`SUPPRESSED`), including the pre-loop (Finding #4) and per-channel (Phase 11 Gap #1/#5) fallback paths | `DeliveryOrchestratorTest.shouldRecordEveryDeliveryAttemptAndOutcomeInLog`, `DeliveryOrchestratorIntegrationTest.shouldRecordEveryDeliveryAttemptAndOutcomeInLog` (real DB, asserts every field) | No | No |
| **L3** — dispute-grade, append-only delivery log; retry adds a row, never overwrites | Yes | `DeliveryLog.java:65` (no update/mutator methods exist — only getters + one constructor); `DeliveryLogRepository.java:12` exposes only inherited `save()`, no custom update query | `DeliveryOrchestratorIntegrationTest.deliveryLogRowsCommitWhenTheExternalCallersTransactionCommits` (real row persists), full suite has no test that ever updates an existing row | No | No — `attempt` is hardcoded to `1` (retry/increment is task 14's own scope, explicitly out of T11) |
| **L4** — no secrets/tokens in logs; every `errorDetail` redacted | Yes | `DeliveryOrchestrator.java` `save(...)` — `SecretSafeLogging.redact(errorDetail)` applied before persistence on every `FAILED` path | `DeliveryOrchestratorTest.renderFailureErrorDetailIsRedactedBeforePersistence`, `channelSendFailureRecordsFailedRedactedRowAndOtherChannelStillProceeds` | No | No |
| **L5** — channels behind one interface, orchestrator channel-agnostic | Yes | `NotificationChannel.java:18` (introduced by this task); `DeliveryOrchestrator` never branches on a concrete channel class, only on the `channel` string key via `channelsByName.get(channel)` | `DeliveryOrchestratorIntegrationTest.exactlyTwoNotificationChannelBeansAreRegisteredWithExpectedNames`, `NoOpEmailChannelTest`/`NoOpInAppChannelTest` | No | No |
| **L6** — preference resolution, safe default, never "send everywhere" | Yes (via T08, first real caller here) | `DeliveryOrchestrator.java:157` calls `preferenceResolver.resolve(accountUuid, mapping.category(), channel)` before any render/send | `PreferenceResolverIntegrationTest` (T08, pre-existing) + this task's own suppression tests exercise the real call path | No | No |
| **L9** — templates versioned, version recorded per delivery | Yes (via T09, first real caller here) | `DeliveryOrchestrator.java` `templateRenderer.render(...)` → `message.version()` passed to every `save(...)` call on the `SENT`/missing-channel-bean paths | `DeliveryOrchestratorTest.shouldRecordEveryDeliveryAttemptAndOutcomeInLog` asserts `templateVersion` on both rows; `DeliveryOrchestratorIntegrationTest` asserts `templateVersion == 1` against the real seeded template | No | No |
| **AC1** — `DeliveryOrchestrator implements NotificationDispatcher`; `NoOpNotificationDispatcher` deleted | Yes | `DeliveryOrchestrator.java:64`; `NoOpNotificationDispatcher.java` confirmed absent from the tree (`git log` shows deletion at `b137213`) | `IdempotencyGuardIntegrationTest.theRealDeliveryOrchestratorIsTheResolvedSpringBean` | No | No |
| **AC2** — VERBATIM mapping table, `user.registered→SECURITY` resolved, `account.suspended` excluded, unknown kind is a silent no-op | Yes | `DeliveryOrchestrator.java:83-90` — 7 entries, verified against `design.md` lines 31-38/45-47 | `DeliveryOrchestratorTest.notificationMappingsTableContainsExactlyTheSevenVerbatimEntriesAndExcludesAccountSuspended`, `unknownNotificationKindWritesNoRowsAndTouchesNoCollaborator`, `nullNotificationKindWritesNoRowsAndTouchesNoCollaborator` | No | No — `user.registered`'s category is resolved by elimination since `design.md:45` names `(verify, reset, suspended, security_alert)`, not `registered`; a disclosed, reasoned interpretation, not a literal spec match |
| **AC3** — prefs resolved before rendering; disabled channel skips render/dispatch entirely | Yes | `DeliveryOrchestrator.java:157` — `resolve` called, then `if (!enabled) return;` before the `templateRenderer.render(...)` call | `shouldSuppressChannelWhenRecipientOptedOut` (asserts `templateRenderer` never called for the suppressed channel) | No | No |
| **AC4** — render failure caught, `FAILED` row, continues to next channel | Yes | `DeliveryOrchestrator.java` inner `try { render } catch { save(FAILED); return; }` | `renderFailureRecordsFailedRowSkipsSendAndDoesNotPropagate` | No | No |
| **AC5** — email resolved once per `dispatch`, used for `EMAIL` recipient, passed to `send` | Yes | `DeliveryOrchestrator.java:119` `findEmail` called once, before the channel loop | `inAppRecipientIsAlwaysTheAccountUuidStringEvenWhenEmailIsPresent`, `missingEmailFailsEmailChannelWithoutRenderOrSendButInAppStillProceeds` | No | No |
| **AC6** — resolved channel bean called; `send` failure caught, `FAILED`; success is `SENT` | Yes | `DeliveryOrchestrator.java` `channelsByName.get(channel)`, inner `try { send; save(SENT) } catch { save(FAILED) }` | `channelSendFailureRecordsFailedRedactedRowAndOtherChannelStillProceeds`, `missingChannelBeanRecordsFailedRowWithTheAlreadyRenderedTemplateVersionAndSkipsSend` | No | No |
| **AC7** — every row includes all required fields, redacted `errorDetail`, `attempt` always `1` | Yes | `DeliveryLog.java` constructor hardcodes `this.attempt = 1` | `shouldRecordEveryDeliveryAttemptAndOutcomeInLog` (unit + integration) | No | No |
| **AC8** — `NotificationChannel` introduced; two real (not stub) temporary implementations | Yes | `NotificationChannel.java`, `NoOpEmailChannel.java`, `NoOpInAppChannel.java` | `NoOpEmailChannelTest`, `NoOpInAppChannelTest` | No | No |
| **AC9** — `dispatch` never throws for any plausible input | Yes | Two-level `try/catch` (outer in `dispatch`, outer+inner in `dispatchOneChannel`), both catching `Exception` not `Throwable` | `aGenuineErrorFromAChannelPropagatesOutOfDispatchInsteadOfBeingSwallowed` (proves the deliberate `Exception`-only boundary), every `FAILED`-path test proves non-propagation | No | No |
| **AC10** — `@Transactional`, joins caller's transaction, rolls back with it | Yes | `DeliveryOrchestrator.java:113` `@Transactional` (default `REQUIRED`) | `dispatchIsAnnotatedTransactionalWithDefaultRequiredPropagation`, `deliveryLogRowsRollBackIfTheExternalCallersTransactionRollsBack`, `deliveryLogRowsCommitWhenTheExternalCallersTransactionCommits` | No | No |
| **Named test** `shouldRecordEveryDeliveryAttemptAndOutcomeInLog` (`package.md` §8) | Present | — | `DeliveryOrchestratorTest` + `DeliveryOrchestratorIntegrationTest`, both by this exact method name | No | No |
| **Named test** `shouldSuppressChannelWhenRecipientOptedOut` (`package.md` §8) | Present | — | `DeliveryOrchestratorTest` + `DeliveryOrchestratorIntegrationTest`, both by this exact method name | No | No |

## Answers

**(1) Is the task fully complete?** Yes. All 6 files-to-create exist (`DeliveryLog`, `DeliveryLogRepository`,
`DeliveryOrchestrator`, `NotificationChannel`, `NoOpEmailChannel`, `NoOpInAppChannel`); both
files-to-modify (`ContactProjectionUpdater`, `AuthEventConsumer`) carry exactly the disclosed
extensions; `NoOpNotificationDispatcher` and its test are deleted, per the pre-authorized T06
Javadoc. The task has now been through 8 rounds of adversarial review (Kimi Phases 3, 8, 11) and
this session's own empirical self-review (Phase 7), with every finding either fixed or explicitly,
correctly rejected (Finding #3, a factually false Kimi claim caught via direct grep) or disclosed as
an accepted, out-of-scope limitation.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC10, see matrix above. AC9
("`dispatch` never throws") is the hardest constraint this task carries and is proven at three
independent levels: empirically (Phase 7's real-infrastructure scratch test), by mutation (Phase 11
addendum's real bug fix — a `preferenceResolver.resolve` throw used to silently drop a row; now it
doesn't), and by a dedicated `Error`-vs-`Exception` boundary test.

**(3) Does it violate any LOCKED decision?** No. L3/L4/L5/L6/L9 all hold, per the matrix. The one
interpretive judgment call — `user.registered`'s category resolved to `SECURITY` by elimination,
since `design.md`'s own §4c-equivalent table (lines 45-47) doesn't literally name "registered" in
either list — was disclosed at Phase 0/1 and is not a LOCKED-decision violation, since no LOCKED
decision states a category for this specific kind.

**(4) Remaining risks?**
- `display_name` is, and will remain, `null` for every real row until some future task adds an
  actual data source in `auth-service`'s own domain — the wiring is complete and tested end-to-end
  (Phase 11 Gap #6), but the underlying data gap is explicitly out of this task's scope and was
  disclosed as far back as T05.
- The four payment-derived mappings (`invoice.created`, `payment.seen`, `payment.finalized`,
  `receipt.issued`) are built but unreachable until `PaymentEventConsumer` (T07) exists — T07 remains
  skipped pending `payment-service`, a pre-existing, already-disclosed condition, not a new risk
  this task introduces.
- If a future caller's own `eventData` omits `sourceEventKey` entirely (unlike `AuthEventConsumer`,
  the only real caller today, which always supplies it), the per-channel `save()` falls back to a
  synthetic `"unknown:" + accountUuid` key rather than losing the row (Phase 8 Finding #4 / Phase 11
  Gap #1/#5) — a disclosed, tested, and now doubly-covered (pre-loop and per-channel) safety net,
  not an open gap.

## Verdict

**PASS** — T11 fully satisfies R10, R11, and every locked decision and acceptance criterion it
touches; both named tests exist and pass; the one real bug this task's own review pipeline
surfaced (Phase 11 Gap #1/#5) was fixed, not merely noted; the full suite is green at 239 tests,
0 failures, 0 errors.
