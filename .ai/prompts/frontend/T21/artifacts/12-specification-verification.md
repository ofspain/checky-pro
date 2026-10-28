# frontend · T21 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/11-test-review.md` and every prior artifact |
| **Produces** | `artifacts/12-specification-verification.md` |

Task statement: *"The token is held in a module-scoped variable and nowhere else. Verifies
`callbackValidatesNonceAndKeepsAccessTokenInMemory`. Cites R7, L3."*

**The whole change set is three files**, and one of them is the test:

| File | Change |
|---|---|
| `frontend/src/auth/accessToken.ts` | **new**, 99 lines — the holder |
| `frontend/src/auth/CallbackScreen.tsx` | `clearAccessToken()` at `:74` inside the shared discard, `retainAccessToken(response.accessToken)` at `:149` after the nonce check |
| `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` | +39 cases (69 → **108**) |

Nothing else. No contract, no dependency, no catalogue key, no composition-root wiring (T21-D5), and
`crossTabRenewal.ts` and `sessionBoundary.ts` are byte-identical to their committed state — which is an assertion
in the suite, not just an observation.

---

## Traceability

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R7** third clause — "SHALL hold the access token in memory only" | Yes | `accessToken.ts:24` one module-scoped `let`; `:82-84` the one write; `CallbackScreen.tsx:149` the only production caller | `…KeepsAccessTokenInMemory.test.tsx:918` *holds the access token after a callback that completes*; `:929` two unrelated values | No | No |
| **R7** ordering — the nonce is validated *before* the token is held | Yes | `CallbackScreen.tsx:136-139` early return, `:149` retention on the far side | `:1013` *holds the token only AFTER the nonce check, never before it* (structural, and M21 proves nothing else can see it) | No | No |
| **L3** "never written to storage, logs, or analytics" | Yes | `accessToken.ts` names no storage API, no `console`, no address | `:1189` storage/DOM/title/URL sweep; `:1222` all four console channels; `:1261` the 6-row `FORBIDDEN` scan, each row self-proving | No | No |
| **L3** renewal: no refresh token to hold | Yes | `accessToken.ts:63-67` — `renew()` is a seam; nothing holds a refresh token because none is issued (T20 measured this) | `:1152` the fixed-message case; `:1170` the 401 through the real boundary | No | No — T21-D1, the house seam pattern, third use |
| **R19** sign-out clears in-memory token state | **Not T21** — the export it needs exists | `accessToken.ts:97-99` `clearAccessToken()` | — | Task **35** owns sign-out | No |
| **R21** one renewal per tab, shared across tabs | **Not T21** — conformance only | the holder satisfies `LocalSession` by use | `:1046`, `:1059` the real channel listener, `:1099` the real leader path | Task 33 owns the route to clean re-auth | No |
| **R22** no half-authenticated screen | Yes, for the holder's part | `CallbackScreen.tsx:74` — the discard clears | `:983` nine failure rows; `:991` the stale-token case | No | No |
| **L16** the sessionStorage carve-out is exhaustive and names no token | Yes — nothing added to it | no storage write anywhere in the change set | `:1211` *leaves the three L16 items exactly as task 15 wrote them* | No | No |
| **L18** the client boundary owns 401 renewal | Yes — unchanged | `sessionBoundary.ts` untouched | `:1170`; and `:1354` keeps middleware off the OIDC client | No | No |
| Named test `callbackValidatesNonceAndKeepsAccessTokenInMemory` | Yes — both halves now exist | the file, 108 cases | 108/108, exit 0 | No | No |
| Named test `tokensAreNeverWrittenToAnyStorage` | **Not T21** | — | — | Task **22** (`tasks.md:34`) | No |
| Named test `returnTargetOutsideAllowlistFallsBackToHome` | **Not T21** | — | — | Tasks **24, 25** (`tasks.md:36-37`) | No |
| Named test `callbackValidatesNonce` | Already passing (T19) | `callbackValidatesNonce.test.tsx` | unchanged, green | No | No |

### Acceptance criteria

All seventeen pass. The mapping is in `artifacts/10-test-generation.md`; four are worth restating because the
*form* of their evidence is the decision:

- **AC9** is verified in T21-D8's corrected form, not as frozen in the original brief. The literal AC9 ("no second
  copy … anywhere") is false of code that was already built and is on the do-not-modify list:
  `sessionBoundary.ts:100` reads `current()` into `tokenUsed`, `:108` keeps it in a `Map` and `:104` clones a
  request carrying the bearer. AC5 *requires* that copy. The criterion verified is "T21 introduces no copy of its
  own and holds no durable second copy", and the suite asserts exactly one mutable binding with exactly two
  writes to it (`:1251`).
- **AC14** is structural because it cannot be anything else. A token retained before the nonce check and then
  cleared by `discard` leaves `current()` null at the end of a case — indistinguishable from the correct order by
  any outcome. Mutant M21 confirms it: every behavioural assertion in the file passes under it.
