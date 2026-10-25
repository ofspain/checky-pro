# frontend · T14 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact |
| **Produces** | `artifacts/13-pr-preparation.md` |

## Commit title

```
frontend T14: PKCE verifier
```

## Commit message

```
The SPA is a public OIDC client -- no client secret in the bundle -- so
the code challenge is the only thing binding an authorization code to
the client that asked for it. And the backend will not keep that rule
for us: RegisteredClientSeeder sets requireProofKey(true), so a
challenge is mandatory, but S256 appears nowhere in the auth service's
main source and Spring Authorization Server accepts plain with no switch
to forbid it. The strength of the exchange is decided in this file.

src/auth/pkce.ts, the first module under src/auth/: 32 CSPRNG bytes
base64url-encoded to 43 characters, the S256 challenge via
crypto.subtle, a validator over the full RFC 7636 range, and the method
published as a literal type so no caller can widen it to plain.

base64url rather than a draw from all 66 unreserved characters because
64 divides 256, so it is bias-free by construction; byte % 66 is not
(256 % 66 = 58, eight characters drawn a quarter less often). The cost
is that base64url never emits . or ~, so an assertion over our own
output could never fail -- the vacuity that bit T12's AC7 and T13's AC8.
Paid for by feeding the validator eleven inputs the generator cannot
produce, including both omitted characters. That case is what catches
M1.

67 cases, correctness resting on RFC 7636 Appendix B's published vector,
which no wrong hash satisfies. 18 mutants, 15 caught. The three
survivors are each explained: M12 is an equivalent mutant (a shared
buffer refilled and read with no await between produces 5000 distinct
verifiers out of 5000, measured), M14 is a guard weakening closed by
M13, and M17 surviving IS the experiment -- it shows the comment
stripper shipped in phase 6 would have let a verifier reach
localStorage with no case failing.

Suite 479/481, delta exactly 67, the two failures T07's known
environmental pair.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

| File | Change |
|---|---|
| `frontend/src/auth/pkce.ts` | **new**, 97 lines |
| `frontend/src/auth/pkceVerifierMeetsRfc7636.test.ts` | **new**, 423 lines, 67 cases |
| `.ai/prompts/frontend/T14/artifacts/*` | the fourteen phase artifacts |

**No existing file modified** — not `vite.config.ts`, not `package.json`, not `.oxlintrc.json`, no T01–T13 test, no
`spec/**`, `contracts/**`, `services/**` or `infra/**`. No npm dependency added.

## Testing performed

| Check | Result |
|---|---|
| `vitest run src/auth` | **67/67** |
| `vitest run` (whole suite) | **479/481** — 414 before, delta exactly 67 |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |
| Mutation pass | **18 mutants, 15 caught**; three survivors each explained by measurement |
| Stability | 15 reruns in phase 6, 8 in phase 9, 6 in phase 11 — the statistical cases never flaked |

The two failures are T07's `lintRejectsHandWrittenBackendFetch` AC4 (this host's `oxlint@1.87.0` prints no summary
line) and AC2 end to end (`spawn pnpm ENOENT`), both environmental, both failing since T07, neither touched here.

## What T15 inherits

1. **Import, do not restate.** T15 must build the authorize request from `generatePkcePair()` and send
   `PKCE_CODE_CHALLENGE_METHOD`. The literal type is the only mechanism carrying the S256 obligation across the
   boundary — **nothing in T14 notices if T15 sends `plain`, and the backend will accept it.**
2. **Persisting the verifier is T15's or T17's, not T14's.** L16 permits sessionStorage to hold `state`, `nonce`,
   `code_verifier` and the post-login return target; T14 deliberately stores nothing, and `sessionStorage` is in
   its forbidden-name scan to keep that true. The task that writes that set as a unit owns it, single-use and
   deleted at callback.
3. **`deriveCodeChallenge` is self-validating** and rejects anything outside 43–128 unreserved characters, so T15
   need not pre-check — but must handle a rejected promise rather than assume success.
4. **Silent renewal runs this code too.** `sessionBoundary.ts:7-14` documents `renew()` as re-opening
   `/oauth2/authorize` on the SAS cookie, so a fresh PKCE pair is needed per renewal, not only per sign-in.

## Owner decisions, in the order they bite

1. **OQ-1 — now the immediate blocker.** `design.md` §4c declares four public environment keys and R58 permits them
   in the bundle; **T01 `:130` forbids any source under `src/**` from naming `import.meta.env` at all.** T14 needed
   no key and proceeded. **T15 and T16 need the issuer, client id and redirect URI — exactly three of those four.**
   The owner must choose: relax T01 for the §4c keys, or keep deriving them at runtime. T01 is on the
   do-not-modify list, so this is not a decision any task can take for itself.
2. **T12's OQ-1** — a refresh cannot promote a waiting worker; the prompt UI needs the owner's wording and its
   reload mechanism.
3. `package.md:321-323` — forced update for rollback is owned by no task.

## Known, recorded, not fixed (all pre-existing, none this task's scope)

- `segmentPrefixPattern` is still duplicated between `navigationDenylist.ts` (private) and `apiRequestPolicy.ts`.
- `package.md:172` maps `serviceWorkerUpdateWaitsForReload` to R57, which does not describe it.
- `apiRequestPolicy.ts:46` lists `/app` in `SHELL_DOCUMENT_PATHS`, and `/app` returns 404 at the edge today.
- `design.md` §4c's heading still reads "PROPOSED, pending sign-off (Q6)" although Q6 closed 2026-10-10.
- **T01 builds and asserts against a development bundle** (it inherits Vitest's `NODE_ENV=test`) — same trap T13
  fell into; T01 is on the do-not-modify list.

## Where the frontend stands

**Phase 1 — Foundation (T01–T13) is complete. T14 is the first task of the auth section**, and the first module
under `src/auth/`. `src/api/`'s `SessionTokens` and `LocalSession` interfaces have been waiting since T08 for the
auth module to implement them; T14 supplies the first piece.

**The first real visible screen is still expected at T16** (`signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField`),
two tasks away. T15 assembles the authorize request; T16 renders the sign-in screen that starts it.

## Specification references

R6 (`requirements.md:21-22`); `design.md:8-10` (L1), `:13` (L3), `:49` (L12), `:50-51` (L13), `:52-55` (L14),
`:58-60` (L16), `:203` (§6 `auth/`), `:133-135` (SAS endpoints); `agents.md:18-24`, `:79-83`;
`package.md:110`, `:173` (§8), `:254`, `§11 Q6` (closed 2026-10-10); `tasks.md:23`, task 14;
RFC 7636 §4.1, §4.2, Appendix B;
`frontend/src/api/sessionBoundary.ts:7-14`, `frontend/src/app/routes.tsx:6-18`;
`services/auth/src/main/java/com/themistra/auth/token/RegisteredClientSeeder.java:53-60`,
`services/auth/src/main/resources/application.properties:52-53`,
`services/auth/src/test/java/com/themistra/auth/authn/SasLoginIntegrationTest.java:649-662`.
