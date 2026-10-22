# frontend · T03 · Phase 7 — Self-Review

Date: 2026-10-22.

| # | Severity | Observation | Handling |
|---|---|---|---|
| 1 | Low | Install pulled in 33 packages. Only `openapi-fetch` (with `openapi-typescript-helpers`) is a runtime dependency; the rest are the generator's dev tree. | Accept. Nothing reaches the bundle until app code imports the client. |
| 2 | Low | The workspace has two `tsc` binaries. `pnpm -C libs/ts/api-client exec tsc` would run 5.9, not 7. | Fixed: the README says how to check the package with the project compiler. Test case 2 calls the frontend's TS 7 by path. |
| 3 | Info | `createAuthClient()` creates a new client on each call. | No change. The T05 wrapper decides the client's lifetime and middleware. |
| 4 | Info | The generated file carries the contract's descriptions as JSDoc. | No change. It is generated output. |
| 5 | Info | The CI drift step runs before the tests, so a stale client fails fast with a clear diff. | — |
| 6 | Info | `typescript@5.9.3` is 13 months old but is the latest 5.x. The generator's peer is `^5.x`. | Accept. |

No blocker. The change matches the brief's Files sections. The named test and the mutation breaks follow in Phase 10.
