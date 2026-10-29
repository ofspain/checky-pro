# frontend · T26 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` (FROZEN) |
| **Produces** | `artifacts/05-implementation-plan.md` |

No code in this phase. Plan only, against the frozen brief and nothing else.

---

## Part 0 — Measurements taken before planning

A throwaway probe was run and deleted (`src/auth/__t26probe.test.tsx`, one vitest run, output in the session
scratchpad). Planning a sweep around an assumed store is how a sweep ends up reading nothing, so the store was
measured first. Nine results, four of which change the plan:

| # | Measured | Consequence |
|---|---|---|
| **M1** | Mounting the SPA at `/app/sign-in` with nothing started leaves **both stores empty** — `session: []`, `local: []`. | The router writes no key, and `signInNotice.ts` keeps its notice in a module variable, citing L16's exhaustiveness in its own header. The inventory has no third-party entrant to tolerate. |
| **M2** | After `buildAuthorizeRequest()`: exactly the three PKCE keys. After `rememberReturnTarget('/home')`: exactly four. | The inventory is four keys and nothing else, at the two states where it is fullest. |
| **M3** | After a successful callback the store is **empty** and the visitor is at `/app/account/sessions`. | The end state of a complete flow is zero keys, not four. AC2's success row asserts an empty store. |
| **M4** | `sessionStorage.length` is 4 and `key(i)` enumerates in **insertion order**: `state`, `nonce`, `code_verifier`, `return_to`. `Object.keys` and `getOwnPropertyNames` return the same four. | Three independent enumerations agree; the sweep can use `Object.keys` and cross-check `length`/`key(i)` so a single broken enumeration cannot report "empty". |
| **M5** | **With `getItem` throwing, `Object.keys` still returns all four and `length` is still 4.** Only `getItem` itself throws. | **This changes AC12.** A key-only sweep is *immune* to a throwing store, so it passes while reading nothing of the values — exactly F3's vacuity, now measured. The guard must read **values as well as keys**, and must fail loudly when a value read throws rather than treat it as absence. |
| **M6** | A `Storage.prototype` spy sees **all four indirect forms**: `s['setItem'](…)`, a `.bind` alias, `Storage.prototype.setItem.call(s, …)` and `.apply(s, […])`. | T22-D14's argument, measured: the runtime instrument is alias-proof by construction and the source scan is not. F1's cases assert the pair. |
| **M7** | Those indirect writes really landed: the store then held `probe.apply`, `probe.bound`, `probe.bracket`, `probe.call` beside the four. | The violation the controls inject is a real write, not a spied no-op, so the sweep is proven against a store that is genuinely dirty. |
| **M8** | jsdom: `indexedDB`, `caches`, `cookieStore` and `navigator.storage` are all `undefined`; `document.cookie` is `""`. | Confirms T22's measurement and extends it to `navigator.storage` (F4). Those surfaces are provable **structurally only**, and the file must say so rather than assert over an API that does not exist. |
| **M9** | `localStorage` is empty at every state of every flow driven. | AC6 is assertable behaviourally as well as structurally. |

## Files to create

- `frontend/src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx` — the named test, the only authorized new file.

## Files to modify

None. The frozen brief anticipates no production change; one found in Phase 6 is a finding for the Phase 9 gate.

## Public methods (signatures)

None — this task adds no production API. The file's own exported surface is empty; what follows are its internal
helpers, planned here because the brief's acceptance criteria are claims about them.

## Private methods (the file's helpers)

**The inventory**

```ts
type Item = { key: string; owner: string; why: string };
function inventory(): Item[]            // derived: the three AUTHORIZE_STORAGE_KEYS values + RETURN_TARGET_KEY
function inventoryKeys(): string[]      // sorted keys only
function expectedNextEntrant(): string  // the text naming L16 clause (b), used in failure messages (AC10)
```

`inventory()` is a **function, not a module-scope constant** (brief constraint): a module-scope read of a name
that participates in the `routes → CallbackScreen → returnTarget → routes` cycle yields `undefined` and fails
closed silently, which here would mean an inventory of nothing matching every store. Each row carries its owner
and reason so a failure says what to do, the shape T22's `STORAGE_ALLOWLIST` established.

**The store sweep (AC1, AC2, AC12)**

```ts
type Sweep =
  | { reachable: true; keys: string[]; values: Record<string, string> }
  | { reachable: false; how: string };
function sweepSession(): Sweep          // keys from Object.keys, cross-checked against length + key(i) (M4)
function sweepLocal(): Sweep
function expectOnlyInventory(sweep: Sweep, where: string): void
```

