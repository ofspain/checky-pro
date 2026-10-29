# frontend · T26 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Claude Opus 5 (packet assembly only; the decision is the author's) |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

Downstream phases may not renegotiate this brief. Everything in Phase 2 stands except where amended below.

---

## Part 1 — Disposition of the Phase 3 challenge

**All eight findings accepted. None rejected.** Every one is a test-plan addition; no production change and no
scope change. Two carry a correction to the finding's own premise, recorded because the premise is what a later
reader would rely on.

### F1 · `.call` / `.apply` / `.bind` / bracket notation — **ACCEPTED**

Verified against source: T22's patterns are `/\bsetItem\s*\(/` and `/\bremoveItem\s*\(/`
(`tokensAreNeverWrittenToAnyStorage.test.tsx:429-433`). In `sessionStorage['setItem'](k, v)` the name is followed
by `']`, in `sessionStorage.setItem.bind(…)` by `.bind`, and in `Storage.prototype.setItem.call(…)` by `.call` —
none matches. **The finding is correct about the scan.**

Worth stating precisely, because it is the whole of T22-D14: **the runtime spy catches all four forms by
construction.** `bind`, `call` and `apply` all invoke the same `Storage.prototype` method the spy replaced, and
bracket notation resolves to it too. So these are forms the *second* net misses and the *primary* instrument
sees — which is the argument for having two nets, and a case that demonstrates exactly that is worth more than
widening the regex silently.

**Amendment.** Required Test 4 gains three synthetic-source rows (`['setItem']`, `.bind`, `.call`), the scan's
patterns widen to catch them, and one case asserts the pair of outcomes: each form fails the scan **and** is
caught by the runtime spy, with the legitimate `authorizeRequest.ts` / `returnTarget.ts` patterns still permitted.

### F2 · The authorization code is missing from the value sweep — **ACCEPTED; premise corrected**

The finding states "T22's leak sweep already asserts it never reaches storage, logs or surfaces." **It does
not.** T22's header excludes the code explicitly — it "is a one-time credential rather than a token, was placed
there by the authorization server rather than by the SPA, and belongs to task 24, which owns the post-login
destination (T22-D3, T22-D9)". The only assertion T22 makes about it is
`expect(swept.routerLocation).not.toContain('code=THE-CODE')` (`:672`), about the router's location, not storage.
T24 then owned the destination and swept the location, not the store.

So **nothing in the package asserts that the authorization code never reaches a client-side store.** The
recommendation is accepted, and it closes a real gap rather than duplicating one.

**Amendment.** AC5 and Required Test 5 gain the authorization code as a swept value.

### F3 · No required test sweeps the inventory under a throwing `getItem` — **ACCEPTED**

The Phase 2 constraint "the guard must distinguish 'the store is empty' from 'the store is unreachable', and must
not pass for the second reason" had no required test of its own. This is the vacuity failure in its most likely
real form: a store that throws on access is a real browser configuration (blocked site data), and a sweep that
treats a throw as "no keys" reports compliance for a store it never read.

**Amendment.** New required test: with a store whose `getItem`, `key` or `length` throws, the inventory sweep
reports **unreachable** and fails; it must not report "subset of inventory". Paired with a control that the same
sweep over a genuinely empty store reports **empty** and passes, so the two outcomes are distinguishable and not
merely both non-fatal.

### F4 · The never-named surfaces may not be exhaustive — **ACCEPTED**

`navigator.storage` (StorageManager / OPFS) is a real persistence surface that AC7's four names do not cover, and
L16's sentence is absolute.

**Amendment.** AC7's list becomes `indexedDB`, `document.cookie`, `cookieStore`, `caches`, `navigator.storage`
and `webkitRequestFileSystem`. The file states that the list is **enumerative, not exhaustive** — a scan over
named surfaces cannot prove the absence of one nobody thought of — and records that a new surface is a design
decision under `design.md` §5, not a test change. Open Question OQ4 carries that to the author.

### F5 · "Names a surface" and "calls a method on it" are different prohibitions — **ACCEPTED**

They are, and conflating them would make the failure message wrong. AC3 is a method-call claim; AC7 is a name
claim. Note that comments are stripped before either scan, so a module explaining a rule it obeys is not the
hazard here — a surface name surviving comment-stripping is a name in live code, which is exactly what AC7 should
flag.

**Amendment.** The two scans report separately and with different messages, and Required Test 7's controls gain a
synthetic file that *names* a forbidden surface without calling it, proving the name scan fires on it.

### F6 · `removeItem` identity by module — **ACCEPTED**

A removal is part of the inventory surface, and a third module could acquire one while the write scan still sees
two.

**Amendment.** AC3 gains "and removes only keys in the inventory", and the two permitted modules are asserted to
be the only callers of `removeItem` on a storage object. `clear()` stays forbidden outright for both — T25
measured that a `clear()` substituted for a targeted `removeItem` wipes the other half of the carve-out, and no
production module calls it today.

