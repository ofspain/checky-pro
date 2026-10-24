# frontend · T08 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T08 — No POST replay |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Current `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `spec/frontend/agents.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **No POST replay.** A 429 on a non-idempotent POST is never retried automatically. Verifies `rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23.

---

## Review scope

Reviewed the Phase 6 T08 test additions against the frozen brief, `agents.md`, and the Phase 7 self-review. No production code was changed; the review covers only the shared test file.

The T08 additions are comprehensive and correctly grouped under a nested `describe('no POST replay')`. The self-review found seven issues, all low or informational. This review corroborates them and adds two fresh observations.

---

## New findings

### 1. The table-driven POST cases don't assert the request body is preserved exactly
**Issue ·** The `PUBLIC_POSTS` and `PROTECTED_POSTS` `it.each` cases assert `sent[0]!.body` is not empty and that the call count is one, but they do not compare the recorded body to the JSON the case sent. A mutant that silently swapped or dropped the body while still sending exactly one request would pass these cases. AC2 says "the recorded request bodies show each body sent once."  
**Severity ·** Low — the AC3 re-send case compares bodies for one operation, and the AC8 privacy case uses a marker, so a body-swap bug would likely still be caught.  
**Evidence ·** `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx:796-829` (the two `it.each` tables).  
**Recommendation ·** Add a `body` field to `PostCase` and assert `sent[0]!.body` equals the expected JSON string (or `toContain` the marker value). This makes AC2 explicit for every driven operation.

### 2. `repoRoot` is computed at module load time from `expect.getState().testPath`
**Issue ·** The drift guard computes `repoRoot` once at the top level of the module using `expect.getState().testPath`. Vitest currently sets this before the file is executed, so it works, but it couples the guard to Vitest's import order and to the file's depth in the tree. A runner change or file move would break the guard with a confusing failure.  
**Severity ·** Low — stable in the current Vitest version and file location.  
**Evidence ·** Test file line 23: `const repoRoot = dirname(dirname(dirname(dirname(expect.getState().testPath as string))));`.  
**Recommendation ·** Compute the repo root inside the AC7 test using `import.meta.dirname` or `process.cwd()` (with a small check that `contracts/api/auth.yaml` exists), or wrap the current computation in a helper that is called from the test rather than at module load. This removes the implicit Vitest-state dependency.

---

## Corroborated findings (from Phase 7 self-review)

| # | Self-review finding | Independent view |
|---|---|---|
| 1 | `installTransport` body field makes a T07 helper shared with T08 | **Confirmed, acceptable.** One shared recorder inside a single file is better than duplication. The helper change is backward-compatible with T07's cases. |
| 2 | `'<uncloneable>'` sentinel conflates unreadable with empty | **Confirmed, acceptable.** The sentinel is test-only and the call count is what AC4 is about. Empty bodies are distinguishable because the sentinel is not `''`. |
| 3 | Drift guard fails on POST removal as well as addition | **Confirmed, acceptable.** A removed POST is a contract change that should fail loudly; T03's drift check and the generated client also guard it. |
| 4 | Contract parse is line-based | **Confirmed, acceptable for `auth.yaml`.** The contract is generated flat. Worth revisiting only if `contracts/` is split. |
| 5 | Ten POST operations accounted for but not driven | **Confirmed, acceptable.** The boundary and notice are operation-agnostic; the drift guard ensures new POSTs cannot escape. |
| 6 | AC6 timer assertion runs unmounted | **Confirmed, inherited caveat.** T07's mounted AC4 case already proves an hour of faked time clears nothing. |
| 7 | Most T08 cases don't assert `failures` is empty | **Confirmed, minor.** Adding `expect(failures).toEqual([])` to the two `it.each` tables is a cheap win. |

---

## Non-gaps (acknowledged, no change needed)

- **AC3 boundary is pinned:** the test comment explains why two sends (401-driven re-send + 429 final) are correct and a third is forbidden.
- **No replay path exists:** the notice middleware has only `onResponse` (asserted by AC5), and the boundary returns early for non-401 statuses.
- **No resumption:** AC6 uses fake timers and dismissal; nothing sends a second request.
- **Privacy:** AC8 uses a marker body and asserts it reaches only the transport.
- **T05 parity:** AC9 asserts a POST 401 renews once and re-sends once to a 201.
- **Drift guard:** AC7 reads `contracts/api/auth.yaml` and verifies every POST is accounted for.
- **L12 respected:** T07's 29 cases are unedited, no production file touched, T05/T06/T07 suites unchanged.
- **Mutation coverage:** 18/18 breaks caught, including four T08-specific ones.

---

## Summary

The T08 test additions are solid: they cover public and protected POSTs, the 401→renewal→429 boundary, uncloneable bodies, structural middleware shape, no-timer/no-resumption, contract drift, POST-body privacy, and T05 parity. The two fresh findings are minor: add exact body assertions to the table-driven cases, and make the drift guard's repo-root computation less dependent on Vitest module state. The self-review's recommendation to assert `failures` is empty in the two `it.each` tables is also worth applying in Phase 9.
