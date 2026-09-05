# AI prompt workflows — `crypto-service`

One task folder per implementation task in [`spec/crypto-service/tasks.md`](../../../spec/crypto-service/tasks.md). Each folder holds the 14-phase prompt set and a README.

**Spec package:** [`spec/crypto-service/`](../../../spec/crypto-service/) — `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

**Tasks (29):**

| Task | Title | Section |
|---|---|---|
| [T01](T01/) | Threat-model + skeleton & POM | Foundation |
| [T02](T02/) | Schema V1 | Foundation |
| [T03](T03/) | Config & resource server | Foundation |
| [T04](T04/) | Outbox & EventTopics | Foundation |
| [T05](T05/) | Adapter interface + fakes | Adapters, providers, quorum |
| [T06](T06/) | Ethereum adapter | Adapters, providers, quorum |
| [T07](T07/) | Tron adapter | Adapters, providers, quorum |
| [T08](T08/) | Observation log first | Adapters, providers, quorum |
| [T09](T09/) | Quorum evaluator | Adapters, providers, quorum |
| [T10](T10/) | Provider health + degraded | Adapters, providers, quorum |
| [T11](T11/) | Token allowlist + validator | Token, address, finality |
| [T12](T12/) | Address validation | Token, address, finality |
| [T13](T13/) | Address-poisoning detector | Token, address, finality |
| [T14](T14/) | Finality policies | Token, address, finality |
| [T15](T15/) | Watch registration API | Watchers, reorg, events |
| [T16](T16/) | Watcher layer | Watchers, reorg, events |
| [T17](T17/) | Seen/confirmed/finalized emission | Watchers, reorg, events |
| [T18](T18/) | Reorg detector | Watchers, reorg, events |
| [T19](T19/) | Screening client | Screening, attestation, key custody |
| [T20](T20/) | KMS signer — single path | Screening, attestation, key custody |
| [T21](T21/) | Attest endpoint | Screening, attestation, key custody |
| [T22](T22/) | Verification keys endpoint | Screening, attestation, key custody |
| [T23](T23/) | Contracts | Contracts, sidecar contract, hardening |
| [T24](T24/) | Sidecar-as-provider test | Contracts, sidecar contract, hardening |
| [T25](T25/) | ArchUnit/module boundaries | Contracts, sidecar contract, hardening |
| [T26](T26/) | End-to-end integration test | Final verification |
| [T27](T27/) | Run full suite | Final verification |
| [T28](T28/) | Threat-model closure | Final verification |
| [T29](T29/) | Bump spec status | Final verification |

To execute a task, open its folder and run `00-…` through `13-…` in order with the model named in each file's header. See [`../../WORKFLOW.md`](../../WORKFLOW.md).
