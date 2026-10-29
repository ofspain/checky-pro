# frontend · T26 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

---

## Business Rules

| ID | Rule |
|---|---|
| **R29** | If the session expires during a task, the SPA keeps **only non-sensitive form input** in sessionStorage with a **15-minute TTL**, re-authenticates, and keeps **no** tokens, passwords, recovery codes, API keys or PII in **any** storage (`requirements.md:101-103`). |
| **R7** (boundary only) | The access token is held **in memory only** — the one claim that makes "nothing else in the store" a security property rather than tidiness (`requirements.md`, R7, citing L3). Already verified by T22; cited here because T26's sweep must not contradict it. |

R29 is the only requirement the task statement scopes. Its first clause is clause (b) of L16 and has **no
implementation** (Phase 0 §5.1); its last clause — *not in any storage* — is what T26 verifies for the whole
package rather than per item.

## Locked Decisions

| ID | Constraint on this task |
|---|---|
| **L16** | **The only sessionStorage carve-out, and the list is exhaustive**: (a) `state`, `nonce`, `code_verifier` and the post-login return target, single-use and deleted at callback; (b) non-sensitive mid-task form input for R29, with a 15-minute TTL. "Nothing else is stored client-side, and no PII is stored in either case. This list is the entire carve-out." (`design.md:58-60`) |
| **L3** | Access tokens are never written to storage, logs or analytics (`design.md:13`). The sweep must assert the token's absence without re-proving T22. |
| **L17** | The service-worker cache holds the static shell and hashed assets only (`design.md:61-62`, `design.md:194`). This is the one client-side surface L16's absolute sentence does not govern — see Open Question OQ3. |
| **`agents.md:79-81`** | "The only sanctioned client-side storage for sensitive data is sessionStorage, and only for the PKCE and resume state listed in `design.md` L16. **Nothing else is persisted client-side. No PII is ever persisted client-side.**" Authoritative and not to be restated in code. |
| **`design.md` §5** | Per-surface inventory: tokens in memory; **sessionStorage — only the L16 items, nothing else**; localStorage — public preferences (theme, locale) only; service-worker cache — static shell and assets; server state — the query cache (`design.md:191-196`). |
| **T24-D4** (inherited, not a spec LOCKED) | The carve-out is **two constants in two modules** — `AUTHORIZE_STORAGE_KEYS` and `RETURN_TARGET_KEY` — deliberately unmerged, because the three PKCE items are deleted for having been *consumed by the exchange* and the return target for having been *read by the navigation*. T26 must enumerate both rather than merge them. |

## Files involved

**Read / extend (all existing):**

| File | Why |
|---|---|
| `src/auth/authorizeRequest.ts:35-39` | `AUTHORIZE_STORAGE_KEYS` — three of the four keys, exported in T15 explicitly "so task 26's carve-out guard enumerates the same keys". |
| `src/auth/returnTarget.ts:39` | `RETURN_TARGET_KEY` — the fourth. |
| `src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` | T22's sweep: the two-instrument pattern, `STORAGE_ALLOWLIST`, `productionSources()`, the `Storage.prototype` watch, and the controls that prove each instrument. Its header hands T26 "the full L16 inventory and R29's TTL". |
| `src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx:837-854` | T23's claim that the three keys are *the* items `consumeAuthorizeState` deletes — the nearest existing statement of an inventory. |
| `src/auth/CallbackScreen.tsx`, `codeExchange.ts`, `idTokenNonce.ts` | The readers; they must appear in no write claim. |
| `src/app/routes.tsx`, `src/i18n/i18n.ts` | Needed only to drive a real flow in jsdom. |

**New:** `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx` — the named test (`package.md:160`). No file of
that name exists.

**No production file is expected to change.** If Phase 6 finds that it must, that is a finding, not a licence.

## Dependencies

- **Exported constants:** `AUTHORIZE_STORAGE_KEYS` (`{state, nonce, codeVerifier}`), `RETURN_TARGET_KEY`.
- **Exported functions reachable from a flow:** `buildAuthorizeRequest()`, `consumeAuthorizeState()`,
  `rememberReturnTarget()`, `takeReturnTarget()`, `isAllowlistedTarget()`.
- **Test infrastructure:** `vitest` + `jsdom`; `react` `act`; `createRoot`; `createMemoryRouter` /
  `RouterProvider`; `I18nextProvider` + `createI18n()`; `node:fs/promises` for the source scan.
- **Config keys:** none. R29's TTL has no configuration surface because clause (b) is unbuilt.
- **Contracts:** none. T26 makes no backend call; `contracts/api/*` are out of this task's path.
- **Measured environment facts that bound the task:** jsdom provides no `indexedDB`, no `caches`, no
  `cookieStore` (T22, measured) — those surfaces can only be proven structurally.

## Acceptance Criteria

