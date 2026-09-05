# AI prompt workflows — `payment-service`

One task folder per implementation task in [`spec/payment-service/tasks.md`](../../../spec/payment-service/tasks.md). Each folder holds the 14-phase prompt set and a README.

**Spec package:** [`spec/payment-service/`](../../../spec/payment-service/) — `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

**Tasks (29):**

| Task | Title | Section |
|---|---|---|
| [T01](T01/) | Service skeleton & POM | Foundation |
| [T02](T02/) | Schema V1 | Foundation |
| [T03](T03/) | Config & resource server | Foundation |
| [T04](T04/) | Outbox & EventTopics | Foundation |
| [T05](T05/) | Invoice domain | Invoices |
| [T06](T06/) | Invoice API + watch registration | Invoices |
| [T07](T07/) | Merchant-scoped reads | Invoices |
| [T08](T08/) | Invoice expiry sweep | Invoices |
| [T09](T09/) | Payment state machine | State machine & consumers |
| [T10](T10/) | Verification record | State machine & consumers |
| [T11](T11/) | Idempotent consumers | State machine & consumers |
| [T12](T12/) | Reorg handling | State machine & consumers |
| [T13](T13/) | Amount & poisoning integrity | State machine & consumers |
| [T14](T14/) | Idempotency & ordering tests | State machine & consumers |
| [T15](T15/) | Crypto client | Attestation, receipts, and ledger |
| [T16](T16/) | Receipt digest (Q5) | Attestation, receipts, and ledger |
| [T17](T17/) | Receipt issuance — FINALIZED only | Attestation, receipts, and ledger |
| [T18](T18/) | S3 WORM store | Attestation, receipts, and ledger |
| [T19](T19/) | Hash-chain ledger | Attestation, receipts, and ledger |
| [T20](T20/) | Ledger verifier | Attestation, receipts, and ledger |
| [T21](T21/) | Daily anchor job (Q4) | Attestation, receipts, and ledger |
| [T22](T22/) | Tax export (Q6) | Exports, contracts, hardening |
| [T23](T23/) | RFC 9457 errors | Exports, contracts, hardening |
| [T24](T24/) | Contracts | Exports, contracts, hardening |
| [T25](T25/) | ArchUnit/module boundaries | Exports, contracts, hardening |
| [T26](T26/) | End-to-end integration test | Final verification |
| [T27](T27/) | Run full suite | Final verification |
| [T28](T28/) | Threat-model check | Final verification |
| [T29](T29/) | Bump spec status | Final verification |

To execute a task, open its folder and run `00-…` through `13-…` in order with the model named in each file's header. See [`../../WORKFLOW.md`](../../WORKFLOW.md).
