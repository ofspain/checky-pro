# notification · T20 · Phase 6 — Implementation Notes

Consumes:
- `artifacts/04-frozen-task-brief.md`
- `artifacts/05-implementation-plan.md`

Verification: `mvn -o -pl services/notification verify` → **419/419, BUILD SUCCESS** (2026-10-19). That is the previous 413, plus 2 new integration tests and 4 guards. No production code changed.

## What changed

### Spec (sanctioned by D1)

**`spec/notification-service/package.md`**
- **Header:** Version is now `0.2`. Status is now `READY FOR IMPL — implemented and verified through tasks 1–20 (2026-10-19). Disclosed gaps: …`. The disclosure names FU-T07-1, FU-T07-2 / payment FU-T29-1, the L2 interpretation and FU-T07-4, and points to §9 items 1–3 and §11 Q4/Q7. No other header row changed.
- **§9:** items 1–3 are `[ ] **Partial —**`, and items 4–11 are `[x]` with test citations.
  - Item 1 states what the two drifted named tests actually prove (Kimi F4).
  - Item 1 also explains the L11 rule-field-plus-canary shape.
- **§11:** a dated **Resolved** note is appended to each of Q1–Q7, citing real classes with Kimi's wrong names corrected (F6). Q4 is marked partial (provisional paths, no receipt link). Q7 is marked as deviating from §4c (merchant only, `user.suspended` unmapped, no late-route retry). Each original question sentence is unchanged.

**`spec/notification-service/design.md`**
- **L2:** a dated interpretation sub-bullet (T07 D2).
- **O1, O2, O4, O5 and O6:** dated **Resolved** suffixes. **O6 is beyond the brief's list (O1/O2/O4/O5)**: the template-engine decision was equally settled at T09, and leaving it "open" would have been the same kind of stale entry. This is disclosed for Kimi Phase 8.
- **§4c:** a dated "Recipients as shipped" blockquote directly after the mapping block. The four fenced blocks are byte-identical; their SHA-256 `9a9ba0ff…` was taken before editing and pinned.

**`spec/notification-service/requirements.md`**
- Dated italic note sub-bullets under R3 (partial), R4 and R5 (partial).
- No requirement sentence changed.

### Tests (D3)

| Test | What it does |
|---|---|
| `common/SecretLeakEndToEndIntegrationTest.shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs` | §8's R15 name. Its own Kafka container and Postgres; the real path from `auth.email.requested(password_reset)` through the orchestrator, renderer and both channels. A thread-safe root-logger appender captures every line, with `com.themistra` set to DEBUG, including throwable text. It asserts the token appears exactly once in the email body, as `…/reset-password?token=<token>`, and never in the subject, the in-app title/body/link, `delivery_log.error_detail`, or any captured log line. A positive control checks the capture really saw this service's own "Email sent" line. |
| `delivery/PaymentEmailOptOutIntegrationTest.shouldSuppressOptedOutPaymentEmailButStillDeliverInApp` | T17's buildable payment half. The merchant has a contact and a `PAYMENT:EMAIL` opt-out; `payments.invoice.created` produces exactly `EMAIL:SUPPRESSED` and `IN_APP:SENT`, both keyed `invoice.created:<uuid>`. No email is handed to the transport, and one `PAYMENT` in-app row shows `1.5`. |
| `T01SkeletonRegressionTest` (+4) | `notificationSpecIsBumpedToReadyForImplVersionZeroPointTwo` (header table only, one row each, disclosure IDs present). `everyTestNamedInTheVerificationChecklistExists` (≥ 25 `Class.method` citations, each a JUnit test in that class's file, or an `ArchRule` field the class `.check(`s). `verificationChecklistKeepsItsPartialItemsUnticked` (items 1–3 partial with their reasons, 4–11 ticked, exactly 8 ticked). `verbatimSection4cIsUnchanged`. |

## Mutation checks

Each mutation was applied, run and restored, and every restore was confirmed byte-identical.

| # | Mutation | Killed by |
|---|---|---|
| T1 | `EmailChannel` logs the body | the R15 test |
| T2 | `PreferenceResolver` ignores the stored opt-out | the opt-out E2E |
| T3 | Tick §9 item 2 | the partial-items guard |
| T4 | Change one §4c fence row's recipients | the §4c hash |
| T5 | Cite a non-existent test | the citations guard |
| T6 | Revert the version to `0.1` | the header guard |

All six were killed.

## Deviations and disclosures

1. **O6 annotated** beyond the brief's list (see above).
2. **What the §4c pin covers.** It covers the fenced blocks only, not the prose. That is the Phase 5 clarification: the heading and the `chain.*` sentence are not hashed.
3. **R15's "full API keys".** No consumed event carries an API key, so the R15 test exercises the token path only. API-key redaction stays covered by `SecretSafeLoggingTest.redactMasksApiKeyAndApiUnderscoreKeyParameters` (T10).
4. **T17's remaining payment parts** (both parties, receipt link) stay unbuildable (D1/D3 of T07). They are disclosed in §9 item 1 and the header.
5. **Build time.** Each of the two new integration tests starts its own Kafka container (the T07 isolation pattern), adding about 20 s to the suite.

## Open Questions

None blocking.
