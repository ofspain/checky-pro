<!-- MODEL: Claude Opus 5 — Phase 4 (Freeze Task) -->

# frontend · T35 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` (Kimi 2.7) |
| **Produces** | `artifacts/04-freeze-task.md` — **the only specification phases 5–13 use** |

Twelve findings. **Every citation was checked against source before being accepted or rejected**, and two of
them changed the task materially: F2's scan was already built by another task, and F5 is a real defect in
built code.

## Resolutions

### F5 — ACCEPTED, CONFIRMED, and the most serious finding. A signed-out tab can be re-authenticated by a sibling.

Verified at `api/crossTabRenewal.ts:95-111`: `onMessage` calls `local.adopt(outcome.token)` on any
`renewal:succeeded` message, **with no check that this tab has signed out**. The sequence:

1. Tab A signs out. Its token is `null`.
2. Tab B, which has not processed any sign-out, completes a renewal and posts `renewal:succeeded`.
3. Tab A's listener adopts the token. **Tab A holds a live session again, with no visitor action.**

Task 39's propagation reduces this but cannot close it: a sibling whose renewal resolves before it processes
the sign-out broadcast still posts a success, and the channel has no ordering guarantee.

**T35 cannot fix it, and will not pretend to.** `signOut()` takes no parameter and so cannot reach the
`SharedRenewal` closure; the guard would have to live in `accessToken.ts` or `crossTabRenewal.ts`, both
closed, and would mean **adding** a piece of state in the task whose statement is about removing it. What
T35 does instead, which is the T33 AC11 pattern: **AC9 pins the hazard** with a case that drives the real
channel, demonstrates the exact three-step sequence, asserts the current behaviour, and carries a failure
message naming task 39 as the owner. Escalated as **OQ-T35-3**.

### F2 — ACCEPTED and redesigned, because the scan I specified already exists and belongs to another task.

`src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx` (task 22) **already owns L16's inventory**: it derives
the four keys from `AUTHORIZE_STORAGE_KEYS` and `RETURN_TARGET_KEY` (`:736-742`), pins the carve-out at four
with a message naming clause (b) as the expected next entrant (`:750-755`), spells no key literal of its own
(`:764-771`), and maintains a `PERMITTED_WRITERS` table of which module may call which storage method on
which key (`:600-617`). **T35 must not become a second opinion on it** — that is OQ-T32-4's debt exactly.

AC7 is therefore narrowed to the question T35 actually owns: *of the state that exists, does sign-out clear
each piece?* Two concrete instruments replace the open-ended scan:

- **(a) A derived inventory table** in the test, each row naming a piece of state, the module that owns it
  and the primitive that clears it, with the keys built from the imported production constants so no literal
  appears in the file.
- **(b) A `themistra.`-literal scan** over production source: every such literal must be either in the
  derived inventory or in a declared non-storage exclusion list, with a justification per entry. The two
  known non-storage entries are `RENEWAL_CHANNEL` and `RENEWAL_LOCK` (`api/crossTabRenewal.ts:18-19`). This
  catches a fifth key introduced in a new module, which a constants-only derivation cannot see.

### F3 — ACCEPTED. `takeReturnTarget()` is the wrong primitive, and the additive export is safe.

Confirmed at `returnTarget.ts:120-139`: it returns a destination with a home fallback, and its own header
records T24-D4 — the return target is "deleted at callback, **by the module that owns its reader**", for
having been read by the navigation, as against the PKCE items deleted for having been consumed by the
exchange: "Different consumers, different moments." Sign-out is a third moment and reads nothing.

**`clearReturnTarget()` is added to `returnTarget.ts`**, and the brief's "Files NOT to Modify" is relaxed for
that one additive export. Checked, not assumed, that this is safe:

- T22's `PERMITTED_WRITERS` (`:609-616`) already permits `auth/returnTarget.ts` to call `removeItem` on
  `RETURN_TARGET_KEY`, so the new function is a permitted writer in a permitted module on a permitted key.
