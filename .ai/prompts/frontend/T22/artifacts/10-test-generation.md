# frontend · T22 · Phase 10 — Test Manifest

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

**43 cases in one new file.** Suite **974 passed / 1 failed of 975**, the one failure being the known
environmental `oxlint` case, **verified by exit code**. **Mutation: 20 mutants, 20 killed, no survivors — and
every mutant is a real leak.** No production code changed (T22-D7).

---

## Files

| File | Cases | Why |
|---|---|---|
| `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` | **43** (new) | The named test (`package.md:157` → R7). The whole deliverable. |

Nothing else. `src/auth/` because every token in the package originates there; the scan still reads all 23
production modules. `.tsx` rather than `.ts` because the sweep drives the real screen.

## The shape

| Block | Cases | Covers |
|---|---|---|
| a completed sign-in leaves no token behind | 5 | AC1, AC2, AC3, AC5, AC7 |
| every failure path leaves no token behind | 11 | AC8, plus the two-sign-in and post-clear cases |
| the instruments catch what they are looking for | 8 | AC10, AC11, AC15, T22-D14 |
| the package itself names no leak | 16 | AC9, AC13, T22-D11, T22-D15 |
| what this file cannot prove here | 2 | AC4 |
| a sibling tab's token is no different | 1 | AC12 |

## Acceptance criteria → cases

| AC | Where |
|---|---|
| **AC0** | `ACCESS_TOKEN` / `OTHER_ACCESS_TOKEN` / `piecesOf()` and the header's T22-D2 paragraph; `state`, `nonce` and `code_verifier` are asserted **present**, not absent |
| **AC1** | *puts no part of either token in any surface, and holds the token it was given*; *sweeps a SECOND, unrelated token the same way*; *leaves nothing from a FIRST sign-in behind when a second one runs in the same tab* |
| **AC2** | *leaves session storage holding exactly L16's three items…* — whole-store equality against what the authorize step generated |
| **AC3** | *adds nothing to the cookie jar*, with the jsdom limitation stated |
| **AC4** | *has no IndexedDB and no Cache Storage to observe in this environment* + *leaves the Cache Storage policy to the module that owns it*; the prohibition itself is carried by the source scan |
| **AC5** | *leaves a token nowhere in the URL the SPA navigated to*, with the control that the router's location **does** carry `code=` and `state=` |
| **AC6** | the console assertion in the success case and in all nine failure rows; *finds a token handed to a console channel, including through an alias* |
| **AC7** | `expectThreeAuthorizeWrites` — keys and values in order, no `removeItem`, no `clear` — asserted in the success case and every failure row |
| **AC8** | the nine-row `FAULTS` table |
| **AC9** | the 12-row `FORBIDDEN` scan; the allowlist case; *touches session storage in only the four files that read it*; *leaves the three Error subclasses that name themselves alone*; *strips comments without emptying any file* |
| **AC10** | eight controls: an innocuous key, a bracket alias, a Bearer value, sentinels in the three empty-box surfaces, a node outside the container, a console alias, a throwing store, and a scan that refuses empty input |
| **AC11** | *says a surface was UNREADABLE rather than reporting it clean when touching it throws* |
| **AC12** | *keeps an adopted token out of every surface, through the real cross-tab listener* |
| **AC13** | no new string, key or dependency; `git status` clean of production files |
| **AC14** | below |
| **AC15** | *finds a token leaked as a bearer header value* |

## The five traps this file is built against

Each was **measured**, four in Phase 6 and two more after the file's first green run. They are the reason 43
cases exist rather than 15.

1. **The `setItem` spy installed too late.** Phase 6's probe reported **zero** writes for a flow that had just
   made three. `watchStorage()` is therefore called at the top of every case, before `startSignIn()`, and
   `expectThreeAuthorizeWrites` asserts three keys and three values in order — never an absence.
2. **`location.href` never moves.** `createMemoryRouter` keeps its location in router state and writes neither
   `window.location` nor `history`, so a sweep of `location.href` reads an untouched document and passes against
   anything — not even the authorization `code` is there. The sweep reads `router.state.location`, and the AC5
   case first asserts that location **contains** `code=THE-CODE`, so the absence that follows is a measurement.
3. **The wrong ID token.** Phase 6 searched for a constant built from a dead nonce while the flow carried one
   built from the live nonce. `tokensFor(nonce)` now builds the body from what the authorize step really
   generated, every case searches for `body.id_token` / `body.access_token`, and the success case asserts the
   holder holds the value it swept for (T22-D13).
