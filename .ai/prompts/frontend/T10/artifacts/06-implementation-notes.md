# frontend · T10 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

## What was built

**`frontend/src/pwa/endSessionNavigationBypassesServiceWorker.test.ts`** (new, 14 cases) — and **nothing else**.
Gate decision D1 holds: `isDenylistedNavigation` reads `url.pathname`, `/connect/logout` is one of the eleven, so
every end-session URL shape was already declined by T09's policy. No acceptance criterion failed against the
current module, so there was no defect to report and no reason to touch it.

The file carries: the six D2 URL variants built from the backend's own shape; a synthetic JWT-shaped `ID_TOKEN`
(prefixed `fake.`); a spied fake cache that **records every key it is asked about**, so the token assertions are
over the recorded keys and not only the call counts; the D4 harness copy under a header comment naming T09's copy
and task 12's replacement; and groups AC1–AC7.

## Verification

| Check | Result |
|---|---|
| `vitest run src/pwa` (T09 + T10) | **75/75 pass** (61 + 14) |
| `vitest run` (whole frontend suite) | **223/224 pass** |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |

The one failure is the known environmental `lintRejectsHandWrittenBackendFetch` AC4 — this host's `oxlint@1.87.0`
prints no summary line; baselined in T07 both with changes stashed and against a real injected `fetch` violation.
Unchanged by this task, and never reported as passing.

## Deviations from the plan

1. **AC2's per-key scan was removed as dead code.** The plan had AC2 assert the key list is empty *and* loop over
   the keys checking none contains the token. After `expect(keys).toEqual([])`, that loop can never execute — a
   vacuous assertion that implies coverage it does not provide. AC2 now asserts the cache was never touched, the
   recorded key list is empty, and the joined key list contains no `id_token_hint`; the live per-key check moved to
   AC5, where a key actually exists. Noticed while re-reading the file after the first green run, not by a test
   failure.
2. **`APP_BASENAME` and `PUBLIC_ROUTE_PATHS` are imported** (D3) rather than parsed, as planned, and the import
   proved side-effect-free and fast under jsdom — no fallback needed.
