# frontend · T20 · Phase 10 — Test Manifest

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T20 — Code exchange sends the verifier |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

**68 new cases.** Suite **886 passed / 1 failed of 887**, the one failure being the known environmental `oxlint`
case. **Mutation: 21 mutants, 21 killed, no survivors.** No production code was changed in this phase.

---

## Files

| File | Cases | Why |
|---|---|---|
| `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` | **64** (new) | The named test (`package.md:111` → R7), verifier half. |
| `frontend/src/api/generatedClientCompiles.test.ts` | **+3** | AC22, AC23, and the two-contracts-apart guard. |
| `frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts` | **+1** | The `ALLOWED` entry naming the OIDC client. |

Two layers in the named file, as in T19. The exchange is driven **directly** for the wire and the failure matrix,
where a component harness would hide which input produced which outcome; and **through the real screen** for the
outcome and the ordering, because R8 and L4 describe properties of the screen, not of this function. Only the
network is stubbed — no module is mocked, so every case runs the real `codeExchange.ts`.

## Acceptance criteria → cases

| AC | Where |
|---|---|
| **AC1** the stored verifier travels | `the stored verifier is what travels` → *sends the stored verifier* |
| **AC2** two unrelated stored values | → *sends %s stored value, so no constant can pass* (×2) |
| **AC3** POST to a relative path | `the wire` → *POSTs to the relative token path, resolved against the page origin* |
| **AC4** form body, exactly five fields | → *sends exactly the five fields, and no sixth*; *does not send `state`*; *sends a form-encoded body, not JSON*; *sends the exact form content type* |
| **AC5** `redirect_uri` byte-identical, derived once | → *sends a redirect_uri byte-identical to the authorize request* (built by the real `buildAuthorizeRequest`, not reconstructed) and `the source itself` → *derives the redirect URI from the one exported helper* |
| **AC6** no client credential | → *carries no client credential of any kind* |
| **AC7** goes through the generated client | `the source itself` → *goes through the generated client and hand-writes no request*; plus `oxlint` exit 0 |
| **AC8** the code is the admitted one | `through the screen` → *completes no exchange at all when the callback has …* (×4) and its control |
| **AC9** nothing written, deleted, cleared | `nothing is written, deleted or cleared` (×2) — `setItem`, `removeItem` and `clear` all spied |
| **AC10** no token anywhere visible | `through the screen` → *leaves no part of either token in storage, the DOM, the title or the URL* |
| **AC11** every failure, one outcome | `every failure is the same rejection` (×19) and `through the screen` → *gives four different faults one identical outcome* |
| **AC12** missing/non-string tokens reject | the matrix rows for absent, empty, numeric, null and object tokens — **both** `id_token` and `access_token` |
| **AC13** no refresh token | `what comes back` → *carries no refresh token through even when the server sends one*; the source scan; and the contract defines none |
| **AC14** the nonce check still runs | `through the screen` → *still discards a token whose nonce is not the stored value, now over a real transport* |
| **AC15** no new visible string | no catalogue key added; the discard copy is asserted equal across causes |
| **AC16** no env key, no absolute address | `the source itself` → the `FORBIDDEN` table |
| **AC17** exit code, not counts | recorded below |
| **AC18** callable outside the screen | every direct-layer case calls it outside the screen; T20-D8 documented in `codeExchange.ts` |
| **AC19** `/oauth2/token` is NetworkOnly | **already pinned** by `serviceWorkerNeverCachesApiResponses.test.ts:169-175`, which names `['token', '/oauth2/token']`. No duplicate written (Phase 7 F5). |
| **AC20** no server detail escapes | → *lets none of the authorization server's own words reach the SPA*; *writes the server detail to no console channel* |
| **AC21** the storage read is guarded | → *turns a storage read that THROWS into the ordinary rejection* |
| **AC22** the client form-encodes | `generatedClientCompiles` → *sends a form-encoded body through the OIDC client with the exact content type* |
| **AC23** `oidc.ts` drift | `generatedClientCompiles` → *reproduces the committed OIDC client byte for byte*; *keeps the two contracts apart* |

## Phase 8 and Phase 7 findings, closed

| Finding | Closed by |
|---|---|
| I1 named test missing | the 64-case file |
| I2 AC22 missing | the form-encoding case |
| I3 AC23 missing | the drift case |
| I4 `ALLOWED` entry missing | the lint table entry |
| I5 AC6 unproven | *carries no client credential of any kind* |
| I6 array reaches the cast | the matrix row *an array body* — pinned by behaviour, with no inert guard added |
| I7 unmount in flight | *survives the screen unmounting while the exchange is still in flight* |
| F1 AC4 rests on a test | the five-field case asserts the **serialized body**, not the object handed to the client |

## Mutation

21 mutants against `codeExchange.ts` and `CallbackScreen.tsx`. Baseline **0 failed of 321** across the three auth
suites. **21 killed, no survivors.** The interesting ones:

| Mutant | Fate |
|---|---|
| M1 send a constant verifier | killed (4) — this is AC2's whole purpose |
| M2 read the state key instead | killed (27) |
| M6 send with no verifier at all | killed (23) |
| M8 add `; charset=UTF-8` to the content type | killed (10) — T20-D5's `===` trap, caught |
| M9 put `state` on the wire | killed (2) |
| M10 hand-spell the redirect URI | killed (1) — **by the structural scan only** |
| M14 pass the whole response through | killed (8) |
| M15 let the original error escape | killed (3) |
| M17 store the access token | killed (5) |
| M21 exchange despite a mismatched state | killed (20) |

**Two cases exist because a mutant survived first, and both are recorded rather than quietly fixed.**

**M10 · hand-spelling the redirect URI.** A second derivation from `location.origin` and the basename produces the
**identical string**, so no assertion about the request can tell it from the shared helper — they differ only on
the day one of them changes, which is the day sign-in is already broken against an exact-match registration. The
case that kills it is a source scan, and it is a source scan on purpose: T20-D7 is a structural decision, so it
needs a structural test. This mutant was anticipated while writing the harness and the scan was added before the
run.

**M5 · removing the `try/catch` around the storage read — this one actually survived.** The original case asserted
only that a throwing read *rejects*. But `exchangeAuthorizationCode` is `async`, so an **unguarded** read rejects
too, with the `DOMException` — the assertion could not see the guard at all, and the file would have gone on
passing with the protection deleted. This is the package's recurring defect in a fresh disguise: a test that
exercises a path without exercising the property. The case now compares the rejection's `message` to the one
message every other failure produces, and its `name` to `Error`, and the mutant dies.

## Verification

- Named suite **64 passed**, whole suite **886 passed / 1 failed of 887**.
- The one failure is `lintRejectsHandWrittenBackendFetch` AC4 at `:113` — this host's `oxlint@1.87.0` prints no
  summary line — **and no other**, read from the failure list rather than inferred from the counts.
- `vitest` exit **1**, which is that single failure and **no unhandled rejection**: the run prints no `Errors`
  line. AC17 is about the exit code precisely because T19 found an unhandled rejection reported *below* a line
  saying every test passed.
- `tsc --noEmit` clean. `oxlint` exit **0**.
- The harness is at
  `/private/tmp/claude-501/-Users-macbookpro-personal-crypto-dispute/d6c7daa6-f34e-40ce-afe5-23ce45bf781e/scratchpad/mutate-t20.py`.
  It reverts with `git checkout --`, so the tests were committed before it ran.

## Open questions

**None.** OQ-2 and OQ-3 stay deferred with the owners Phase 4 named.

---

## Next — Phase 11 — test review. **Kimi 2.7.**