### F7 · A write-then-remove control — **ACCEPTED**

AC4 says a write-then-delete is still a violation; nothing proved the assertion survives the removal.

**Amendment.** Required Test 8 gains a synthetic flow that writes an out-of-inventory key and removes it again;
the sweep must fail on the write. This is the control that makes AC4 falsifiable rather than merely stated.

### F8 · Discard → fresh sign-in — **ACCEPTED**

A discard leaves the PKCE items in place (T23-D2) and a fresh sign-in overwrites them. Realistic, and the
transition is where a fifth key would appear.

**Amendment.** Required Test 9 gains discard → fresh sign-in → completed callback, with an inventory assertion
after each step.

## Part 2 — Open Questions: resolved or deferred with an owner

| # | Question | Disposition |
|---|---|---|
| **OQ1** | Does the guard admit L16 clause (b)? | **Resolved for implementation.** The guard pins the four keys that exist and fails loudly when the carve-out grows, naming clause (b) as the expected next entrant — the convention T22's `STORAGE_ALLOWLIST` already uses. The alternative verifies less. |
| **OQ2** | Is `expiredSessionMidTaskPreservesOnlyNonSensitiveInput` T26's second named test? | **Deferred, owner: the author.** The spec files are authoritative over the generated phase header; `tasks.md:38` names only `sessionStorageHoldsOnlyTheL16Items`, so it stays out of T26. Clause (b) — the store, the TTL and that test — is claimed by no task in `tasks.md` and needs either a task or a recorded deferral. **Does not block T26.** |
| **OQ3** | "Nothing else is stored client-side" versus the service-worker precache | **Resolved by reading, owner notified.** `design.md` §5's per-surface inventory scopes L16's sentence to the key-value stores; the cache is governed by L17 and owned by R57's own tests. This is the only reading under which the built SPA is compliant. |
| **OQ4** | Is the never-named surface list closed? *(new, from F4)* | **Deferred, owner: the author.** The scan enumerates six surfaces and says so; a surface nobody thought of is a design decision under `design.md` §5, not a test change. **Does not block T26.** |

## Part 3 — Final scope, files and acceptance criteria

**Scope** — unchanged from Phase 2. In: one new test file holding an inventory derived from the two exported key
constants, swept at every flow state and on the write path, with a source scan and controls that make each
instrument fail. Out: L16 clause (b); re-proving T22, T23, T24/T25; the service-worker cache; the query cache; any
production change.

**Files to Create** — `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx`.

**Files to Modify** — none anticipated. A production change found in Phase 6 is a finding for the Phase 9 gate,
not a licence.

**Files NOT to Modify** — anything under `spec/`; `authorizeRequest.ts`, `returnTarget.ts`, `CallbackScreen.tsx`,
`codeExchange.ts`, `idTokenNonce.ts`, `accessToken.ts`; and the three neighbouring named tests
(`tokensAreNeverWrittenToAnyStorage`, `stateSurvivesRedirectAndIsConsumedOnce`,
`returnTargetOutsideAllowlistFallsBackToHome`) — read as patterns, cited where they already own a claim, never
moved or duplicated.

**Acceptance criteria — final, 12:** AC1–AC11 as written in Phase 2, with these amendments, plus one new:

- **AC3** (amended, F6) — adds: and **removes** only keys in the inventory; the two permitted modules are the
  only callers of `removeItem` on a storage object, and no production module calls `clear()`.
- **AC5** (amended, F2) — adds the **authorization code** to the swept values.
- **AC7** (amended, F4, F5) — six named surfaces, reported as a **name** scan distinct from AC3's method-call
  scan, and stated to be enumerative rather than exhaustive.
- **AC9** (amended, F1, F5, F7) — the controls additionally cover: the three indirect call forms failing the
  scan while the runtime spy catches them; a forbidden surface *named* but not called; and an out-of-inventory
  key written and then removed.
- **AC12** (new, F3) — the sweep distinguishes **empty** from **unreachable**: a store whose access throws fails
  the guard as unreachable, and a genuinely empty store passes as empty. A sweep that cannot tell them apart
  cannot report DIRTY for the case most likely to occur in a real browser.

**Required tests — final:** Phase 2's nine, with F1's three synthetic rows and paired spy assertion added to 4,
F2's code added to 5, F5's name-without-call control and F6's removal-identity assertion added to 7, F7's
write-then-remove control added to 8, F8's discard→sign-in sequence added to 9, and F3's reachability pair as a
new tenth.

**Constraints** — unchanged from Phase 2, with one added by F1: **where the two instruments disagree, the file
says which one caught the case and why**, because a form the scan misses and the spy catches is the argument for
having both (T22-D14), and a reader who cannot see that will eventually delete one of them.

---

**STATUS: FROZEN.** Phase 5 may plan against this brief and no other document.
