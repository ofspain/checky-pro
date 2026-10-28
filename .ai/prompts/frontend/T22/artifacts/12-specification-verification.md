# frontend · T22 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/11-test-review.md` and every prior artifact |
| **Produces** | `artifacts/12-specification-verification.md` |

Task statement: *"No token is written to local storage, session storage, IndexedDB, a cookie, a URL, or a log.
Verifies `tokensAreNeverWrittenToAnyStorage`. Cites R7, L3."*

**The change set is one file, and it is the test.**

| File | Change |
|---|---|
| `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` | **new**, 956 lines, **47 cases** |
| everything else | **unchanged** (T22-D7) |

No production file, no contract, no dependency, no catalogue key, no `.oxlintrc.json` change (T22-D12).

---

## Traceability

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R7** third clause — the access token is held in memory only | Yes, **pre-existing** (T21) | `accessToken.ts:24` one module-scoped `let`; the only storage writer in the service is `authorizeRequest.ts:47-49` | `…AnyStorage.test.tsx:486` success sweep; `:635` nine failure rows | No | No |
| **L3** — never written to **storage** | Yes | nothing in 23 production files names `localStorage`, `indexedDB` or `openDatabase` | `:813` the 14-row `FORBIDDEN` scan; `:823` the allowlist; `:709`/`:717` the leak controls | No | No |
| **L3** — never written to **logs** | Yes | no `console` call in any production file | `:778` the console-and-alias control; every flow case asserts `loggedText(logs) === ''` | No | No — T22-D4 also counts a thrown `Error`'s message |
| **L3** — never to **analytics** | Yes, **by decision** | no analytics dependency; `package.md` **Q9**: none at launch | `:813` rows for `sendBeacon`, `new Image`/`Audio`, `.src =`, absolute addresses | No | No |
| `agents.md:79` — **never placed in a URL** | Yes | the SPA's only navigation is `navigate('/sign-in', { replace: true })`, carrying no state | `:580`, with the control that the router's location **does** carry `code=` and `state=` | No | **T22-D3**: the authorization `code` SAS put there is not swept — it is a one-time credential, not a token, and task 24 owns the navigation (**T22-D9**) |
| `agents.md:80-81` — sessionStorage limited to L16 | Yes | `authorizeRequest.ts:47-49`, three items, no fourth | `:545` whole-store equality against the values the authorize step generated | No | No |
| **R29** prohibition half — no token, password, recovery code, API key or PII in any storage | Yes, for tokens | as above | the sweep | the TTL half and the full inventory are **task 26** | No |
| **L16** — the carve-out is exactly three items (+R29 form input) | Yes | — | `:545`; the three are asserted **present**, since L16 names them | deleting them is **task 23** | No |
| **L17 / R57** — the Cache Storage surface | Yes, **referenced not duplicated** | `apiRequestPolicy.ts` states why Cache Storage counts as storage; `/oauth2` and `/accounts` are NetworkOnly | `:913` reads the real policy module | the runtime behaviour is R57's own named test, already built | No |
| **IndexedDB** | **Structurally only** | no production file names it | `:904` records `indexedDB`, `caches`, `cookieStore` all `undefined` in jsdom | a real-browser check has **no owning task** | Stated, not implied (§7 of the frozen brief) |
| **L13** — no new dependency, no state library | Yes | `package.json` untouched | — | No | No |
| **L4** — neutral copy | Yes | no new string | — | No | No |
| Named test `tokensAreNeverWrittenToAnyStorage` | **Yes, new** | the file | 47/47, exit 0 | No | No |
| Named test `callbackValidatesNonceAndKeepsAccessTokenInMemory` | T19 + T21, built | — | 108 cases, green | No | No |
| Named test `callbackValidatesNonce` | T19, built | — | green | No | No |
| Named test `returnTargetOutsideAllowlistFallsBackToHome` | **Not T22** | — | — | tasks **24, 25** (`tasks.md:36-37`) | No |

### Acceptance criteria

**AC0-AC15: all pass.** Four are worth restating because the *form* of the evidence is the decision:

- **AC4** is **structural, and says so in the file.** jsdom provides no `indexedDB`, so
  `expect(indexedDB.databases()).toEqual([])` would throw and `typeof indexedDB === 'undefined'` would assert the
  environment rather than the code. The prohibition is carried by the source scan; `:904` exists to keep the
  limitation in front of a reader.
- **AC5** is proven against `router.state.location`, **not** `location.href`. `createMemoryRouter` writes neither
  `window.location` nor `history`, so the obvious instrument is inert — measured in Phase 6, where every dump
  showed an untouched document URL that did not even contain the authorization `code`.
- **AC10** is eleven controls, not one. Each instrument is shown catching a leak before its clean result is
  believed: an innocuous key, a bracket alias, a Bearer value, a decoded claim, three empty-box sentinels, a node
  outside the container, a console alias, a throwing store, and a scan that refuses empty input.
- **AC11** required a specific design: a refusal must look **different** from an absence. A sweep that swallowed
  a throwing store would hand back an empty list, which reads exactly like a clean store — so `storeEntries`
  returns `UNREADABLE` and `:790` asserts the sweep says so while the planted token is invisible.

