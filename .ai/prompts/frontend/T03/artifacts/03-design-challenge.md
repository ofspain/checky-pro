# frontend · T03 · Phase 3 — Design Challenge

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T03 — Generated client**. Findings are against the brief itself; no code was changed.

---

## Findings

### 1. The TypeScript version split is a hidden long-term dependency
**Issue:** The generator (`openapi-typescript@7.13.0`) requires TypeScript 5.x because it uses `ts.factory`, while the project compiler is TypeScript 7.0.2. The probe confirmed the generated output compiles under TS 7.0.2, but this is an implicit compatibility guarantee, not a locked decision. A future `auth.yaml` change or a patch release of `openapi-typescript` could produce TS 5-specific output that TS 7 rejects.  
**Severity:** Medium — the task depends on a cross-version compatibility assumption that is not declared as a constraint.  
**Evidence:** Brief §Dependencies table: “The probe showed the generator **fails under TS 7.0.2** … TS 7 exposes no `ts.factory`.” Brief §Constraints: “`typescript@5.9.3` is a devDependency of the api-client only and is never imported by app code.”  
**Recommended brief amendment:** Add an explicit constraint: “The generator output must remain parseable and type-correct under TypeScript 7.0.2 `strict`. If a generator or contract change breaks this, the generator tooling must be upgraded or pinned before the contract change lands.”

### 2. No explicit requirement that RFC 9457 problem types are generated
**Issue:** `agents.md` states errors are RFC 9457 `application/problem+json` and that problem types are exposed as generated types. The brief mentions this under Locked Decisions but does not include an acceptance criterion or test that verifies problem-response schemas are present in the generated output.  
**Severity:** Medium — a contract change that removes or renames problem schemas would silently violate `agents.md`.  
**Evidence:** Brief §Locked Decisions: “agents.md, ‘API access’: RFC 9457 problem types are exposed as generated types.” Brief §AC4 only checks `/accounts/me` GET and SAS prefix absence.  
**Recommended brief amendment:** Add AC4c: “The generated `components.schemas` include the problem-type schemas declared in `auth.yaml` (e.g., `ProblemDetail` or equivalent), and the test asserts their presence by name.”

### 3. CI drift check covers the whole package directory, not just the generated file
**Issue:** The proposed CI step is `git diff --exit-code -- libs/ts/api-client`. This will fail if any hand-written file in the package changes (e.g., `index.ts`, `README.md`, `package.json`), not just if `src/generated/auth.ts` drifts. In a PR that intentionally edits `index.ts`, the step would need to be skipped or scoped.  
**Severity:** Low — CI will fail on legitimate package changes unless the check is scoped.  
**Evidence:** Brief §Files to Modify: `.github/workflows/ci.yml`: `git diff --exit-code -- libs/ts/api-client`.  
**Recommended brief amendment:** Scope the drift check to the generated file only: `git diff --exit-code -- libs/ts/api-client/src/generated/auth.ts`.

### 4. No test that `openapi-fetch` middleware is reachable
**Issue:** The brief chooses `openapi-fetch` partly because it has a middleware hook for T05–T08. However, none of the required tests verifies that the exported client type exposes `use` or an equivalent middleware hook. If a future generator/client change removed or renamed the hook, T05–T08 would be blocked.  
**Severity:** Low — not needed for T03 acceptance, but the choice of library is justified by this capability.  
**Evidence:** Brief §Dependencies: “`openapi-fetch` … has a middleware hook for T05–T08.” Brief §Required Tests: no middleware assertion.  
**Recommended brief amendment:** Add a test case that calls `createAuthClient().use(...)` (or the equivalent API) and type-checks; it does not need to run a real request.

### 5. Byte-for-byte drift check is brittle to benign generator changes
**Issue:** AC3 and the first test case require the regenerated file to be byte-identical to the committed file. Even a patch release of `openapi-typescript` that only reorders unions or reflows whitespace would fail this check, even if the types are semantically identical.  
**Severity:** Low — versions are pinned, so this is unlikely in the short term; however, the exact-version pin is the only defense.  
**Evidence:** Brief §AC3: “regenerating from `auth.yaml` reproduces `src/generated/auth.ts` byte for byte.” Brief §Dependencies: exact versions.  
**Recommended brief amendment:** Keep the byte-for-byte check as the primary guard, but document the escape hatch: “If a generator patch changes formatting only, the committed file may be regenerated and committed in a standalone lockfile-only change; semantic diff is not accepted for contract changes.”

