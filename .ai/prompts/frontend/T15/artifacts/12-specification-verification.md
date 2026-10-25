# frontend · T15 — Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Spec section** | Phase 1a — Security properties |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/11-test-review.md`, the working tree |
| **Produces** | `artifacts/12-specification-verification.md` |

**Task statement (verbatim):**
> Assemble the OIDC authorization request: exactly the three declared scopes, `state`, `nonce`, `code_challenge_method=S256` and the PKCE challenge from T14 — and persist the three L16 items the callback tasks will read back.

---

## Verdict

**PASS.**

| | |
|---|---|
| `src/auth` (T14 + T15) | **117 / 117 pass** |
| Whole frontend suite | **529 pass / 531 total** |
| The 2 failures | `lintRejectsHandWrittenBackendFetch` AC2 and AC4 — T07's `spawn pnpm ENOENT`. Bare `pnpm` is not on this host's PATH; `npx -y pnpm@12.14.0` is the working recipe. Pre-existing, unrelated, untouched by this task. |
| `tsc --noEmit` | clean |
| `oxlint --no-ignore -c .oxlintrc.json src` | clean |
| Mutants | **28 run, 28 caught** |
| T15 cases | **50** |
| Suite delta | 479/481 → 529/531, **+50**, matching the case count exactly |

---

## Phase 11 resolution

Kimi raised seven gaps. **Six applied, one declined.** Two of the seven rested on a premise I checked against
source first, and both premises were wrong in a way worth recording.

| # | Gap | Disposition |
|---|---|---|
| 1 | No source scan for an absolute URL or `import.meta.env` | **Applied, with a correction.** The *rule* was never unenforced: T01's `productionSourceFiles()` reads `src/**` recursively, filters out `*.test.*`, and scans every remaining file — `authorizeRequest.ts` included — for both patterns. So this is duplication, not a hole. Applied anyway, because the frozen brief listed it as a required test and because a module-local failure names this module instead of handing the reader a list of paths. **M28** confirms it fires. |
| 2 | The exact-eight matcher is only shown failing when a parameter is *added* | **Applied.** A default-deny matcher should be shown failing in both directions; a removal control is one case. |
| 3 | The leak matcher's limits are documented but never demonstrated | **Applied, and this is the best of the seven.** A base64-re-encoded verifier and a verifier split across two parameters now both go through the matcher and both pass undetected. Both *are* leaks. Making the limit a measured fact stops the comment rotting into a claim. **M29** confirms the case is load-bearing. |
| 4 | Nothing asserts one PKCE pair per request | **Applied.** M7 already caught storing one pair and sending another; the remaining shape is deriving two and using one consistently — correct output, two CSPRNG draws and two SHA-256 runs, and a challenge that was not the only one the call created. **M27** confirms. |
| 5 | Nothing pins `state`/`nonce` to the shared helper | **Applied, implemented differently.** Kimi's suggestion calls `codeWithoutComments(moduleSource)` — **that helper does not exist in T15's test.** It is T14's, and I deliberately did not duplicate it (phase 9 anchored T15's scans to *usage* rather than mention, precisely to avoid carrying a second copy of a scanner). Implemented instead as a call-site count, `/\brandomUrlSafeToken\s*\(/g` with length 2, which needs no comment stripping because the import line and the doc comment are not call sites. **M25** confirms. |
| 6 | The authorize URL and the `redirect_uri` are never compared to each other | **Applied, and reported as not independently load-bearing.** The line is in. But **M26** — hardcoding the redirect origin to `http://localhost:3000`, the jsdom origin — is caught by the pre-existing stubbed-origin case and by gap 1's new absolute-address scan, **not** by this equality. No mutant I could construct is killed by it alone, because both URLs are built from the same `location.origin` expression in the same call. Kept as a cheap invariant statement; recorded here so nobody later mistakes it for proven coverage. |
| 7 | The backend path set should be asserted to equal exactly `{/oauth2/authorize, /oauth2/token}` | **DECLINED.** Kimi marks it optional and names the risk itself. The guard's job is to confirm the SPA spells an endpoint the backend actually drives — that is a containment property, and containment is what is asserted. Set *equality* would make this frontend test fail when someone adds an unrelated `/oauth2/device_authorization` to a backend integration test: a red frontend suite for a change that cannot affect the authorize request. A frontend test should not be the gatekeeper of the auth service's test files. The guard is directional because the dependency is directional. |

### A process failure, again

**The mutation harness destroyed the phase-11 test edits before they were committed.** M29 is a guard weakening — it
mutates the *test* file — and the harness reverts each mutant with `git checkout -- <path>`, which discarded all
five new cases along with it. The suite still passed, at 112 instead of 117, and only the case count revealed it.

This is the **same failure as T14 phase 11**, where 19 cases were lost the same way, and the rule against it was
already written down. The edits were re-applied, committed immediately, and the five mutants re-run against the
committed state — all five still caught. Recorded here rather than quietly fixed, and the rule is now its own
standing memory with the instruction to check the case count after every mutation pass.

---