- T22's "never calls `clear()`" case is unaffected — this is a `removeItem`.
- T24's only source scan of the file asserts no module-scope use of the route table
  (`returnTargetOutsideAllowlistFallsBackToHome.test.tsx:854`), which a guarded removal does not add.

`takeReturnTarget`'s body is **not** refactored to delegate to it. Two three-line removals in one module is
the cost, taken deliberately: changing a closed function's body to save three lines is the worse trade.

### F4 — ACCEPTED, with the distinction that makes it right, and replaced with an instrument that has teeth.

F4 is correct and the reason is worth stating: T33's AC12 failed on wiring because the author **needed an
instruction** — pass `createRenewalFailureHandler` as `onRenewalFailed` — so the failure carried
information. Wiring T35 requires nothing but calling `signOut()`, so the same test here would be pure
obstruction. No dormancy canary.

**AC10 replaces it with a primitive-caller allowlist**, measured from source rather than imagined:

| Primitive | Permitted production callers today |
|---|---|
| `clearAccessToken` | `auth/cleanReauth.ts:74`, `auth/CallbackScreen.tsx:94` |
| `takeSignInNotice` | `auth/SignInScreen.tsx:24` |
| `consumeAuthorizeState` | `auth/CallbackScreen.tsx:181` |
| `takeReturnTarget` | `auth/CallbackScreen.tsx:194` |
| `clearReturnTarget` | *(new)* `auth/signOut.ts` only |

A caller anywhere else fails, with a message saying: if you are building sign-out, call `signOut()`; if this
is a new legitimate consumer, add it here with a reason. This never obstructs the wiring and it stops
someone hand-rolling a partial sign-out beside the real one — which is the failure mode that actually
threatens R19.

### F6 — ACCEPTED. AC6's count was wrong and would have failed against correct code.

Measured: `consumeAuthorizeState` performs three `removeItem` calls (`authorizeRequest.ts:121-130`);
`clearReturnTarget` performs one; `clearAccessToken` and `takeSignInNotice` touch no store. So the total is
**four removals and no read** — and the absence of the read is a second reason F3's fix is right, since
`takeReturnTarget` would have added a `getItem`. AC6 is rewritten to enumerate rather than count.

### F8 — ACCEPTED. AC7 promised more than a scan can deliver.

AC7 is narrowed to **module-scope declarations** (`/^let /`, `/^var /`) over production source, with a
justified exclusion list: `registerServiceWorker.ts:37,39` (`registering`, `updateIsWaiting`) are PWA
registration state, not session state, and a sign-out that dropped them would re-register a worker. The
limitation is stated plainly rather than papered over: **closure-held state is out of reach** —
`crossTabRenewal.ts:90-93` and `sessionBoundary.ts`'s pending map live in factory closures — and that
limitation *is* F5's hazard, which AC9 pins on its own.

### F7 — ACCEPTED in substance, with the ordering corrected.

L16 clause (b)'s mid-task form input **does not exist**: T22 pins the carve-out at four and its message names
`expiredSessionMidTaskPreservesOnlyNonSensitiveInput` as a test no task claims. T35 derives from production
constants, so a key that does not exist cannot break it, and when clause (b) arrives **T22's count pin fails
first**, which is the right order — T22 owns that spec event. Clause (b) is pre-authorized in T35's exclusion
list with the handoff recorded explicitly: **whether a deliberate sign-out should destroy a visitor's
in-progress form input is task 26's decision, not T35's to presume.**

### F1 — ACCEPTED as reading (c). Recorded, not papered over.

AC1–AC8 are verified by direct invocation in tests and cannot be exercised end-to-end until a caller exists.
No task in `tasks.md` owns a sign-out affordance; no authenticated route has an element; `AppShell.tsx`
renders a bare `<div>`. T35 is the **sixth** built-and-dormant task after 5, 6, 30, 31 and 33, and tasks
36–39 all land in the same place. Reading (b) — building a caller — is declined: it would mean T35 inventing
an affordance, its placement and its copy, which are tasks 173/174's, and L11 forbids inventing the copy.
**OQ-T35-2**, same substance as OQ-T33-2, now raised a sixth time.

