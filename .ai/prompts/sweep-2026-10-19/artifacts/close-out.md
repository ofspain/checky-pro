# open-issues sweep · Close-out (2026-10-21)

The author asked to close everything still open without another Kimi round, and move to the frontend. This records what closed the last items. The review trail is in this folder (07–11) and in `citext/`.

## Closed in the final pass

| Item | How it closed | Guarding tests | Deliberate breaks |
|---|---|---|---|
| No test runs crypto and payment together | **By contract instead of a harness.** Both sides are pinned to the same files. Crypto checks its emitted chain events against `contracts/events/chain/*.schema.json` (`*PayloadContractTest`) and its handlers against `contracts/api/crypto-internal.yaml` (`CryptoInternalOpenApiContractTest`). New payment `CryptoContractConformanceTest` requires every type payment exchanges with crypto (4 chain events, 4 internal API bodies) to have exactly the contract's fields. The behaviour across the boundary is proven in halves (crypto `EndToEndIntegrationTest`, payment `PaymentEndToEndIntegrationTest`). | `CryptoContractConformanceTest` (8) | 2 (a schema field added, a contract field renamed), both caught |
| Crypto task 30, on-demand tx-hash lookup (R29, L16) | Built. `GET /internal/v1/transactions/{chain}/{txHash}` asks all 3 providers via `getTx` and decides every fact with the existing `QuorumEvaluator`. Read-only. Behaviour the §4c line left open (503, disputed absence is `HELD`, no facts on `HELD`) is recorded in the crypto `design.md` note. | `TransactionLookupServiceTest` (11), `TransactionLookupControllerTest` (5), `CryptoInternalOpenApiContractTest.transactionLookupResponseMatchesTheDesignSpecFieldsExactly`, handler count 4 → 5 | 6 (evaluator bypassed, any absence 404, absent provider counted, token check inverted, provider error swallowed, extra response field), all caught |
| One database per service (citext part 2) | There is no deployment code yet (`infra/stacks` is empty), so it is recorded as a requirement of the `data` stack in `infra/README.md`. Both services' Flyway callbacks refuse a shared database. `services/notification/README.md` now documents its own local database. Existing shared local setups keep working. | `AuthCitextCallbackIntegrationTest`, `NotificationBaselineMigrationIntegrationTest` | see `citext/artifacts/09-review-resolution.md` |
| Auth spec note for the citext guard | Dated note in `spec/auth-service/design.md` (before §5), including D-005. | — | — |
| Frontend Q2 (waiting on `payments.yaml`) | Closed with dated notes in `spec/frontend/package.md` (§11 Q2 and the phase table) and `requirements.md` (Phase 1b). The contract landed in payment T24, and the sweep added the receipt read. | — | — |

## Still out of scope by earlier author decision

- payer notification (FU-T07-1)
- the R26 on-chain anchor (payment T21)
- infrastructure alerting and IAM
- the deployment itself: writing the CDK `data` stack

## Suite state

`mvn -o -pl services/<service> verify`, all BUILD SUCCESS, 0 failures (test cases in the Surefire reports): auth 787, crypto 811, notification 463, payment 579.