`sweepSession` reads **keys and every value**, because M5 measured that key enumeration survives a throwing
`getItem`. A value read that throws yields `{ reachable: false }`, and `expectOnlyInventory` **fails** on an
unreachable store rather than passing on an empty key list — the distinction AC12 names. The three enumerations
are cross-checked so that one broken enumeration cannot produce a false "empty".

**The write watch (AC3, AC4)**

```ts
type StorageWatch = { setItem: Mock; removeItem: Mock; clear: Mock };
function watchStorage(): StorageWatch                 // spies Storage.prototype, installed BEFORE the flow
function writtenKeys(w: StorageWatch): string[]
function removedKeys(w: StorageWatch): string[]
function expectNoKeyOutsideInventory(w: StorageWatch): void   // over writes AND removals, never `clear`
```

Prototype-level, because M6 proves that is what makes it alias-proof. Asserted over the *call log*, so a key
written and later removed still fails (AC4).

**The flow (AC1, AC2)**

```ts
async function startSignIn(): Promise<Triple>   // the real authorize step, refusing a triple it cannot read back
async function arriveAt(url: string, answer: () => Response | Promise<Response>): Promise<Arrival>
function stubFetch(answer): Mock
function tokenWith(claims: unknown): string
function jsonResponse(body: unknown, status?: number): Response
function refusal(): Response
async function turn(): Promise<void>
```

Reimplemented in this file rather than imported from a neighbouring test: T24 and T25 did the same, the route
graph must be imported **dynamically** inside `arriveAt` (a statically imported module is a different instance
after `vi.resetModules()`), and a shared harness across named tests would couple two files' failure modes.

**The source scan (AC3, AC6, AC7)**

```ts
type ScanTarget = { path: string; code: string };
async function productionSources(): Promise<ScanTarget[]>  // comment-stripped; refuses an empty tree
function strip(source: string): string
function hits(targets: ScanTarget[], pattern: RegExp): string[]
const WRITE_METHODS: Array<{ name: string; patterns: RegExp[] }>   // direct + bracket + .call/.apply/.bind (F1)
const FORBIDDEN_SURFACES: Array<{ name: string; pattern: RegExp }> // six names (F4), a NAME scan (F5)
const PERMITTED_WRITERS: Array<{ file: string; methods: string[]; keys: string[]; why: string }>
```

Two scans with **two messages** (F5): AC3 asks *who calls a write method*, AC7 asks *who names a surface*. A
module that merely named `caches` in live code is suspicious but is not a method call, and the failure text must
not claim otherwise.

**The value sweep (AC5, AC11)**

```ts
function piecesOf(token: string): string[]        // whole, each of the three segments, each decoded claim value
function secretsOf(flow: FlowSecrets): string[]   // tokens, auth code, password, recovery code, API key, PII
function expectNoSecretInStore(sweep: Sweep, secrets: string[]): void
```

The authorization code is in `secretsOf` (F2). The Phase 4 correction stands behind that: T22 excluded the code
by name and handed it to task 24, which swept the location rather than the store, so **no file in the package
asserts the code never reaches a store**. T26 is where that lands.

## Entities / Repositories / Services used

Not applicable — no persistence layer, no DI container, no backend call. The analogous list:

- **Production modules under test:** `auth/authorizeRequest.ts`, `auth/returnTarget.ts`, `auth/CallbackScreen.tsx`,
  `auth/codeExchange.ts`, `auth/idTokenNonce.ts`, `auth/accessToken.ts`, `app/routes.tsx`.
- **Exported constants consumed:** `AUTHORIZE_STORAGE_KEYS`, `RETURN_TARGET_KEY`.
- **Exported functions consumed:** `buildAuthorizeRequest`, `consumeAuthorizeState`, `rememberReturnTarget`,
  `takeReturnTarget`, `isAllowlistedTarget`.
- **Test infrastructure:** `vitest`, jsdom, `react` `act` + `StrictMode`, `createRoot`, `createMemoryRouter` /
  `RouterProvider`, `I18nextProvider` + `createI18n()`, `node:fs/promises`.

## Unit / integration tests required

One file, ten blocks, each mapped to the frozen brief's criteria:

| Block | Content | AC |
|---|---|---|
| **B1 · The inventory** | Four keys, derived from the two constants; the count pinned with a message naming the carve-out and clause (b) as the expected next entrant; a scan of this file's own source proving **no key literal** appears in it. | AC1, AC8, AC10 |
| **B2 · Flow states** | Nothing started (empty, M1); after authorize (three, M2); target remembered (four, M2); successful callback (**empty**, M3); and each discard — mismatched `state`, missing `state`, nonce mismatch, refused code, server fault, unusable body. | AC2 |
| **B3 · Write-path identity** | Every `setItem` and `removeItem` key and value over a whole flow, in order; no out-of-inventory key at any point; `clear` never called. | AC3, AC4 |
| **B4 · Alias and indirection** | Bracket, `.bind`, `.call`, `.apply`: each fails the source scan, **and** each is caught by the prototype spy (M6), **and** each really lands in the store (M7). One case states which instrument caught what and why both exist. | AC3, AC9 |
| **B5 · Value sweep** | Over every stored key and value: whole access token, whole ID token, its three segments, each decoded claim value, a synthetic `refresh_token`, the **authorization code**, a password, a recovery code, an API key, an email address and an account UUID. | AC5, AC11 |
| **B6 · `localStorage`** | Empty after every flow (M9), and no production module writes to it. | AC6 |
| **B7 · Structural surfaces** | No production module names `indexedDB`, `document.cookie`, `cookieStore`, `caches`, `navigator.storage` or `webkitRequestFileSystem`; the claim is stated as **structural** because jsdom supplies none of them (M8), and the list as **enumerative, not exhaustive**. | AC7 |
| **B8 · Reachability** | A store whose value read throws fails as **unreachable**; a genuinely empty store passes as **empty**; and the control that makes this a measurement — with `getItem` throwing, key enumeration still returns all four (M5), so a key-only sweep would have passed while reading nothing. | AC12, AC9 |
| **B9 · Controls** | An injected out-of-inventory write fails the sweep; a key written **and then removed** still fails (F7); a synthetic file naming a forbidden surface without calling it fails the name scan (F5); a synthetic offending source line fails the method scan; an empty source tree is refused; a watch installed one line **late** is shown to report zero writes for a flow that made four. | AC9 |
| **B10 · Repetition** | Two sign-ins in one tab; a replayed callback; and **discard → fresh sign-in → completed callback** with an inventory assertion after each step (F8). | AC1, AC2, AC8 |

## Execution order

The brief's "front-load schema, then dao, service, api, tests" maps onto a verification-only task as
**instruments first, then the claims that stand on them, then the controls that prove they can fail** — because
every ordering mistake in this package has been an instrument believed before it was tested.

1. **The inventory (B1).** Nothing else can be asserted before the thing everything is compared against exists,
   and it is one function. Includes the self-scan for key literals, so the rule cannot rot as the file grows.
2. **The sweep and the reachability split (B8's helpers).** Built second, because M5 means the obvious
   implementation is the vacuous one. The `{reachable}` discrimination is designed in, not retrofitted.
3. **The write watch (B3's helpers).** Prototype-level, with the installation-order control from B9 written **in
   the same sitting** — T22 measured a watch installed one line late reporting zero writes for a flow that had
   made three, and that control is what keeps the ordering honest.
4. **The flow harness (B2's helpers).** `arriveAt` with the dynamic route import, `startSignIn` with its
   read-back refusal built in so every later "the sign-in completed" assertion stands on a measured store.
5. **B2, then B3** — the two behavioural blocks, in that order: a state inventory is the simpler claim and its
   failures are easier to read, and the write-path block reuses its flows.
6. **B5, B6** — the value and `localStorage` sweeps, which need the flows from step 5.
7. **The source scan (B7, and AC3's half of B4).** Independent of the runtime work, deliberately later: it is the
   second net, and writing it first invites treating it as the primary one.
8. **B4** — the indirection block, which needs both instruments to exist before it can assert the pair.
9. **B9** — every remaining control, each paired with the claim it guards, so no block above is believed on its
   own word.
10. **B10** — the repetition sequences, last because they compose everything above.
11. **Commit, then mutate.** The mutation harness reverts with `git checkout`; uncommitted test edits are
    destroyed silently. The harness is **committed with the artifacts** this time, not left in a scratchpad.
12. **Run and read the exit code**, not the pass count. The one expected failure is the pre-existing
    environmental `lintRejectsHandWrittenBackendFetch.test.ts:118`.

## Risks this plan is built against

- **Vacuity is the default failure of a sweep.** M5 is the measured instance: the natural implementation passes
  while reading nothing. Hence step 2 before any claim, and B8 holding the control that shows it.
- **An empty store is also what a bug produces.** T25's escaped `clear()` mutant came from exactly this; `clear`
  is asserted never-called in B3 rather than inferred from a store that looks right.
- **A guard that cannot grow.** Clause (b) is permitted and unbuilt; B1's failure message names it as the
  expected next entrant, so the day R29's store lands the failure reads as an instruction rather than a mystery.

---

**Phase 5 complete.** No code written. Nine measurements, one new file, ten blocks, twelve execution steps.