### F9 — remedy ACCEPTED, rename REJECTED.

`tasks.md:63` and `:64` both name `signOutClearsMemoryAndEndsSasSession`, so a T35-only name would be a name
no spec line contains and would force task 36 to rename the file. The file keeps the spec's name, carries a
prominent header stating T35 owns the "clears memory" half only, and its describe blocks are arranged by
criterion so task 36 appends rather than restructures.

### F10 — risk ACCEPTED, remedy relocated.

`authorizeRequest.ts` is not edited — a comment edit is still an edit to a file this brief protects, and
tasks 15, 22 and 23 are built on it. The reason for the call goes in `signOut.ts`, beside the call, which is
where a reader of sign-out looks. AC10's allowlist is the structural half of the same protection: a
maintainer who refactors `consumeAuthorizeState` on the assumption of one caller has to edit that table.

### F11 — ACCEPTED as an explicit scope note.

T35 does not abort in-flight requests. Worth stating precisely: `api/sessionBoundary.ts` reads `current()`
when each request is built and never caches it, so **every request built after sign-out carries no token**;
only a request already dispatched still carries the old one, and it will outlive the sign-out by its own
round trip. Aborting those is unowned by any task.

### F12 — ACCEPTED as documentation.

The file is `.tsx` because task 36 extends it with navigation cases, and renaming a test file later is churn
for no gain. T35's own cases use no JSX. One line in the header says so.

## Frozen brief

**Task.** Build the clearing half of sign-out: one synchronous function that leaves the tab holding no
session state. R19 clause 1. The end-session navigation is task 36.

**Scope — in:** the function; idempotence; the derived inventory and its two scans; the primitive-caller
allowlist; the pinned cross-tab hazard. **Out:** the end-session navigation, the `id_token_hint`, the
`post_logout_redirect_uri` (task 36) · broadcasting to siblings (task 39) · the `/app/signed-out` screen and
its copy (task 37) · any affordance (unowned) · retaining an `id_token` (task 36) · aborting in-flight
requests (unowned) · L16 clause (b) (task 26).

**Business rules.** R19 cl. 1 *(this task)* · R19 cl. 2 *(task 36; constrains ordering only)* · R22 *(task
39; requires idempotence and callability without a gesture)* · R57 *(built; makes task 36's step a full-page
navigation, so this clearing must be synchronous and complete on return)*.

**Locked decisions.** L3 · L16 · L18 · L4 · L11 · L14 · L12.

**Dependencies.** `clearAccessToken` (`auth/accessToken.ts:97`) · `takeSignInNotice`
(`auth/signInNotice.ts:41`) · `consumeAuthorizeState` (`auth/authorizeRequest.ts:120`) ·
`clearReturnTarget` (**new**, `auth/returnTarget.ts`). No network call, no contract, no config key.

**Inputs:** none, no parameter. **Outputs:** `void`. **State changes:** the held token → `null`; the pending
notice → `null`; four sessionStorage keys removed; nothing else.

**Files to create.** `frontend/src/auth/signOut.ts` ·
`frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx`.

**Files to modify.** `frontend/src/auth/returnTarget.ts` — **one additive export only**,
`clearReturnTarget()`. Nothing else in it changes.

**Files NOT to modify.** `auth/accessToken.ts` · `auth/signInNotice.ts` · `auth/authorizeRequest.ts` ·
`api/crossTabRenewal.ts` · `api/sessionBoundary.ts` · `app/routes.tsx` · `i18n/catalogue.ts` · every closed
suite · anything under `spec/`. If the work appears to need one, **stop and log it**.

**Acceptance criteria.**