| # | Criterion | Maps to |
|---|---|---|
| AC1 | After a complete sign-in → callback → exchange flow, the set of sessionStorage keys is a subset of the L16 inventory, and the inventory is derived from `AUTHORIZE_STORAGE_KEYS` and `RETURN_TARGET_KEY` rather than from literals. | R29, L16, T24-D4 |
| AC2 | The same holds at **every intermediate state** of the flow, not only at the end: before sign-in (empty), after the authorize step (three items), with a return target remembered (four), after a successful callback (the PKCE three and the target consumed), and after each discard path (the three remain, the target remains). | R29, L16 |
| AC3 | Every production module that calls a sessionStorage **write** method (`setItem`, `removeItem`, `clear`) is one of the two modules the inventory names, and it writes only its own keys. | L16, R29 |
| AC4 | **No key outside the inventory is ever written** by any flow the test drives — asserted on the write path, not only by reading the store afterwards, so a write-then-delete is still a violation. | R29, L16 |
| AC5 | No token, token segment, decoded claim value, password, recovery code, API key or PII value appears as any sessionStorage **value**, nor as any part of a key. | R29, L3 |
| AC6 | `localStorage` holds nothing after any flow the test drives, and no production module writes to it. | R29, `design.md:193` |
| AC7 | No production module names `indexedDB`, `document.cookie`, `cookieStore` or `caches` — proven structurally and **stated as structural**, because jsdom supplies none of them. | R29 |
| AC8 | The inventory is **exhaustive and pinned**: a count assertion fails if a fifth key joins the carve-out without this guard being updated, and the failure message says which key and which module. | L16 ("this list is the entire carve-out") |
| AC9 | Each instrument is proven to report DIRTY before it is believed: a synthetic out-of-inventory write fails the runtime sweep, a synthetic offending source line fails the scan, and a scan over an empty tree is refused. | T22-D14 pattern |
| AC10 | L16 clause (b) is recorded as **permitted but unbuilt**: the guard must not be written so that a future R29 implementation is a spec violation, and the artifact names its unowned named test. | R29, L16(b) |
| AC11 | No PII reaches any storage — distinct from AC5's token claim, because PII would be the *value* of a legitimately-named key. | R29, `agents.md:81` |

## Tests required

**Named test (`package.md:160`):** `sessionStorageHoldsOnlyTheL16Items` → R29. One file,
`src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx`.

**Named test NOT in this task:** `expiredSessionMidTaskPreservesOnlyNonSensitiveInput` (`package.md:153`, also
R29) covers L16 clause (b). The Phase 0 survey found it is claimed by **no task in `tasks.md`**, and T26's
statement scopes only the inventory test. Out of scope here; recorded as a package gap.

**Boundary and implied cases:**
1. **Each flow state** — nothing started; authorize written; target remembered; successful callback; mismatched
   `state`; missing `state`; nonce mismatch; exchange failure; a throwing store. (AC2)
2. **Write-path identity** — the key *and* the value of every `setItem`, in order, over a whole flow, so a
   transient out-of-inventory write is caught even if it is removed again. (AC4)
3. **Alias resistance** — a write through `globalThis['sessionStorage']` and through a captured
   `const s = sessionStorage` must both be seen; the `Storage.prototype` spy makes this true by construction and
   a control must demonstrate it. (AC9)
4. **`localStorage` after every flow**, plus the structural claim that no module calls it. (AC6)
5. **Token-and-secret value sweep** over every stored key and value: whole access token, whole ID token, each of
   its three segments, each decoded claim value, a synthetic `refresh_token`, a password, a recovery code, an API
   key, an email address and an account UUID as PII stand-ins. (AC5, AC11)
6. **Inventory exhaustiveness** — the derived inventory equals exactly four keys; the count is pinned with a
   message naming the carve-out. (AC8)
7. **Negative controls** — an injected out-of-inventory write fails; an injected offending source line fails; an
   empty source tree is refused; a sweep that reads no surface cannot pass. (AC9)
8. **Idempotence / repetition** — two sign-ins in one tab, and a sign-in whose callback is replayed, add no fifth
   key. (AC1, AC8)

## Open Questions

- **OQ1 — Does the guard admit L16 clause (b)?** Clause (b) is permitted by the spec and unbuilt in the code. A
  guard that asserts "exactly these four keys" is correct today and becomes a false failure the moment R29's
  form-input store is built; a guard that leaves room for an unspecified key prefix verifies less. **Not a
  blocker**: the task statement says "contains nothing beyond the L16 list", so the default reading is that the
  guard pins what exists and fails loudly when the carve-out grows, with the clause (b) entrant documented in the
  failure message as the expected next one — the same convention T22's `STORAGE_ALLOWLIST` already uses for a new
  row. Flagged for the Phase 4 gate to confirm, not to block Phases 2-3.
- **OQ2 — Is `expiredSessionMidTaskPreservesOnlyNonSensitiveInput` meant to be T26's second named test?** The
  phase header lists it among the task's named tests, while `tasks.md:38` names only
  `sessionStorageHoldsOnlyTheL16Items`, and no task in the package claims it. The spec files are authoritative
  over the generated header, so the task statement wins and it stays out of scope. **Owner action:** assign
  clause (b) — the store, the TTL and that test — to a task, or record it as deferred. Not a blocker for T26.
- **OQ3 — "Nothing else is stored client-side" versus the service-worker precache.** L16's sentence is absolute;
  `design.md:194` and L17 separately permit a precache of the shell and hashed assets. The reading taken here is
  that §5's per-surface inventory scopes L16's sentence to the key-value stores, with the cache governed by L17 —
  which is also the only reading under which the built SPA is compliant. **Owner action:** confirm. Not a blocker:
  under either reading T26 asserts no API response is cached, which `serviceWorkerNeverCachesApiResponses` (R57)
  already owns.
- **No `package.md` §11 question blocks this task.** Q1–Q12 were checked; none touches R29, L16 or client-side
  storage.

---

**Phase 1 complete.** No design, no implementation.
