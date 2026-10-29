# frontend · T26 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

> This brief is the only specification the implementation and review phases use. It is frozen at Phase 4.

---

## Task

Build `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx`, the package-wide guard that sessionStorage holds
nothing beyond L16's list. One new test file; no production change anticipated.

## Purpose

Twelve tasks have written to a client-side store, each proving its own item was allowed. T26 asks the opposite
question — **is anything there that is not on the list?** — and it is the only task in the package that asks it.
L16's own words are "this list is the entire carve-out", and an exhaustive claim cannot be proven item by item: it
needs one place that enumerates the whole inventory and sweeps the surface against it.

The reason the claim is worth a task: an out-of-inventory item changes no behaviour. A token parked in
sessionStorage, a form field holding an email address, a debug key left behind by a future screen — each is
invisible until something reads it, and none breaks a test that was not looking for it. So the entire value of
this file is **its ability to report DIRTY**, which is why every instrument below is proven against a synthetic
violation before it is believed.

**Measured starting position** (Phase 0, by `grep` over `src/**/*.ts{,x}` excluding tests): the production surface
that touches a client-side store is **two writing modules and four keys**. `auth/authorizeRequest.ts` writes and
removes `AUTHORIZE_STORAGE_KEYS.{state,nonce,codeVerifier}`; `auth/returnTarget.ts` writes, reads and removes
`RETURN_TARGET_KEY`. Three further modules read a key back. `localStorage`, `indexedDB`, `document.cookie` and
`caches` have **no production caller at all**. Both key sets are already exported — `AUTHORIZE_STORAGE_KEYS` was
published in T15 with a comment saying it exists "so task 26's carve-out guard enumerates the same keys, rather
than either copying the strings".

## Scope

**In**
- One new test file holding the inventory guard.
- The inventory **derived** from the two existing exported constants, never retyped as literals.
- A runtime sweep over a real sign-in → callback flow and over every discard path, at every intermediate state.
- A write-path assertion, so a key written and then removed is still a violation.
- A source scan proving which production modules may call a storage write method, and that four surfaces are
  never named at all.
- Controls that make each instrument fail.

**Out**
- L16 clause (b): the R29 mid-task form store and its 15-minute TTL. Not built anywhere, not scoped by
  `tasks.md:38`, and its named test `expiredSessionMidTaskPreservesOnlyNonSensitiveInput` is claimed by no task.
  T26 records it as the expected next entrant and must not be written so that building it is a violation.
- Re-proving T22 (`tokensAreNeverWrittenToAnyStorage`): the token sweep here is the *value* half of the inventory
  claim, asserted over whatever this file's flows store, not a second full leak hunt.
- Re-proving T23's deletion ordering or T24/T25's allowlist. This file asserts *which keys exist*, not whether a
  target was honoured.
- The service-worker cache (L17, R57's own tests), the query cache, and any backend call.
- Any production change. If Phase 6 finds one is needed, that is a finding to resolve at the Phase 9 gate.

## Business Rules

- **R29** — only non-sensitive form input may sit in sessionStorage, with a 15-minute TTL; no token, password,
  recovery code, API key or PII in **any** storage.
- **R7 / L3 boundary** — the access token is held in memory only; T26 must not contradict it, and asserts it over
  its own flows rather than re-proving T22.

## Locked Decisions

- **L16** — the sessionStorage carve-out is exhaustive: (a) `state`, `nonce`, `code_verifier` and the post-login
  return target, single-use and deleted at callback; (b) non-sensitive mid-task form input for R29 with a
  15-minute TTL. Nothing else client-side; no PII in either case.
- **L3** — tokens never reach storage, logs or analytics.
- **L17** — the service-worker cache holds the static shell and hashed assets only; it is the one client-side
  surface L16's absolute sentence does not govern (see Open Questions).
- **`agents.md:79-81`** — authoritative, not to be restated in code: sessionStorage is the only sanctioned
  client-side storage for sensitive data, and only for the L16 list; no PII is ever persisted client-side.
- **`design.md` §5** — the per-surface inventory: memory / sessionStorage / localStorage / SW cache / query cache.
- **T24-D4 (inherited)** — the carve-out is two constants in two modules, deliberately unmerged, because the PKCE
  three are deleted for having been consumed by the exchange and the target for having been read by the
  navigation. **T26 enumerates both; it does not merge them.**

## Dependencies

`AUTHORIZE_STORAGE_KEYS`, `RETURN_TARGET_KEY`; `buildAuthorizeRequest`, `consumeAuthorizeState`,
`rememberReturnTarget`, `takeReturnTarget`, `isAllowlistedTarget`; `CallbackScreen`, `routes.tsx`,
`createI18n()`; `vitest`, `jsdom`, `react` `act`, `createRoot`, `createMemoryRouter`/`RouterProvider`,
`node:fs/promises`. No config keys, no contracts.

## Inputs

Not an API. The guard's inputs are: the two key constants, the real flow driven in jsdom (authorize → callback →
exchange, and each discard path), the production source tree under `src/`, and synthetic violations used as
controls.

