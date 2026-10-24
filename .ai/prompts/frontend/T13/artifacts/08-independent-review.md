# frontend · T13 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `frontend/src/app/bundleContainsNoSecretEnvKeys.test.ts`, `frontend/vite.config.ts`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **No secret in the bundle.** The production bundle contains no secret value and no non-public environment key. Verifies `bundleContainsNoSecretEnvKeys`. Cites R58, L10.

---

## Review scope

Reviewed the Phase 6/9 T13 implementation against the frozen brief, `agents.md`, `package.md`, and the Phase 7 self-review. No code was rewritten.

The implementation is strong: one shared `scanBundle`, a real `vite build` invoked as a child process, mechanism closure on `envPrefix`/`define`/`.env`/sourcemaps, set-equality checks for keys and URLs, and a comprehensive negative control. The self-review found seven issues; this review corroborates them and adds three fresh observations.

---

## New findings

### 1. The build inherits host `VITE_*` environment variables
**Issue ·** The child `vite build` environment is `process.env` with only `NODE_ENV`, `VITEST`, and `VITEST_MODE` removed. Every other shell variable, including any `VITE_*` key, is passed through. Because `envPrefix` is pinned to `VITE_`, a developer's or CI host variable such as `VITE_EDITOR` or `VITE_SOMETHING` will be inlined into the bundle, causing the test to fail with an unlisted key. Conversely, a CI secret injected through the environment would be baked in and the test would correctly fail — but the failure mode depends on the host rather than on the committed code.  
**Severity ·** Medium — environment-sensitive tests are flaky and hard to debug; the distinction between "committed code leaks" and "host environment leaks" is not documented.  
**Evidence ·** `bundleContainsNoSecretEnvKeys.test.ts:168-175` copies `process.env` verbatim except for the three removed keys.  
**Recommendation ·** Decide explicitly in the frozen brief:
- Option A (strict): keep host variables and document that the test is intentionally environment-sensitive — a production build must not be produced from a shell carrying extra `VITE_*` variables.
- Option B (deterministic): sanitize the child environment by deleting every key matching `/^VITE_/`, leaving only the four public keys if set. This makes the test verify the committed configuration, not the host, but it shifts responsibility for host-leak detection elsewhere.
Either is acceptable; silence is not.

### 2. The allowlist parser stops at the first blank line
**Issue ·** `allowlistFromSpec` collects lines from the `Environment keys` heading until the first blank line. `design.md` currently has the four keys in a single paragraph, so this works. If the spec is later reformatted as a Markdown list with blank lines between items, the parser will stop early and the drift guard will fail open.  
**Severity ·** Low — a latent parser fragility.  
**Evidence ·** `bundleContainsNoSecretEnvKeys.test.ts:146-150`.  
**Recommendation ·** Change the loop to continue until the next Markdown heading (`/^#/`) or the end of the file, and collect backticked `VITE_*` tokens across all lines under the heading. Add a test that deliberately passes a multi-line spec paragraph to the parser.

### 3. The negative control does not exercise a secret appended to an allowlisted URL
**Issue ·** This is the concrete missing case behind the self-review's S1. The known URL `https://bit.ly/wb-precache` is in the allowlist. If a secret is appended as a query string or fragment, the current `ABSOLUTE_URL_PATTERN` strips it, so set equality passes and the leak is invisible. The negative control should include exactly this bypass shape.  
**Severity ·** Medium — the primary AC8 bypass needs a regression test.  
**Evidence ·** `bundleContainsNoSecretEnvKeys.test.ts:80` and the negative control at `:218-230`.  
**Recommendation ·** Add a synthetic file containing `'https://bit.ly/wb-precache?key=SUPERSECRET'` and assert the scanner reports either the full URL or a query/fragment-containing URL that is not in `KNOWN_ABSOLUTE_URLS`. Then fix the pattern to consume `?`, `&`, `=`, `%`, and `#`.

---

## Corroborated findings (from Phase 7 self-review)

| # | Self-review finding | Independent view |
|---|---|---|
| S1 | Secret in query string on allowlisted URL is invisible | **Confirmed — critical AC8 bypass.** The path class in `ABSOLUTE_URL_PATTERN` stops at `?`. Fix the regex and add a negative-control case (see New Finding 3). |
| S2 | Platform-host check is narrower than it reads | **Confirmed.** The regex `//auth[:/]|//auth$` misses `auth.checky.example`. Broaden it or reword the case to say it only checks bare service names, with set equality as the real guard. |
| S3 | Production-build assertion covers only the main chunk | **Confirmed.** The worker is built by `vite-plugin-pwa` and could be non-production independently. Assert `process.env.NODE_ENV` and no `react.dev/(link\|warnings)` over every emitted JS file. |
| S4 | Nothing asserts the bundle actually contains this application | **Confirmed, low.** Add a marker check (e.g., the `app-shell` test id or a distinctive mount error string) to the main chunk. |
| S5 | Binary assets are read as UTF-8 | **Confirmed, low.** Read non-text extensions as `Buffer` and scan with `latin1` so no bytes are dropped, or document the text-only assumption. |
| S6 | Key scan cannot see a value inlined under an arbitrary `define` name | **Confirmed, by design.** Add a comment at `ENV_KEY_PATTERNS` pointing to the `define` case as the counterweight, so readers do not over-trust the pattern. |
| S7 | The "every entry justified" case is a smell detector, not a proof | **Confirmed.** The case counts comment lines but does not tie them to entries. Keep it with an honest comment, or replace it with a per-entry comment requirement. |

---

## Non-gaps (acknowledged, no change needed)

- **One scanner, two callers:** `scanBundle` is used by the real assertions and the negative control; definitions are not duplicated.
- **Vacuity trap closed:** `scanBundle` throws on an empty tree and a case asserts the throw.
- **Real production build:** the child `vite build` runs with `NODE_ENV`/`VITEST` removed, so React's development build is not scanned.
- **Mechanism closure is real:** `envPrefix` pinned to `['VITE_']`, no `define` block, no `.env*` files, no sourcemaps, no surviving `import.meta.env.`.
- **Allowlist is the spec's:** parsed from `design.md` §4c and asserted by set equality, with length/shape checks.
- **Stricter-than-required state is labelled:** the "no environment key at all today" case is explicitly separated from the rule, with a comment pointing at OQ-1.
- **No T01–T12 file modified** except `vite.config.ts`, and that change only states the existing `envPrefix` default.
- **Worker filename discovered by shape**, not hard-coded.
- **Secret patterns cover private keys, `client_secret`, Bearer/JWT, AWS AKIA, and the platform's own `checky-local-only` value.**
- **Absolute URLs checked by set equality against an enumerated allowlist.**

---

## Summary

T13's bundle scanner is a credible regression guard. The highest-priority Phase 9 fixes are the query/fragment bypass in the URL pattern (S1 / New Finding 3) and the decision about host `VITE_*` variables (New Finding 1). The remaining findings are low-severity hardening: broaden the platform-host check, assert production mode over all JS files, add an application marker, handle binary assets robustly, and strengthen the allowlist parser. None of these weaken the existing protections.

**Note:** This review was performed statically; the suite could not be executed on this host because `frontend/node_modules` is incomplete (`vite-plugin-pwa` is missing), continuing the T12 OQ-C blocker.
