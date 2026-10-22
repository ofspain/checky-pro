# frontend · T04 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T04 — No hand-written backend fetch |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Current `frontend/.oxlintrc.json`, `frontend/package.json`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **No hand-written backend fetch.** A lint rule fails a build that calls `fetch` against a backend route. Verifies `lintRejectsHandWrittenBackendFetch`. Cites L6.

---

## Review scope

Reviewed the Phase 6 T04 implementation against the frozen brief and `spec/frontend/agents.md`. No code was rewritten.

---

## Findings

### 1. `XMLHttpRequest` property access on window-family objects is not banned
**Issue ·** The `no-restricted-properties` rule only restricts the `fetch` property on `window`, `globalThis`, `self`, `top`, `parent`, `opener`, and `frames`. It does not restrict `XMLHttpRequest` as a property of those objects. Therefore, `window.XMLHttpRequest`, `self.XMLHttpRequest`, etc., will not be flagged, even though bare `XMLHttpRequest()` is banned.  
**Evidence ·** `frontend/.oxlintrc.json:24-61`: every entry restricts `fetch` only; no entry restricts `XMLHttpRequest` as a property.  
**Recommendation ·** Add `XMLHttpRequest` entries to `no-restricted-properties` for every object in the list, with the same L6 message. This closes the `window.XMLHttpRequest` bypass without changing the rule set shape.  
**Confidence ·** High — direct bypass of the stated ban.

### 2. Lint script does not use `--no-ignore`
**Issue ·** `oxlint` reads `.eslintignore` by default. A malicious or accidental `.eslintignore` entry could hide a source file from lint and make `pnpm -C frontend lint` exit 0 while a violation exists. The self-review confirmed this bypass.  
**Evidence ·** `frontend/package.json:8`: `"lint": "oxlint -c .oxlintrc.json src"`; self-review finding 2.  
**Recommendation ·** Change the script to `"lint": "oxlint --no-ignore -c .oxlintrc.json src"`. Update the Phase 10 test case 4 to assert the new script string, and add a fixture/mutation break that shows `.eslintignore` no longer hides violations.  
**Confidence ·** Medium — a real bypass, but requires filesystem changes outside the config.

### 3. No guard against inline disable directives
**Issue ·** `// oxlint-disable-next-line no-restricted-globals` and `// eslint-disable-next-line` silence the L6 rule. oxlint 1.87.0 has no flag to forbid directives, so a single comment can defeat the build-time guarantee. The self-review confirmed this bypass.  
**Evidence ·** `frontend/package.json:8` (`lint` has no directive control); self-review finding 1.  
**Recommendation ·** Add a test case to `lintRejectsHandWrittenBackendFetch` that scans `frontend/src/**/*.{ts,tsx}` for any `oxlint-disable` or `eslint-disable` directive and fails if found. Today there are none, and the R59 exception should live in `.oxlintrc.json` (AC5), not in a comment.  
**Confidence ·** Medium — silent bypass in one line.

### 4. Aliased window objects bypass `no-restricted-properties`
**Issue ·** The rule matches property access only when the object is a literal identifier like `window`, `globalThis`, `self`, etc. Code such as `const w = window; w.fetch('/api')` or `const g = globalThis; g.fetch('/api')` is not flagged, because the rule does not track the variable assignment.  
**Evidence ·** `frontend/.oxlintrc.json:24-61`: rule entries match literal object names only.  
**Recommendation ·** Document this as a known residual bypass. A static linter cannot fully track aliases without dataflow analysis. The existing rules still catch the common cases; deeper enforcement would require code review or a custom AST rule (out of T04 scope).  
**Confidence ·** Low — inherent limitation of the chosen rule, not a config mistake.

### 5. `this.fetch()` in non-strict global context is not banned
**Issue ·** In non-strict mode, `this` at the top level refers to the global object (`window`). A call like `this.fetch('/api')` would not be flagged by either rule. TypeScript and Vite emit strict mode by default, so this is unlikely in practice, but it is a theoretical bypass.  
**Evidence ·** `frontend/.oxlintrc.json`: no rule restricts `this.fetch`.  
**Recommendation ·** No change needed for T04. Note it as a residual bypass alongside finding 4. TypeScript's `alwaysStrict` (enabled by `strict`) makes `this` undefined at the top level, so the code would error at runtime anyway.  
**Confidence ·** Low — mitigated by TypeScript strict mode.

### 6. `Reflect.get(window, 'fetch')` and similar metaprogramming bypasses
**Issue ·** `Reflect.get(window, 'fetch')()` or `Object.getOwnPropertyDescriptor(window, 'fetch').value()` retrieve the fetch function without a direct property access that the rule recognizes.  
**Evidence ·** The rule set targets identifiers and literal property access, not `Reflect`/`Object` metaprogramming.  
**Recommendation ·** No change for T04. These patterns are uncommon and would stand out in code review. Document as residual bypasses if a security checklist is maintained.  
**Confidence ·** Low — edge case.

### 7. No assertion that lint reports exactly two rules
**Issue ·** The config deliberately runs only two rules. If a future `oxlint` bump adds a default-on category or a dependency changes the config resolution, extra rules could start running without failing AC4. The self-review recommended asserting the rule count.  
**Evidence ·** Self-review finding 3.  
**Recommendation ·** In the Phase 10 test case that lints the real `frontend/src`, assert that the output reports exactly two rules (or two diagnostics categories). This makes unintended rule additions a test failure.  
**Confidence ·** Low — defensive test strengthening.

### 8. The T04 named test file does not exist yet
**Issue ·** `frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts` is part of Phase 10 and has not been written. The config and scripts are in place, but the named test is missing.  
**Evidence ·** `glob frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts` returns nothing.  
**Recommendation ·** Complete Phase 10 and write the five test cases plus mutation breaks listed in the frozen brief, incorporating findings 1–3 and 7 above.  
**Confidence ·** High — expected workflow gap.

### 9. `$schema` path resolves only after install
**Issue ·** The `.oxlintrc.json` `$schema` points to `./node_modules/oxlint/configuration_schema.json`, which does not exist until `pnpm install` runs. Editors show no schema before then. oxlint ignores `$schema`, so lint is unaffected.  
**Evidence ·** `frontend/.oxlintrc.json:2`; self-review finding 4.  
**Recommendation ·** No change. This is a local editor convenience and has no runtime impact.  
**Confidence ·** Low — informational.

### 10. Only `src` is linted; build config is not
**Issue ·** `vite.config.ts` and `index.html` are outside the lint path. `index.html` has no inline script; `vite.config.ts` is build-time Node config. This matches the brief's scope.  
**Evidence ·** `frontend/package.json:8` (`src` only); self-review finding 5.  
**Recommendation ·** No change. The brief correctly scopes the rule to app code.  
**Confidence ·** Low — confirmation.

---

## Open Questions

- None blocking. Findings 1–3 should be addressed before freezing; findings 4–7 are residual limitations or strengthenings.

---

## Summary

The T04 config and scripts are minimal and match the frozen brief. The most important gap is that `XMLHttpRequest` is only banned as a global, not as a property on `window`/`self`/`top`/etc. The self-review's two bypasses (inline directives and `.eslintignore`) are also unaddressed in the current implementation. The remaining findings are known residual bypasses inherent to static name-based linting or low-severity test strengthenings. The Phase 10 test file is the remaining deliverable.