## Outputs

A test file whose failures name the offending key, the offending module, and what to do about it.

## State Changes

None in production. Within a test: sessionStorage and localStorage are written by the flow under test and cleared
between cases; `Storage.prototype` methods are spied and restored.

## Files to Create

- `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx`

## Files to Modify

None anticipated.

## Files NOT to Modify

- Anything under `spec/`.
- `src/auth/authorizeRequest.ts`, `src/auth/returnTarget.ts`, `src/auth/CallbackScreen.tsx`,
  `src/auth/codeExchange.ts`, `src/auth/idTokenNonce.ts`, `src/auth/accessToken.ts` — the subjects of the guard.
- `src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx`, `stateSurvivesRedirectAndIsConsumedOnce.test.tsx`,
  `returnTargetOutsideAllowlistFallsBackToHome.test.tsx` — read as patterns, not edited. A claim T26 needs that
  one of them already makes is cited, not moved.

## Acceptance Criteria

- **AC1** — After a complete flow, the sessionStorage key set is a subset of the inventory, and the inventory is
  derived from `AUTHORIZE_STORAGE_KEYS` and `RETURN_TARGET_KEY`, never from literals. (R29, L16, T24-D4)
- **AC2** — The same holds at **every intermediate state**: nothing started; authorize written; target
  remembered; successful callback; and each discard path. (R29, L16)
- **AC3** — Every production module that calls `setItem`, `removeItem` or `clear` on a storage object is one of
  the two the inventory names, and writes only its own keys. (L16, R29)
- **AC4** — No key outside the inventory is **ever written**, asserted on the write path, so a write-then-delete
  is still a violation. (R29, L16)
- **AC5** — No token, token segment, decoded claim value, password, recovery code or API key appears as any
  stored value or inside any stored key. (R29, L3)
- **AC6** — `localStorage` is empty after every flow, and no production module writes to it. (R29, `design.md:193`)
- **AC7** — No production module names `indexedDB`, `document.cookie`, `cookieStore` or `caches`; proven
  structurally and **stated as structural**, because jsdom supplies none of them. (R29)
- **AC8** — The inventory is exhaustive and pinned: a fifth key fails the guard, and the message names the key,
  the module and the carve-out. (L16)
- **AC9** — Each instrument is proven to report DIRTY before it is believed: a synthetic out-of-inventory write
  fails the runtime sweep; a synthetic offending source line fails the scan; a scan over an empty tree is
  refused. (T22-D14 pattern)
- **AC10** — L16 clause (b) is recorded as permitted-but-unbuilt, in the file, naming its unowned named test, so
  a future R29 store is an expected entrant rather than a surprise failure. (R29, L16(b))
- **AC11** — No PII reaches any storage — distinct from AC5, because PII would be the *value* of a legitimately
  named key. (R29, `agents.md:81`)

## Required Tests

Named test: **`sessionStorageHoldsOnlyTheL16Items`** → R29 (`package.md:160`).

1. **Inventory derivation** — exactly four keys, assembled from the two constants; a count pin with a message
   naming the carve-out; no string literal of a key anywhere in the file.