### 6. No test for line-ending consistency of the generated file
**Issue:** The byte-for-byte check can fail if the generator emits different line endings on macOS vs. Linux (e.g., during local regeneration vs. CI). The probe was run in a container, but a developer running the generator on macOS could produce CRLF or mixed endings.  
**Severity:** Low — flakiness risk for the drift check.  
**Evidence:** Brief §AC3 and §Required Tests case 1.  
**Recommended brief amendment:** Add a `.gitattributes` entry: `libs/ts/api-client/src/generated/auth.ts text eol=lf`, and/or normalise line endings in the generate script before writing the file.

### 7. No test that the client is the sanctioned import path
**Issue:** T04 will add a lint rule rejecting handwritten backend `fetch`. T03 establishes the client as the sanctioned path, but the brief does not assert that `frontend/src/` imports backend types only from `@themistra/api-client`. A screen task could accidentally import a raw path type from the generated file.  
**Severity:** Low — T04 owns the lint rule, but T03 could add a weaker static check now.  
**Evidence:** Brief §Scope Out: “The lint rule (T04).”  
**Recommended brief amendment:** Optional: add a test case that every `frontend/src/**/*.ts(x)` import matching `@themistra/api-client` resolves through the package entry (`libs/ts/api-client/src/index.ts`) and not through a deep import into `src/generated/`. Defer to T04 if preferred.

### 8. Source export assumes the consumer transpiles the package
**Issue:** The brief specifies `exports: { ".": "./src/index.ts" }` with no build step. This works for Vite and the frontend TypeScript project, but any other workspace package or Node script that imports `@themistra/api-client` would need its own transpiler.  
**Severity:** Low — out of current scope, but the package is in `libs/ts/` where other consumers may exist.  
**Evidence:** Brief §Files to Create: `exports: { ".": "./src/index.ts" }`, “a source export with no build step.”  
**Recommended brief amendment:** Add a note: “Source export is acceptable because the only current consumer is the Vite frontend. If another package needs to import `@themistra/api-client`, a build step must be added in a separate task.”

### 9. No test verifying the generated operations count/list
**Issue:** The probe noted 34 operations in the generated file, but the brief does not include an acceptance criterion that the generated file contains the expected set of operations. A malformed generator run could drop or duplicate operations while still producing a valid file.  
**Severity:** Low — the `/accounts/me` check and drift check provide partial coverage.  
**Evidence:** Brief §Dependencies: “34 operations.” Brief §AC4 only checks one path.  
**Recommended brief amendment:** Optional: add a test that counts the generated `operations` and verifies it matches the number of operations in `auth.yaml`, or at least asserts it is non-zero and above a minimum.

### 10. `createAuthClient` baseUrl is only indirectly tested
**Issue:** AC5 says a stubbed `fetch` receives `/accounts/me`, which assumes `baseUrl: ''`. There is no direct assertion that `createAuthClient` passes an empty `baseUrl` to `createClient`. A regression that changed `baseUrl` to `'/'` or omitted it might still pass the fetch test if the path stayed relative, but would violate the L9/L10 “same origin, no absolute URL” rule.  
**Severity:** Low — the source scan covers `import.meta.env` and `https?://`, but not an empty-string check.  
**Evidence:** Brief §AC5: “a `createAuthClient().GET('/accounts/me')` call, with `fetch` stubbed, requests the same-origin path `/accounts/me`.”  
**Recommended brief amendment:** Add an assertion in the test case that inspects the client options or directly checks `index.ts` contains `baseUrl: ''` (or `baseUrl: ""`).

---

## Open Questions

- None that block freezing. The TypeScript version split (finding 1) is the most significant long-term risk and should be tracked explicitly.
