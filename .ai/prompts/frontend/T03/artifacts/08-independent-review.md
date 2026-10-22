# frontend · T03 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` / `libs/ts/api-client` |
| **Task** | T03 — Generated client |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Current `libs/ts/api-client/` source tree, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **Generated client.** `libs/ts/api-client` is generated from `contracts/api/auth.yaml` and compiles. Verifies `generatedClientCompiles`. Cites L6, R20.

---

## Review scope

Reviewed the Phase 6 T03 implementation against the frozen brief and `spec/frontend/agents.md`. No code was rewritten.

---

## Findings

### 1. CI workflow and drift step are missing
**Issue ·** The frozen brief requires `.github/workflows/ci.yml` to run the generator and fail on drift (`git diff --exit-code -- libs/ts/api-client`). No such workflow file exists in the repository.  
**Evidence ·** `glob .github/workflows/*` returns nothing; brief §Files to Modify: `.github/workflows/ci.yml`.  
**Recommendation ·** Create `.github/workflows/ci.yml` with the `frontend` job and the generator drift step as specified in the T01 and T03 frozen briefs. Without it, AC6 is not satisfied.  
**Confidence ·** High — a required deliverable is absent.

### 2. The T03 named test file does not exist yet
**Issue ·** `frontend/src/api/generatedClientCompiles.test.ts` is part of Phase 10 and has not been written. The production code is complete, but the named test `generatedClientCompiles` is missing.  
**Evidence ·** `glob frontend/src/api/*` returns nothing.  
**Recommendation ·** Complete Phase 10 and write the five test cases listed in the frozen brief, including the mutation breaks.  
**Confidence ·** High — expected workflow gap, but it is the remaining T03 deliverable.

### 3. `skipLibCheck: false` in `api-client/tsconfig.json` is brittle
**Issue ·** The api-client tsconfig sets `skipLibCheck: false`. This is stricter than the frontend tsconfig (`skipLibCheck: true`) and means any type error in `openapi-fetch`, `openapi-typescript-helpers`, or transitive typings will fail the api-client typecheck. The implementation notes claim the probe passed, but this setting increases fragility for a package whose only job is to re-export generated types and a thin wrapper.  
**Evidence ·** `libs/ts/api-client/tsconfig.json:9` (`"skipLibCheck": false`).  
**Recommendation ·** Either change to `"skipLibCheck": true` to align with the frontend config, or document why the stricter setting is required. The generated file's own type correctness is what matters, not the internal typings of `openapi-fetch`.  
**Confidence ·** Low — not a defect today, but a maintainability risk.

### 4. No `.gitattributes` guard for generated file line endings
**Issue ·** The frozen AC3 and the drift check require byte-for-byte reproducibility of `src/generated/auth.ts`. Without a `.gitattributes` rule, a developer on Windows or with misconfigured `core.autocrlf` could commit CRLF line endings, causing `git diff --exit-code` to fail in CI even when the generator output is semantically identical.  
**Evidence ·** No `.gitattributes` file exists; brief §AC3 requires byte-for-byte regeneration.  
**Recommendation ·** Add `libs/ts/api-client/src/generated/auth.ts text eol=lf` to `.gitattributes`, or normalise line endings in the generate script.  
**Confidence ·** Low — flakiness risk for the drift check.

### 5. No `typecheck` script in the api-client package
**Issue ·** The README explains how to typecheck the package with the project compiler, but `libs/ts/api-client/package.json` has only a `generate` script. This makes the documented check harder to run and harder to enforce.  
**Evidence ·** `libs/ts/api-client/package.json:8-10` (`scripts.generate` only); README lines 17–20.  
**Recommendation ·** Add a `typecheck` script: `"typecheck": "cd ../.. && frontend/node_modules/typescript/bin/tsc --noEmit -p libs/ts/api-client/tsconfig.json"` (or equivalent). This is optional for T03 but improves ergonomics and aligns with the frontend package.  
**Confidence ·** Low — usability, not acceptance.

### 6. No explicit assertion that problem-type schemas are generated
**Issue ·** `agents.md` requires RFC 9457 problem types to be exposed as generated types. The generated file likely contains them (e.g., `ProblemDetail`), but the brief and current code do not assert their presence. A contract change that removes or renames them would not fail T03.  
**Evidence ·** `libs/ts/api-client/src/generated/auth.ts` contains `components.schemas`; brief §AC4 checks `/accounts/me` GET and SAS prefix absence only.  
**Recommendation ·** Add a test case (Phase 10) or a source scan that asserts the generated `components.schemas` include the problem-detail schema name(s) declared in `auth.yaml`.  
**Confidence ·** Low — not required by the frozen T03 brief, but a standing-rule gap.

### 7. `createAuthClient` baseUrl is empty but not directly asserted in production code
**Issue ·** `index.ts` correctly passes `baseUrl: ''`, satisfying L9/L10. However, there is no runtime or static guard that prevents a future edit from changing this to an absolute URL. The Phase 10 test will assert it indirectly via a stubbed fetch, but the source itself could be clearer.  
**Evidence ·** `libs/ts/api-client/src/index.ts:8-9`.  
**Recommendation ·** Add a comment explaining why `baseUrl: ''` must remain empty and same-origin. The existing JSDoc already does this; no code change needed. Optional: add a test that reads `index.ts` and asserts it contains no `baseUrl` value other than `''`.

**Confidence ·** Low — the Phase 10 test and source scan already cover this indirectly.

---

## Open Questions

- None blocking. The missing CI workflow (finding 1) and Phase 10 test file (finding 2) are the remaining T03 deliverables.

---

## Summary

The `libs/ts/api-client` package is implemented according to the frozen brief: it is generated from `auth.yaml`, exports a same-origin `openapi-fetch` client, and has no env keys or absolute URLs. The main gaps are the missing CI workflow (which also blocks T01 AC5/T03 AC6) and the missing Phase 10 test file. The remaining findings are low-severity maintainability observations (`skipLibCheck`, `.gitattributes`, `typecheck` script, problem-type assertion, baseUrl guard).