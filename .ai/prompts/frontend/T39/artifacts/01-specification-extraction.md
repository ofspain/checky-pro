<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction) -->

# frontend · T39 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## The premise phase 0 flagged — settled by measurement, and it inverts

`signOut.ts:44-48` hands T39 its hazard with a conclusion attached:

> *"Task 39's propagation **narrows the window without closing it**, because a sibling whose renewal
> resolves before it handles the broadcast still posts a success and the channel orders nothing. It is not
> fixable from here … the alternative — a latch that makes `adopt` refuse — means ADDING a piece of state
> in the task whose whole statement is removing it."*

Phase 0 said this had to be settled before anything else, because if it is right then T35's own handoff
message is wrong about its own fix. I spiked it against the real modules rather than reasoning about it.

| Measured | Result |
|---|---|
| **Q1.** Post `renewal:succeeded` from a sibling, then call `signOut()` **synchronously in the same task** — the ordering the note calls unorderable | token is `null` immediately after `signOut()`, and **`"sibling-token"`** once delivery runs |
| **Q2.** Can channel delivery be observed **before** a synchronous `signOut()` in the same task? | **`false`** |

### What that means

Q1 confirms the hazard is **worse** than the note's wording suggests: it reproduces even when the sibling
posts *before* the sign-out, because `BroadcastChannel` delivery is a later task than the synchronous
`signOut()`.

And Q2 is why that same fact **closes** it. The note reasons about when the sibling **posts**; what decides
this tab's token is when this tab **handles**. Those are not the same event, and only the second one is
ordered — deterministically, in this tab's favour:

- delivery **after** `signOut()` — always, per Q2 — so a latch set inside `signOut()` is **already set**
  when any `renewal:succeeded` is handled, and the adopt is refused;
- delivery **before** `signOut()` — not observed, and if it ever were, `signOut()`'s own
  `clearAccessToken()` removes the adopted token anyway.

Either branch ends with no token. **The post order is irrelevant.** So `signOut.ts:44-46`'s conclusion is
wrong, and wrong for a specific reason worth writing down: *the broadcast cannot stop a sibling from
posting, but this tab does not need to stop the post — it only needs to refuse the adopt.*