| # | Criterion |
|---|---|
| **AC1** | After sign-out, `accessToken.current()` is `null`. |
| **AC2** | Idempotent: twice in a row, and once from a cold tab that never signed in, both leave the same state and neither throws. |
| **AC3** | No sign-in residue survives — no pending notice, no remembered return target, no authorize triple. |
| **AC4** | Synchronous and complete on return: no `async`, no promise, no timer, nothing deferred. |
| **AC5** | Takes no parameter and returns nothing. |
| **AC6** | The only storage operations are the four removals its primitives perform — three by `consumeAuthorizeState`, one by `clearReturnTarget` — with no read, no `clear()`, no write, and no other surface or console channel touched. |
| **AC7** | Every module-scope mutable declaration in production source is either cleared by sign-out or on a justified exclusion list. The list holds `registerServiceWorker.ts`'s two PWA entries and nothing else. The closure-state limitation is stated in the file. |
| **AC8** | The derived inventory names each piece of state, its owner and its clearing primitive, spells no key literal of its own, and every `themistra.` literal in production source is in it or in the declared non-storage exclusion list. |
| **AC9** | The cross-tab hazard is pinned: a tab that signs out and then receives `renewal:succeeded` on the real channel is shown to adopt the token, with a message naming task 39 as the owner. |
| **AC10** | No production module calls any of the five clearing primitives outside the measured allowlist. |
| **AC11** | T35 adds no route, no catalogue key, no network call, no new storage key, and modifies no file but the two it creates and the one additive export. |
| **AC12** | Never throws, for any storage condition, including a store that throws on every operation — and the token is cleared even then. |

**Required tests.** `signOutClearsMemoryAndEndsSasSession`, in the file named above, arranged by criterion so
task 36 appends. At least one case per criterion, plus: sign-out with a token held and a mid-flight authorize
triple; sign-out from a cold tab; sign-out in a tab that adopted a sibling's token; sign-out with
`sessionStorage` throwing on every operation; and a **control** for each scan and sweep, shown firing on a
planted positive, so a clean report is evidence and not a silent pass.

**Gates.** T35 file green · whole suite no worse than the recorded baseline (**1574 passed / 1 failed of
1575**, the failure being this host's `oxlint` environmental case) · `tsc --noEmit` exit 0 · `oxlint src`
exit 0 · a mutation harness whose canaries are scored apart from regressions, that asserts its own baseline,
and that records each mutant's failure count.

**Constraints.** No token, account identifier, key value or failure detail on any log, console channel,
analytics call or error message (L3, L4) · clear only through the owning primitive, never a direct
`sessionStorage` call or module-variable assignment · no module-level mutable state in the new file ·
synchronous throughout · never throws · no thread-safety or transaction claim, because `sessionStorage`
offers no transaction and saying otherwise promises what the platform does not.

## Open Questions

**OQ-T35-3 — NEW, Blocker, the owner's. A signed-out tab can be re-authenticated by a sibling.** Confirmed in
built code at `crossTabRenewal.ts:95-111`. Task 39's propagation narrows the window but cannot close it.
T35 pins it (AC9) and cannot fix it. The fix belongs where the adoption happens, and it is a decision about
closed modules: either `adopt` refuses after a sign-out — which means a latch, i.e. new state — or
`SharedRenewal.close()` is called on sign-out, which means sign-out must reach the instance and therefore
take a dependency it currently has no way to receive.

**OQ-T35-2 — the owner's, sixth time.** Nothing offers sign-out and nothing can. T35 joins tasks 5, 6, 30, 31
and 33; tasks 36–39 will follow. Same substance as OQ-T33-2.

**OQ-T35-1 — frozen, not blocking.** Task 36 retains the `id_token`, not task 35. Task 36 should settle
whether the hint is needed at all by reading `SasLoginIntegrationTest.endSession*`, which `design.md:168-169`
names as the proof end-session works.

**Recorded drift, not fixed:** `package.md:123` still names the test
`signOutRevokesTokensClearsMemoryAndEndsSasSession` although Q5 removed the revoke call from R19 on
2026-10-10. Third instance of the same `package.md` lag.

**Phase 4 complete — brief FROZEN.**
