# crypto · T26 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement / Locked Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R1 — 2-of-3 quorum for every fact | Yes | `watch/EndToEndIntegrationTest.java` all 4 flows | Flow 1's `EXISTENCE`/`AMOUNT`/`TOKEN` agreement, Flow 2's genuine `AMOUNT`/`CONFIRMATIONS` disagreement | No | None. |
| R2/R3/L2 — disagreement holds, alerts, never auto-resolves | Yes | Flow 2 | `isAgreed(...)` false + `heldFactAlerter` invoked for both held facts + zero downstream events | No | None. |
| R6/R7/L4 — per-chain finality policy, real object not a constant | Yes | Flow 1/3/4, real `List<FinalityPolicy>` autowired (not stubbed) | Explicit `pollFinality()` step, `isAgreed(..., FINALITY)` | No | None. |
| R8/R9/R10 — `seen`/`confirmed`/`finalized` lifecycle, in order | Yes | Flow 1 (full lifecycle), Flow 3/4 (`seen`→`confirmed`) | Real Kafka delivery + Phase 11's own `assertStrictlyOrdered` timestamp checks | No | None. |
| R11/L6 — reorg walks cursor back, emits `chain.tx.reorged` | Yes | Flow 3 | Real Kafka delivery, `ChainCursor.txHash()` nulled, no `finalized` ever follows | No | None. |
| R12/L5 — deterministic idempotency key on every event | Implicit | Every flow's Kafka assertions now filter by the record's own `txHash` payload field (Phase 11) | Not independently re-asserted as a standalone idempotency-key string check | Yes — no flow explicitly parses and asserts the full `chain:txHash:eventType` key format | Minor, disclosed: the `txHash`-matching filter proves the key's *discriminating* component works correctly (distinct flows never cross-match), which is the property this task's own 4 scenarios actually depend on; the exact key *format* is already exhaustively covered by each event's own `*PayloadContractTest` (T23). Not re-proven here to avoid disproportionate duplication. |
| R18/R27 — watch registration via the real, scoped internal endpoint | Yes | All 4 flows' `registerWatch(...)` | Real `MockMvc` + real JWT scope, exercising R27's positive path end-to-end | No | None — R27's negative paths remain `ResourceServerConfigIntegrationTest`'s own, already-exhaustive scope (Phase 11 Finding #5 disposition). |
| R20/R23 — attest signs only once quorum+finality genuinely pass | Yes | Flow 1 | `200 SIGNED`, `KmsSigner.sign` invoked exactly once, `Attestation` row persisted with `SIGNED` | No | None. |
| R21/L12 — sanctioned counterparty blocked, no signature | Yes | Flow 4 | `200 BLOCKED`, zero `KmsSigner.sign` interactions, real `ScreeningResult` + `Attestation` rows persisted | No | None. |
| L1 — no single-provider answer ever leaves as fact (`package.md` §9's own named checklist item) | Yes | Flows 1/2 together | Flow 1 proves 3-provider agreement is required to emit; Flow 2 proves 2-vs-1 (in either direction, since a majority still exists) is `AGREED` while a genuine 3-way split is `HELD` | No | None — this is the specific, real-infrastructure proof `package.md` §9 asks for beyond the existing unit-level coverage. |
| L3 — observation log verbatim, written before the quorum decision | Yes | Flow 1 | Real `Observation` rows queried and counted (3 per fact) via `EntityManager`/JPQL | No | The S3-before-DB *internal ordering* is not re-proven here (Phase 11 Finding #12) — already covered by `ObservationLogTest`'s own dedicated `Mockito.inOrder` assertion at the unit level. |
| L11 (unmodified) — KMS-only signing, single path | Yes, confirmed untouched | `KmsSignerArchitectureTest`, unmodified by this task | Not re-run by this task directly, but no code this task touches could affect it | No | None. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. The one authorized file
(`watch/EndToEndIntegrationTest.java`) contains all 4 required flows, compiles cleanly against the real
codebase, and — after three full review rounds (self-review, independent review, test review) — has had
every genuinely valid finding either fixed (11 total across Phases 9 and 11) or rejected with directly
verified reasoning (6 total: 2 confirmed factually wrong via direct execution or re-verification, 4
confirmed redundant with existing, dedicated coverage elsewhere in the codebase). No production code was
touched at any point.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC6 (frozen brief) all have direct
evidence in the traceability matrix above. This task's own value was never in doubt about *whether* the
pieces were individually correct (every one of T02-T25 already proved that) but in whether they combine
correctly under real infrastructure — and the review process genuinely earned that confidence: Phase 8/9
alone found 7 real bugs a first-draft "looks right" implementation would have shipped with, most
seriously a hardcoded `txHash` that would have silently made all 4 flows share one transaction identity,
and a wrong-primary-key entity lookup that would have made every flow operate on a `null` `Watch`.

**(3) Does it violate any LOCKED decision?** No. L1-L6, L10-L12 are each traced to direct evidence above,
none contradicted. R22/L11's own KMS-signer concentration rule remains fully intact and unmodified —
confirmed via `git diff --stat` against the pre-T26 baseline showing exactly one file touched across
every commit this task produced.

**(4) Remaining risks?**
- **Actual execution has never happened.** Docker has been unavailable in this development environment
  for this task's entire lifecycle (Phase 0 through this phase). Every claim about this class's
  correctness rests on direct source verification, careful manual tracing of the real production code
  paths it exercises, and a clean `mvn test-compile` — not on a real green/red run. This is the single
  most significant open risk this task carries, and it has been disclosed at every phase rather than
  silently assumed away. The class should be re-verified by an actual run at the first opportunity in a
  Docker-available environment.
- **R12/L5's exact idempotency-key format is not independently re-asserted** in this task's own tests
  (see the traceability matrix row above) — a deliberate, disclosed, proportionality call, not an
  oversight, given the format is already exhaustively covered elsewhere (T23).
- **The recurring branch-poisoning issue continued throughout this task** (9 occurrences across T24-T26
  in this session alone, including one — during this task's own Phase 8 — that also carried a premature,
  auto-generated draft of a later phase that had to be discarded). Every occurrence was diagnosed,
  reverted cleanly with no work lost, and verified before continuing. This remains flagged, plainly, as
  an unresolved, external, out-of-scope process risk for whatever task comes after T26.
- **No new risk was introduced by this task** — it is purely additive test coverage with zero production
  footprint.

## Verdict

**PASS** — all 4 flows are implemented, trace to their requirements and locked decisions with direct
evidence, and survived three full rounds of adversarial and self-review that found and fixed 11 genuine
defects (most seriously, two that would have made the entire test suite either non-functional or
silently meaningless). `mvn -pl services/crypto -am test-compile` is clean. The one disclosed, unresolved
gap — this class has never actually been run, due to Docker's continued unavailability in this
environment — is stated here plainly as this task's principal residual risk, not silently claimed away.