4. **Nine rows failing twice.** Phase 6's failure matrix sent a mismatched nonce on every row, so no row could
   have held a token even if its own fault had not fired. `answer` now receives the live nonce and only the last
   row sends a mismatched one — each failure is the only reason its row holds nothing.
5. **Found after the first green run, by mutating:** the sweep read `history.state` but not the router's
   **navigate state**, which in a real browser *is* `history.state`. Mutant M12 passes a token in
   `navigate(…, { state })` and would have survived. Added as `routerNavigateState`, and M12 now dies. The
   second: the sessionStorage case asserted `path:line`, but stripping a block comment removes its newlines, so a
   line number in stripped text is not one a reader can look up — it asserts **files** now.

## Two instruments, proven rather than asserted (T22-D14)

| Shape | Source scan | Runtime spies |
|---|---|---|
| `localStorage.setItem(…)` | catches | catches |
| `globalThis['localStorage'].setItem(…)` | **misses** (bracket row added, but a bound alias still escapes) | **catches** — the alias resolves to `Storage.prototype.setItem` |
| `const c = console; c.log(token)` | **misses** | **catches** — same method object |
| a leak in code no test executes | **catches** | misses |

Both controls are cases, not claims. The header says which instrument is primary and why.

## Mutation — every mutant is a leak

Twenty leaks introduced into `accessToken.ts`, `CallbackScreen.tsx`, `codeExchange.ts`, `idTokenNonce.ts` and
`authorizeRequest.ts`, run against the named file (43 cases, baseline 0 failed). Tests committed first — the
harness reverts with `git checkout`.

| # | Leak | Verdict |
|---|---|---|
| M1 / M2 / M3 | the holder also writes the token to sessionStorage / localStorage / through a bracket alias | KILLED (9 / 8 / 9) |
| M4 / M5 | the holder logs the token / logs it through a bracket alias | KILLED (3 / 3) |
| M6 / M7 | the holder hangs it on `window.name` / sets a cookie | KILLED (5 / 16) |
| M8 / M9 | the screen puts it in `document.title` / stores the ID token | KILLED (4 / 5) |
| M10 / M11 / M12 | the screen puts it in `history.state` / the location hash / **navigate state** | KILLED (5 / 15 / 10) |
| M13 / M14 | the exchange logs the token / stores the raw payload | KILLED (3 / 10) |
| M15 | the nonce check stores the whole ID token | KILLED (6) |
| M16 / M17 / M18 / M19 | the authorize step deletes an item / writes a fourth / clears the store / writes the verifier under a second key | KILLED (11 / 12 / 11 / 12) |
| M20 | a write wrapped in an empty `catch`, the shape that defeats a naive sweep | KILLED (9) |

**20 / 20.** Three are worth naming:

- **M12** is the one that found a hole. It survived the file as first written, which is how
  `routerNavigateState` came to exist.
- **M16** dies to the allowlist table, not to a storage sweep: deleting an item leaks nothing, but it is **task
  23's** work arriving unannounced, and T22-D15 exists so that shows up as a named violation with instructions.
- **M3** and **M5** are the alias pair. They die only because the primary instrument spies the prototype and the
  channel objects; the text scan cannot see either.

## Verification

```
$ npx -y pnpm@12.14.0 -C frontend exec vitest run src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx
  Tests  43 passed (43)                exit 0

$ npx -y pnpm@12.14.0 -C frontend exec vitest run
  Tests  1 failed | 974 passed (975)   exit 1
  ↳ src/api/lintRejectsHandWrittenBackendFetch.test.ts:118 (AC4) — this host's oxlint@1.87.0 prints no
    summary line. Environmental, unchanged since T18, the only failure in the run.

$ npx -y pnpm@12.14.0 -C frontend run typecheck
  tsc --noEmit                         exit 0
```

## Handoffs

- **Phase 11** should look hardest at the sentinel cases. They prove the sweep *reads* a surface; they cannot
  prove a leak that does not exist would have landed there. The file says so, and that wording is the claim to
  challenge.
- **Task 23** is the next entrant in `STORAGE_ALLOWLIST`, and the AC2 whole-store equality is the assertion it
  will have to change when it consumes the three items.
- **Recorded, not taken (T22-D12):** banning `localStorage`, `indexedDB` and `openDatabase` as `oxlint`
  restricted globals would be an alias-proof *static* guard with no legitimate exception. The cost is T18's AC5,
  which pins that config by set equality with L6's message.
- **Still unassigned package-wide:** nothing wires the composition root; silent re-authentication has no task; no
  task owns a real-browser end-to-end check, which is what AC4 and the cookie surface would need.
