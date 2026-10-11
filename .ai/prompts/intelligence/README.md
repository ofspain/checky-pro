# AI prompt workflows — `intelligence-service`

One task folder per implementation task in [`spec/intelligence-service/tasks.md`](../../../spec/intelligence-service/tasks.md). Each folder holds the 14-phase prompt set and a README.

**Spec package:** [`spec/intelligence-service/`](../../../spec/intelligence-service/) — `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

**Tasks (30):**

| Task | Title | Section |
|---|---|---|
| [T01](T01/) | Module skeleton | Phase 0 — Skeleton |
| [T02](T02/) | Security baseline | Phase 0 — Skeleton |
| [T03](T03/) | Flyway baseline migration | Phase 0 — Skeleton |
| [T04](T04/) | ArchUnit boundaries | Phase 0 — Skeleton |
| [T05](T05/) | S3 WORM client wrapper | Phase 1 — Evidence upload & storage (R1-R5) — no crypto-service dependency |
| [T06](T06/) | Evidence upload endpoint | Phase 1 — Evidence upload & storage (R1-R5) — no crypto-service dependency |
| [T07](T07/) | Dedup by digest | Phase 1 — Evidence upload & storage (R1-R5) — no crypto-service dependency |
| [T08](T08/) | Evidence read endpoints | Phase 1 — Evidence upload & storage (R1-R5) — no crypto-service dependency |
| [T09](T09/) | Audit wiring for upload | Phase 1 — Evidence upload & storage (R1-R5) — no crypto-service dependency |
| [T10](T10/) | `TransactionLookupClient` interface + fake | Phase 2 — Automatic evidence collection (R6-R10) — **blocked on `spec/crypto-service` task 30** |
| [T11](T11/) | Bare-tx-hash submission endpoint | Phase 2 — Automatic evidence collection (R6-R10) — **blocked on `spec/crypto-service` task 30** |
| [T12](T12/) | Polling scheduler | Phase 2 — Automatic evidence collection (R6-R10) — **blocked on `spec/crypto-service` task 30** |
| [T13](T13/) | Collected-facts recording & reorg handling | Phase 2 — Automatic evidence collection (R6-R10) — **blocked on `spec/crypto-service` task 30** |
| [T14](T14/) | Audit wiring for polling | Phase 2 — Automatic evidence collection (R6-R10) — **blocked on `spec/crypto-service` task 30** |
| [T15](T15/) | `VisionExtractionClient` implementation | Phase 3 — AI evidence interpretation (R11-R14) — no crypto-service dependency |
| [T16](T16/) | Extraction pipeline | Phase 3 — AI evidence interpretation (R11-R14) — no crypto-service dependency |
| [T17](T17/) | Failure handling | Phase 3 — AI evidence interpretation (R11-R14) — no crypto-service dependency |
| [T18](T18/) | Response-shape enforcement | Phase 3 — AI evidence interpretation (R11-R14) — no crypto-service dependency |
| [T19](T19/) | In-house EXIF extraction | Phase 4 — Evidence integrity analysis (R15-R19) — no crypto-service dependency |
| [T20](T20/) | Perceptual hash + near-duplicate detection | Phase 4 — Evidence integrity analysis (R15-R19) — no crypto-service dependency |
| [T21](T21/) | Metadata-stripped finding | Phase 4 — Evidence integrity analysis (R15-R19) — no crypto-service dependency |
| [T22](T22/) | Scope-boundary test for R19 | Phase 4 — Evidence integrity analysis (R15-R19) — no crypto-service dependency |
| [T23](T23/) | `EvidenceGraph` creation/update | Phase 5 — Multi-evidence correlation (R20-R23) — depends on Phases 1-4's own data existing |
| [T24](T24/) | Disagreement recording | Phase 5 — Multi-evidence correlation (R20-R23) — depends on Phases 1-4's own data existing |
| [T25](T25/) | On-chain-wins response shaping | Phase 5 — Multi-evidence correlation (R20-R23) — depends on Phases 1-4's own data existing |
| [T26](T26/) | Evidence graph read endpoint | Phase 5 — Multi-evidence correlation (R20-R23) — depends on Phases 1-4's own data existing |
| [T27](T27/) | Outbox-published event | Phase 6 — Publishing, docs, contract & final hardening |
| [T28](T28/) | Full audit coverage | Phase 6 — Publishing, docs, contract & final hardening |
| [T29](T29/) | Auth/401 sweep | Phase 6 — Publishing, docs, contract & final hardening |
| [T30](T30/) | ADR + spec status bump | Phase 6 — Publishing, docs, contract & final hardening |

To execute a task, open its folder and run `00-…` through `13-…` in order with the model named in each file's header. See [`../../WORKFLOW.md`](../../WORKFLOW.md).