## Acceptance criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 — exactly `openid profile email`, in order | PASS | exact string, split-array equality, bidirectional matcher · M1–M4, M22 |
| AC2 — `code_challenge_method=S256` from T14's constant | PASS | imported constant, no quoted `'S256'` of its own · **M5** |
| AC3 — the challenge sent belongs to the verifier stored | PASS | stored verifier read back, challenge recomputed · M6, M7, M27 |
| AC4 — `state`/`nonce` present, distinct, fresh, CSPRNG-sourced | PASS | 50-request freshness, length by literal *and* by helper, two call sites · M13, M14, M25 |
| AC5 — `response_type`, `client_id`, `redirect_uri`, endpoint path | PASS | value checks, single-occurrence `checky-spa`, changed-origin case, route-table mocking, backend-anchored spelling · M8–M12, M24, M26 |
| AC6 — the verifier never reaches the URL | PASS | 5 leak shapes caught, 2 demonstrated as beyond reach, absent from the return type · M20, M21, M29 |
| AC7 — exactly three `sessionStorage` keys, nothing else touched | PASS | key-set equality, overwrite-on-retry, runtime Storage/console spies · M15 |
| AC8 / AC12 — drift guards | PASS | `agents.md` scope sentence, edge `@auth` matcher, `routes.tsx`, the backend path set · M12, M23, M24 |
| AC9 — non-vacuity | PASS | every matcher fed wrong input, in both directions · M19, M21–M23, M29 |
| AC10 (D-8) — measured delta, no earlier file modified | PASS | +50 measured; only `authorizeRequest.ts`, its test, and the D-2 change to `pkce.ts` |
| AC11 — fails closed | PASS | invalid-verifier and derivation-failure cases, both asserting empty storage · M16, M17 |

### Locked decisions, each discharged

D-1 published `AUTHORIZE_ENDPOINT_PATH` with a drift guard — done, and strengthened past the brief after M12.
D-2 put `randomUrlSafeToken` in `pkce.ts` — done; T14's 67 cases still pass and its `btoa(` source assertion
still holds. D-3 trailing-slash-safe derivation — done, and now measured on the module via a `/console/` mock
rather than restated in the test. D-4 `CLIENT_ID` as a constant with a single-occurrence guard — done.
D-5 functional drift guard plus changed-origin case — done. D-6 fail-closed on an invalid verifier — done, and it
is the reason `buildAuthorizeRequest` checks the pair's own contract. D-7 runtime Storage spies — done.
D-8 measured baseline — done. D-9 bidirectional matchers — done, and extended in both directions.
D-10 `URLSearchParams`, order-independent assertions — done. D-11 OQ-1 does not gate T15 — holds; no env key
is read.

---

## Files

| File | Change |
|---|---|
| `frontend/src/auth/authorizeRequest.ts` | **new**, 98 lines |
| `frontend/src/auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts` | **new**, 50 cases |
| `frontend/src/auth/pkce.ts` | +9 / −4 — `randomUrlSafeToken` extracted per D-2; `generateCodeVerifier` delegates |

Nothing else. No config, no `package.json`, no dependency, no file from T01–T13.

---

## Carried forward

**Open for the owner, gating nothing in T16:**

- **OQ-1** — T01 forbids `import.meta.env` anywhere under `src/**`; `design.md` §4c allows four public keys.
  T15 needs none, and on phase 0's evidence T16 and T20 will not either: one origin, a server-fixed `client_id`,
  a derivable redirect URI. The §4c allowlist is a ceiling, not a plan. **I previously told the owner this blocked
  T15 and T16. It does not — that was my error, corrected in phase 0 and now in memory.**
- **T12's OQ-1** — a refresh cannot promote a waiting service worker; needs an owner decision before the prompt UI.
- `design.md` §4c still heads the allowlist "PROPOSED, pending sign-off (Q6)" although Q6 closed 2026-10-10.
- `package.md:172` maps `serviceWorkerUpdateWaitsForReload` to R57, which does not describe it.
- `package.md:321-323` forced-update-for-rollback is unowned.
- `segmentPrefixPattern` is duplicated between `navigationDenylist.ts` and `apiRequestPolicy.ts`.
- `apiRequestPolicy.ts:46` lists `/app` in `SHELL_DOCUMENT_PATHS` although `/app` 404s.
- **T01 asserts against a development bundle.** Vitest sets `NODE_ENV=test`, so an in-suite Vite build is a dev
  build. T13 works around this by shelling out with the env scrubbed; T01 has the same bug and is on the
  do-not-modify list.

**The one worth repeating:** the `S256` rule is kept by `authorizeRequestsExactlyTheDeclaredScopes.test.ts` and by
nothing else on the platform. `RegisteredClientSeeder.java:60` requires a proof key; it does not require S256, and
Spring Authorization Server has no switch to forbid `plain`. If that test case is ever deleted as redundant, the
SPA can silently downgrade to no PKCE protection at all and every backend check will still pass.

---

## Next

Phase 13 — PR preparation. Then **T16, the first visible screen.**