### LOCKED decisions

None violated. **L3** is the task's own and is what is proven. **L16** is honoured by asserting its three items
**present** rather than absent — the one place where forbidding a credential would have contradicted the spec.
**L13**, **L4**, **L9**, **L17** unaffected. **T22-D12** deliberately leaves `.oxlintrc.json` alone, because
widening it would edit T18's AC5, which pins that config by set equality.

## What the reviews changed

Four findings changed the deliverable, and three of them were cases of something looking verified that was not:

1. **Phase 6's own measurement — the sweep found the sweep's bugs.** The `setItem` spy installed after the
   authorize step reported **zero** writes for a flow that had just made three; `location.href` never moves under
   a memory router; and `title`, `history.state` and `window.name` were empty boxes.
2. **Phase 7 (self) — I searched for the wrong ID token.** The probe built the token from the live nonce and
   searched for a constant built from a dead one. Two of three segment searches survived only because the header
   and the filler signature matched.
3. **Phase 8 I2 — nine failure rows failed twice**, so none measured its own failure.
4. **Phase 11 G1 — decoded claim values were never searched**, although T22-D2's own definition names them. A
   surface holding `the-subject` contains no part of the segment that encodes it. Mutant **M25** settles that this
   fix is load-bearing: a decoded claim written to `document.title` is caught by nothing else, since no
   whole-store equality can see the title.

Two findings were accepted with their mechanism corrected: **I6** (a scan for `/console\./` already catches
`globalThis.console.log`; what no regex catches is a bound alias, which is why the runtime spies are the primary
instrument) and **G5** (`\bclear\s*\(` does not match `clearTimeout(` — measured; the real ambiguity is `Map`
and `Set`, so `pending.clear()` would have been reported as a storage offence).

## Measurements

```
named file   47 / 47                        exit 0
suite        978 passed / 1 failed of 979   exit 1 — the known oxlint-summary case only
typecheck    tsc --noEmit                   exit 0
mutation     25 mutants, 25 killed, 0 survivors — every mutant a real leak
```

The mutants span `accessToken.ts`, `CallbackScreen.tsx`, `codeExchange.ts`, `idTokenNonce.ts` and
`authorizeRequest.ts`: the token into both web stores, a bracket alias, a cookie, `window.name` dotted and
bracketed, `document.title`, `history.state`, **navigate state**, the location hash, two console paths, the raw
payload, the whole ID token, a **decoded claim** into a store and into the title, a kept `refresh_token`, a
storage call inside `eval`, a fourth sessionStorage item, a second key for the verifier, an unannounced
`removeItem`, a `clear`, and a write wrapped in an empty `catch`.

**Two mutants found holes rather than confirming coverage:** **M12** (navigate state) survived the file as first
written, which is how `routerNavigateState` exists; **M25** is the proof that Phase 11 G1 mattered.

## Remaining risks

1. **No task owns a real-browser check.** AC4 (IndexedDB), the Cache Storage runtime and the cookie surface
   beyond `document.cookie` cannot be proven under jsdom. The file states this rather than implying coverage, but
   the gap is real and unassigned in `tasks.md` 1-39.
2. **The sentinel cases prove reachability, not reach.** They show the sweep reads a surface; they cannot show
   that a leak which does not exist would have landed there. That is the honest maximum and the file says so.
3. **The source scan is text.** A bound alias, a storage call built by string concatenation, or one inside
   minified dependency code is invisible to it. The runtime spies cover the first two when the code runs; the
   third is covered by neither (T22-D1), and remains `bundleContainsNoSecretEnvKeys`' and dependency review's
   concern.
4. **An alias-proof static guard is available and not taken** (T22-D12): three `oxlint` restricted globals, at
   the cost of T18's AC5 set-equality and a decision that the lint config enforces L3 as well as L6.
5. **Task 23 will have to edit this file** — the `STORAGE_ALLOWLIST` row and the AC2 whole-store equality. That is
   intended: T22-D15 exists so an unannounced deletion shows up as a named violation with instructions, and
   mutant M16 proves it does.
6. **The authorization `code` stays in the callback URL** (T22-D3/D9) until task 24 moves the visitor. Out of
   scope, recorded in the file, and not claimed clean.
7. **Carried, unchanged:** nothing wires the composition root; silent re-authentication has no task.

## Verdict

1. **Fully complete?** Yes. The named test exists, drives the real flow, sweeps every surface the task statement
   names plus the two T22-D8 added, and scans all 23 production modules.
2. **Every acceptance criterion?** Yes — 16/16 (AC0-AC15), with AC4 structural for the stated reason and AC5
   proven against the surface that actually moves.
3. **Any LOCKED decision violated?** No.
4. **Remaining risks?** The seven above; only (1) needs an owner, and it is not T22's to take.

**PASS** — no token reaches any storage, URL or log in this package; the claim is carried by an instrument that
is shown catching eleven planted leaks and kills 25 of 25 mutants, and every surface it cannot prove here is
named rather than implied.
