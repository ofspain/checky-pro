# frontend · T03 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` / `libs/ts/api-client` |
| **Task** | T03 — Generated client |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **Generated client.** `libs/ts/api-client` is generated from `contracts/api/auth.yaml` and compiles. Verifies `generatedClientCompiles`. Cites L6, R20.

---

## Method

Reviewed the Phase 10 test manifest for `generatedClientCompiles` (5 cases + mutation breaks) against the frozen T03 acceptance criteria (AC1–AC6) and the named test requirement in `package.md` §8. No code was rewritten.

---

## Gaps

### 1. `components` re-export is not tested
**Gap ·** The brief requires `src/index.ts` to re-export `paths`, `components`, and `operations`. The test manifest checks `paths` (case 3) and `operations` (case 3), but never asserts that `components` is re-exported.  
**Why it matters ·** A missing `components` export would not fail any current case, yet later tasks (error handling, form validation) will import response/error component types from `@themistra/api-client`.  
**Suggested test ·** Add a runtime assertion that `components` is exported from `@themistra/api-client` and is a non-empty object, or that a known schema name (e.g., the account status response schema) is reachable through the package entry.

### 2. No assertion that problem-type schemas are generated
**Gap ·** `agents.md` requires RFC 9457 `application/problem+json` types to be exposed as generated types. None of the five cases checks for the presence of problem-detail or problem-type schemas in the generated output.  
**Why it matters ·** A contract change that removes or renames problem schemas would silently violate a standing rule, and T05–T08 would have no typed error bodies to map.  
**Suggested test ·** Extend case 3 to scan the generated `components.schemas` keys for the problem-schema names declared in `auth.yaml` (e.g., `ProblemDetail`, `ValidationProblem`) and assert they exist.

### 3. `baseUrl: ''` is only indirectly verified
**Gap ·** Case 4 asserts the fetch URL is `${location.origin}/accounts/me`, which passes for both `baseUrl: ''` and `baseUrl: '/'`. Case 5 asserts there is no `https?://` in `index.ts`, but does not assert the literal `baseUrl: ''` value.  
**Why it matters ·** The frozen brief explicitly requires `baseUrl: ''` (same-origin, no prefix). A regression to `baseUrl: '/'` would pass the current tests but deviate from the brief and could affect how paths are joined later.  
**Suggested test ·** Add a source-scan assertion that `libs/ts/api-client/src/index.ts` contains `baseUrl: ''` (or `baseUrl: ""`) and no other `baseUrl` value.

### 4. Only `GET` is exercised at runtime
**Gap ·** Case 4 calls `GET('/accounts/me')`. The auth contract defines POST, PUT, and DELETE operations (e.g., `POST /accounts`, `DELETE /accounts/me/mfa/totp`). None of these are type-checked or called in the test.  
**Why it matters ·** A generated client with a broken non-GET method signature (for example, due to a generator bug) would still pass T03. Later tasks assume all methods compile.  
**Suggested test ·** Add a type-level `expectTypeOf` assertion for at least one non-GET operation (e.g., `POST /accounts`), or a runtime stubbed call to a POST endpoint that asserts the method and path. No network should leave the stub.

### 5. Case 2 hard-codes the path to the frontend TypeScript binary
**Gap ·** The test asserts `Version 7.0.2` by running `frontend/node_modules/typescript/bin/tsc`. This path assumes TypeScript is installed inside `frontend/node_modules` and not hoisted by pnpm. While the probe passed, pnpm's hoisting behavior can vary with `shamefully-hoist`, `.npmrc`, or future workspace changes.  
**Why it matters ·** A brittle binary path can cause the test to fail in a differently configured environment even when TS 7.0.2 is available.  
**Suggested test ·** Resolve the binary dynamically, e.g., `require.resolve('typescript/bin/tsc', { paths: [frontendDir] })`, or run `pnpm -C frontend exec tsc --version` and assert the output starts with `Version 7.0.2`.

### 6. No test that the package entry is the only sanctioned import path
**Gap ·** The brief establishes `@themistra/api-client` as the one sanctioned client. The tests import from the package entry, but there is no assertion that `frontend/src/` code cannot deep-import from `libs/ts/api-client/src/generated/auth`. T04 will add a lint rule, but a static test in T03 could catch it earlier.  
**Why it matters ·** Deep imports bypass the thin wrapper and could lead to absolute URLs, env keys, or non-sanctioned clients creeping in.  
**Suggested test ·** Optional: scan `frontend/src/**/*.ts(x)` for imports matching `@themistra/api-client/src/` and fail if any are found. Defer to T04 if preferred.

### 7. No test that `createAuthClient` returns a client with the expected methods
**Gap ·** Case 4 uses the client imperatively and asserts one fetch. It does not assert that the returned object has the expected `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, and `use` methods.  
**Why it matters ·** A future change to `index.ts` that accidentally narrows the return type would be caught by `frontend typecheck`, but a runtime regression (e.g., returning a plain object) would not be caught until a later task tries to call a method.  
**Suggested test ·** Add a lightweight runtime assertion: `const client = createAuthClient(); expect(client.GET).toBeInstanceOf(Function); expect(client.use).toBeInstanceOf(Function);` etc.

### 8. No test for the `generate` script itself
**Gap ·** Case 1 runs `openapi-typescript` directly from the package's `node_modules`. It does not exercise the `pnpm -C libs/ts/api-client generate` script defined in `package.json`.  
**Why it matters ·** A typo in the `generate` script path or output option would not be caught by the test, but would break the documented regeneration workflow and CI drift step.  
**Suggested test ·** Add an assertion that `pnpm -C libs/ts/api-client generate` (or the equivalent `pnpm exec` invocation inside the package) produces the same output as the direct bin call, or at least that the script exists and its command string matches the expected invocation.

---

## Non-gaps (acknowledged, no change needed)

- **Byte-for-byte generation check.** Correctly verifies AC1/AC3 and catches hand-edits.
- **No network in tests.** All cases stub `fetch` or only type-check; no real HTTP is made.
- **SAS prefix scan.** Covers the frozen AC4 requirement.
- **Mutation breaks a–f.** Comprehensive coverage of the likely regressions.
- **`afterEach` restores globals.** Prevents stub leakage between cases.

---

## Summary

The Phase 10 test manifest provides strong coverage of generation drift, type-checking, same-origin requests, and the SAS-prefix exclusion. The main gaps are: missing `components` export assertion, missing problem-type schema check, indirect `baseUrl` verification, only `GET` exercised at runtime, a hard-coded TypeScript binary path, and no test of the package entry or the `generate` script. Adding these would make the suite a more complete gate for the “generated client only” rule (L6).