- **AC15** is read off the **exit code**, not the counts. An unhandled rejection prints below the all-passed line.
- **AC6** needed a second case to be true at all. See below.

### LOCKED decisions

None violated. **L3** is the task's own and is the thing proven. **L16** is untouched. **L18** is unchanged and
re-pinned. **L1** (the nonce) is T19's and still passes. **L13** (no state library) holds — the holder is a
module-scoped `let`, which is also why its own doc comment opens by saying this is the one place in the package
where that is the requirement rather than a smell. No new dependency, so **L6** and the lint allowlist are
unaffected.

## What the reviews changed

Three findings changed the deliverable rather than the prose, and each was a case of something looking verified
that was not:

1. **Phase 8 I1 — the in-memory half did not exist.** The file's name had promised it since T20. Phase 10 wrote
   it; that is 39 of the 39 new cases.
2. **Phase 7 F1 / Phase 8 I2 — AC9 was literally false.** Resolved as T21-D8 rather than by weakening a test or
   editing built code.
3. **Phase 11 G1 — AC6 was passing against nothing.** `shareRenewalAcrossTabs(accessToken)` with no options looks
   for `navigator.locks`; **measured, jsdom does not have it** (`navigator.locks` is `undefined` while
   `BroadcastChannel` is a function), so the call took the degraded per-tab branch at `crossTabRenewal.ts:79-86`
   and returned a passthrough. The case proved `current()` delegates; the production message listener never ran
   and no broadcast token ever reached `adopt`. The new case at `:1059` injects a channel and a lock manager,
   captures the listener the module registers, and asserts the posted token becomes `current()` — with
   `expect(listeners).toHaveLength(1)` as the control that fails on the degraded branch. A second case at `:1099`
   leads a renewal: the seam rejects, siblings are told `renewal:failed` and nothing else, and the held token
   survives.

Phase 11's G4 was accepted with its mechanism corrected: `rejectionOf(accessToken.renew())` does **not** pass
either way — a synchronous throw propagates out of the argument expression and fails the case, which is what
killed M11. The Promise-shape assertion was still added, so the claim is local and carries its reason.

## Measurements

```
named file   108 / 108            exit 0
suite        931 passed / 1 failed of 932   exit 1 — the known oxlint-summary case only
typecheck    tsc --noEmit         exit 0
mutation     25 mutants, 25 killed, 0 survivors
```

The one suite failure is `lintRejectsHandWrittenBackendFetch.test.ts:118` (AC4): this host's `oxlint@1.87.0`
prints no summary line. Environmental, unchanged since T18, and the only failure in the run.

## Remaining risks

1. **The composition root is still unwired** (T21-D5, Phase 3 F3, Phase 8 I6). `mountShell.tsx` constructs no
   boundary, so in the running app nothing yet reads `current()`. AC5 and AC6 are proven by isolated use of the
   real consumers, which is what the frozen brief asks for — but the app has no working session boundary until
   the task that wires it, and **no task in `tasks.md` 1-39 is visibly that task**. Carried from the brief's §6.
2. **Silent re-authentication has no task either.** `renew()` is a seam with a fixed message. Task 33 owns the
   route to clean re-auth when renewal fails, and today every renewal fails. **Task 33 must never render
   `RENEWAL_NOT_BUILT`** — it is an engineering message and L4 forbids it reaching a visitor.
3. **A forged callback ends a signed-in tab's session** (Phase 8 I7). Deliberate, pinned at `:991`, documented in
   the case so it is not read later as a regression. The alternative leaves a half-authenticated tab, which R22
   forbids.
4. **Three claims rest on source scans** — the single binding, the retain-after-nonce ordering, the un-widened
   `LocalSession`. A refactor can falsify them without changing behaviour; the mutants show scans are the only
   thing standing behind them, which is the honest cost of properties that have no observable difference.
5. **`retainAccessToken` validates nothing** (Phase 8 I4, Phase 7 F7). Documented as deliberate: both callers
   validate in the module that knows what valid means there. A third caller could pass an empty string.
6. **`expires_in` is still dropped** (T21-D2). Nothing knows when the token expires, so the first 401 is the only
   signal — which is exactly what the boundary is built around, but it means no proactive renewal is possible
   until the renewal task exists.

## Verdict

1. **Fully complete?** Yes, for the scope the frozen brief defines. The holder exists, the screen uses it, and
   both halves of the named test are built. The two unassigned pieces (wiring, real renewal) were recorded as
   out of scope at Phase 4, not discovered now.
2. **Every acceptance criterion?** Yes — 17/17, with AC9 in its corrected T21-D8 form and AC14/AC17 proven
   structurally for the stated reason.
3. **Any LOCKED decision violated?** No.
4. **Remaining risks?** The six above; 1 and 2 are the ones that need an owner, and neither is T21's to take.

**PASS** — the access token is held in one module-scoped variable, written only after T19's nonce check, cleared
on every discard, and nowhere else; 25/25 mutants killed and the one AC that was passing against nothing is now
proven against the real cross-tab listener.