2. **Flow states** — nothing started (empty); after `buildAuthorizeRequest()` (three); with a target remembered
   (four); after a successful callback (PKCE three removed, target consumed); after each discard — mismatched
   `state`, missing `state`, nonce mismatch, exchange failure, and a store that throws.
3. **Write-path identity** — every `setItem` key and value over a whole flow, in order; no out-of-inventory key
   at any point, including one later removed.
4. **Alias resistance** — a write through `globalThis['sessionStorage']` and through a captured
   `const s = sessionStorage` are both seen, with a control that proves it.
5. **Value sweep** — over every stored key and value: whole access token, whole ID token, each of its three
   segments, each decoded claim value, a synthetic `refresh_token`, a password, a recovery code, an API key, plus
   an email address and an account UUID as PII stand-ins.
6. **`localStorage`** — empty after every flow; structurally uncalled in production source.
7. **Source scan** — the two permitted modules and their methods; the four never-named surfaces; comments
   stripped before scanning, because a file explains the rule it obeys.
8. **Controls** — an injected out-of-inventory write fails the sweep; an injected offending line fails the scan;
   an empty tree is refused; a sweep installed *after* the code under test is shown to miss what the correct
   order catches.
9. **Repetition** — two sign-ins in one tab, and a replayed callback, add no fifth key.

## Constraints

- **Security.** The file must not weaken an existing claim to make its own pass. Where a claim it needs already
  exists (T22's token hunt, T23's deletion ordering, T24/T25's allowlist), cite it; do not move or duplicate it.
- **Module boundaries.** Keys come from the two exported constants. A literal key string in this file is a defect,
  because it would keep passing after a rename.
- **No module-scope read of a route-table or key name that participates in the import cycle.** `returnTarget.ts`
  documents the hazard: a module-scope read inside the cycle yields `undefined` and **fails closed silently** —
  which here would mean an inventory of nothing, matching every store.
- **Instrument ordering.** A `Storage.prototype` or console spy must be installed before the code under test
  runs; the file must contain the control that demonstrates this.
- **Comment stripping before any source scan**, and assertions over *files*, not `path:line` — stripping a block
  comment removes its newlines.
- **Null / absence handling.** `getItem` may throw rather than return `null` (blocked site data); the guard must
  distinguish "the store is empty" from "the store is unreachable", and must not pass for the second reason.
- **Vacuity.** An empty source tree, a zero-key inventory and a sweep that reads no surface each satisfy every
  prohibition at once. Each must be refused explicitly.
- **Verification.** `npx -y pnpm@12.14.0 exec vitest run …` from `frontend/`; the **exit code** is the verdict,
  not the pass count. One known environmental failure is pre-existing and unrelated:
  `lintRejectsHandWrittenBackendFetch.test.ts:118`.
- **Mutation discipline.** Tests are committed before any mutation run; the harness reverts with `git checkout`.
  The harness must be committed with the task's artifacts, not left in a scratchpad.
- **Performance / thread-safety / transactions.** Not applicable: no concurrency, no transaction, single tab per
  test. Cross-tab coherence (L18) runs over BroadcastChannel and touches no store.

## Open Questions

**No blockers.** Three items are flagged for the Phase 4 gate to confirm, each with the default reading this
brief proceeds under:

1. **Clause (b) admission.** The guard pins the four keys that exist and fails loudly when the carve-out grows,
   naming clause (b) as the expected next entrant — the convention T22's `STORAGE_ALLOWLIST` already uses for a
   new row. The alternative, leaving room for an unspecified key, verifies less.
2. **`expiredSessionMidTaskPreservesOnlyNonSensitiveInput`.** The generated phase header lists it among this
   task's named tests; `tasks.md:38` names only `sessionStorageHoldsOnlyTheL16Items`, and no task in the package
   claims it. The spec files are authoritative over the generated header, so it stays out of scope. **Owner
   action:** assign clause (b) to a task or record it as deferred.
3. **"Nothing else is stored client-side" versus the service-worker precache.** Read as scoped by `design.md` §5
   to the key-value stores, with the cache governed by L17 — the only reading under which the built SPA is
   compliant. R57's own tests own the cache.

---

**Phase 2 complete.** Brief ready for the Phase 3 adversarial review.
