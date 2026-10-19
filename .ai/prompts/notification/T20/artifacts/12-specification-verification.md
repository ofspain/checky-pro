# notification · T20 · Phase 12 — Specification Verification

Consumes:
- artifacts `00`–`11` (including `11-test-review-resolution.md`)
- `spec/notification-service/{requirements,design,tasks}.md`

Final state: commit `5d805c65`; `mvn -o -pl services/notification verify` → **422/422, BUILD SUCCESS**. Mutations T1–T8 and T10–T12 were all killed; the should-pass control T9 passed.

## Traceability matrix

| Requirement | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **T20-3** (`tasks.md:41`): `DRAFT` → `READY FOR IMPL`, `0.1` → `0.2` | Yes | `package.md` header (Version row, Status row) | `notificationSpecIsBumpedToReadyForImplVersionZeroPointTwo` (T6 killed) | — | The Status carries a disclosure sentence after the value (D1, the payment T29 form) |
| **T20-2**: tests pass | Yes | The full `verify` passes, 422/422 | Suite | — | — |
| **T20-1**: §11 questions (esp. Q1–Q4, Q7) closed | Yes, with disclosed partials | `package.md` §11: dated Resolved notes on Q1–Q7, with real class citations (F6). Q4 is *partial*: the paths are provisional and there is no receipt link. Q7 *deviates from §4c*: merchant only, `user.suspended` unmapped. | Manual (the guard was rejected as F6 / Gap 1) | Q4's link paths need the frontend to confirm its routes. The receipt link waits on payment FU-T29-1. | **D1**: the human accepted "closed with disclosed partials" as meeting the gate |
| §9 checklist reflects reality (frozen-brief AC3) | Yes | Items 4, 5 and 7–11 are `[x]` with citations. Items 1–3 and 6 are `[ ] **Partial —**` with reasons. | `everyTestNamedInTheVerificationChecklistExists` (T5); `verificationChecklistKeepsItsPartialItemsUnticked` (T3) | — | Item 6 became partial at Phase 9 (FU-T20-1) |
| §8 named tests exist (item 1) | Yes | All 20 names are enabled tests; L11 is a checked `ArchRule` field | `everyTestNamedInTheTestPlanExists` (T7, T8, T12; control T9) | — | R15's name was missing before T20 and was added |
| Annotations only; §4c verbatim (D2) | Yes | Dated notes in `design.md` (L2, O1/O2/O4/O5/O6, §4c) and `requirements.md` (R3–R5). The fence hash is unchanged. | `verbatimSection4cIsUnchanged` (T4); `specKeepsItsDatedDeviationNotesBesideTheTextTheyQualify` (T10, T11) | — | O6 was annotated beyond the brief's list (disclosed, unchallenged) |
| R15 named test (D3) | Yes | `common/SecretLeakEndToEndIntegrationTest` | Both methods; T1 killed | — | Exposed FU-T20-1 (the in-app verify body carries its token inside the link) |
| T17's buildable payment half (D3) | Yes | `delivery/PaymentEmailOptOutIntegrationTest` | `shouldSuppressOptedOutPaymentEmailButStillDeliverInApp`; T2 killed | T17's "both parties" and "receipt link" parts stay unbuildable | Disclosed in §9 item 1 and the header |
| No production change (AC7) | Yes | The T20 diff covers only `spec/notification-service/*` (3 files) and `services/notification/src/test/**` (3 files) | — | — | — |

## Principal-engineer answers

1. **Is the task fully complete?**
   - Yes. The spec is `0.2` / `READY FOR IMPL`.
   - Every open question carries a dated resolution, and every checklist item is either evidenced or honestly partial.
   - The bump is pinned by guards that fail on any silent regression of the status, the checklist, the named-test list, §4c or the deviation notes.

2. **Does it satisfy every acceptance criterion?**
   - AC1 and AC3–AC7: yes, each guarded by tests that a mutation kills.
   - AC2 (the dated resolutions): yes, with manual verification only. A guard for it was rejected twice.

3. **Does it violate any LOCKED decision?**
   - No. §4c's verbatim artifacts are byte-identical.
   - L2's sentence is unchanged, with its human-granted interpretation recorded directly under it.
   - `agents.md` is referenced, not restated.

4. **Remaining risks:**
   - **"READY FOR IMPL" with four partial §9 items.** A reader skimming only the status word may assume full closure. The disclosure sits in the same cell for that reason (Kimi F3).
   - **FU-T20-1.** The raw one-time verification token is stored in `inapp_notifications.body` and shown in-app. It is recipient-scoped behind JWT, but it is still a token at rest. This should be scheduled soon. The pinned test fails clearly when it is fixed.
   - **Carried from T07:**
     - FU-T07-1: payer notification
     - FU-T07-2 / payment FU-T29-1: the receipt link
     - FU-T07-4: late-route retry
     - S2: lost decimals
     - Q4's provisional link paths
   - **The §11 wording is unguarded.** It relies on review.
   - **Build time.** About 40 s added for the two isolated Kafka containers.

**Verdict: PASS.** The spec is bumped to `READY FOR IMPL` / `0.2` exactly as the task requires. The gate was met with disclosed partials, as the human decided (D1), and every claim is backed by a guard or cited evidence (422/422, 11 mutations killed). The remaining gaps are owned by named follow-ups.
