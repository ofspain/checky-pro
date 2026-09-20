# AI prompt workflows — `notification-service`

One task folder per implementation task in [`spec/notification-service/tasks.md`](../../../spec/notification-service/tasks.md). Each folder holds the 14-phase prompt set and a README.

**Spec package:** [`spec/notification-service/`](../../../spec/notification-service/) — `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

**Tasks (20):**

| Task | Title | Section |
|---|---|---|
| [T01](T01/) | Service skeleton & POM | Foundation |
| [T02](T02/) | Schema V1 | Foundation |
| [T03](T03/) | Config & resource server | Foundation |
| [T04](T04/) | Idempotency ledger | Consumers & idempotency |
| [T05](T05/) | Contact projection (Q1/O1) | Consumers & idempotency |
| [T06](T06/) | Auth event consumer | Consumers & idempotency |
| [T07](T07/) | Payment event consumer | Consumers & idempotency |
| [T08](T08/) | Preference resolver | Preferences, rendering, delivery |
| [T09](T09/) | Template renderer | Preferences, rendering, delivery |
| [T10](T10/) | Secret-safe rendering & logging | Preferences, rendering, delivery |
| [T11](T11/) | Delivery orchestrator + log | Preferences, rendering, delivery |
| [T12](T12/) | Email channel (O2/Q2) | Preferences, rendering, delivery |
| [T13](T13/) | In-app channel + store | In-app channel & retry |
| [T14](T14/) | Bounded retry | In-app channel & retry |
| [T15](T15/) | Consumed-contract tests | Contracts & hardening |
| [T16](T16/) | ArchUnit/module boundaries | Contracts & hardening |
| [T17](T17/) | End-to-end integration test | Final verification |
| [T18](T18/) | Run full suite | Final verification |
| [T19](T19/) | Dispute-log check | Final verification |
| [T20](T20/) | Bump spec status | Final verification |

To execute a task, open its folder and run `00-…` through `13-…` in order with the model named in each file's header. See [`../../WORKFLOW.md`](../../WORKFLOW.md).