This is the second inherited comment in two tasks to state a consequence measurement contradicts
(T37's was `routes.tsx:5`). Unlike that one, this is **load-bearing**: it is the premise T35 used to decide
that the hazard could only be pinned and not fixed.

### And the second half of the objection does not apply to this task

> *"a latch … means ADDING a piece of state in the task whose whole statement is removing it."*

True **of task 35**, whose statement is that sign-out removes things. T39's statement is **cross-tab
coherence**, and L18 names it as such: *"Single-flight renewal and cross-tab coherence."* Coherence between
tabs is inherently stateful. The objection was correctly scoped to the task that made it and does not
transfer.

**Conclusion carried to phase 2: T39 can close OQ-T35-3, and therefore T35's handoff instruction — replace
AC9 with "the token stays null" — is the right instruction after all, for a reason its own file argues
against.**

## Requirements

### R22 (`requirements.md:72-75`)

> *IF renewal fails or the session is no longer valid, THEN the SPA SHALL transition to clean re-auth,
> SHALL NOT show a half-authenticated screen, and SHALL show neutral copy. **Sign-out SHALL propagate to
> every open tab.** Refresh-token reuse detection removed (2026-10-10, Q5 resolved)*

| Clause | Owner |
|---|---|
| transition to clean re-auth | **task 33**, `cleanReauth.ts` — closed |
| no half-authenticated screen | **task 33** |
| neutral copy | **tasks 29/31/33** |
| **sign-out propagates to every open tab** | **T39** — the one clause left |

"Propagate" is the whole requirement and the spec says nothing about *how far* it propagates, so phase 2
must define it. What the repository already decides for me: there is no authenticated screen to leave
(T37 measured that no authenticated route has an element), so propagation today means **state**, not
navigation.

### L18 (`design.md:63-64`)

> *At most one renewal runs per tab. Tabs share renewal **and sign-out** through BroadcastChannel or a Web
> Locks leader (R21, R22).*

Two obligations, and the second is T39's. The mechanism is **already chosen and already built** —
`crossTabRenewal.ts` uses `BroadcastChannel` with a Web Locks leader for single-flight. T39 does not get to
pick a mechanism; it has to use that one or justify a second channel beside it.

### Rules T39 must not break

| | |
|---|---|
| **L3** tokens in memory only | a sign-out broadcast must carry **no token** — it is the one message type on this channel that has nothing to say. `renewal:succeeded` carries a token by necessity; sign-out must not. |
| **L16** the sessionStorage carve-out | propagation stores nothing; the carve-out list is closed |
| **R19** | the SAS session is ended **once**, by the tab the user acted in. A receiving tab must sign out **locally** and must not navigate to `/connect/logout` — N tabs hitting the end-session endpoint is N navigations for one intent |
| **R57 / L17** | `/connect/logout` is in the service-worker navigation denylist (task 36); unchanged here |
| **R21 / single-flight** | the renewal channel's existing contract is a closed task's; a new message type on it is a change to `parseOutcome`'s closed union |

## What the suites already say, and what will go red

| Instrument | Expected reaction |
|---|---|
| `signOutClearsMemoryAndEndsSasSession.test.tsx:767` **AC9** | **must go red.** This is the designed handoff, and phase 1's measurement says the replacement assertion ("the token stays null") is achievable rather than aspirational. |
| the same file's **AC9b** (`:839`) | becomes **vacuous** when AC9 is replaced — it scans the hazard case's source for `task 39`, `OQ-T35-3`, `replace this case`, `stays null`. Phase 2 must say what, if anything, replaces a scan whose subject is gone. |
| T36's `SIGN_OUT_SET` derivation | derives the primitives `signOut.ts` calls **from source**. If `signOut()` gains a broadcast, that derivation sees a sixth call and the monopoly case's count assertion fires. This is the instrument T36 deliberately made source-derived; it is working. |
| `siblingTabsShareOneRenewal.test.ts` | owns the channel. A third `Outcome` variant is its business. |
| `tokensAreNeverWrittenToAnyStorage.test.tsx` | a broadcast is not storage, but it **is** a surface, and the no-token rule above is what keeps it clean |

## Open questions for the brief

| | |
|---|---|
| **OQ-T39-1** | **One channel or two.** A third variant on `RENEWAL_CHANNEL` changes a closed task's closed union and makes every renewal listener a sign-out listener; a separate channel is additive but needs its own listener lifecycle, and `shareRenewalAcrossTabs` is where the only existing listener lives. |
| **OQ-T39-2** | **Does `signOut()` broadcast, or does a wrapper?** `signOut()` is deliberately zero-argument and reaches no closure. A wrapper keeps T35's function exactly as pinned; putting the broadcast inside it trips T36's source-derived `SIGN_OUT_SET`. |
| **OQ-T39-3** | **Where the latch lives.** `api/` imports nothing from `auth/` today, and `auth/accessToken.ts` imports a **type** from `api/crossTabRenewal` (erased at compile). A latch in `api/` keeps the existing direction; a latch in `auth/` inverts the layering. T36's precedent is that a new owner module **is** the pattern. |
| **OQ-T39-4** | **How a receiving tab avoids echoing.** A tab that handles a sign-out by calling `signOut()` must not re-broadcast, or two tabs ping-pong. |
| **OQ-T39-5** | **The latch must reset on sign-in**, or a tab that signs out and back in can never adopt a sibling renewal again. That reset opens a *different* hazard, below. |
| **OQ-T39-6** | **NEW, found by this phase: a stale adopt after re-sign-in.** With the latch reset by a fresh sign-in, a `renewal:succeeded` from a sibling's *pre-sign-out* renewal could arrive afterwards and overwrite the new token with an older one. This is token **freshness**, not sign-out, and it may be unreachable today — `accessToken.renew()` rejects with `RENEWAL_NOT_BUILT` and **no task in `tasks.md` builds renewal**, so no genuine `renewal:succeeded` can be produced in production. Phase 2 must decide whether T39 closes it, names it, or declares it unreachable — and must not do so by assumption. |

## Phase 2

The task implementation brief.
