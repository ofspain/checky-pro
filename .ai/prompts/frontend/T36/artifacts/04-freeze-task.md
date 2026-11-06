<!-- MODEL: Claude Opus 5 — Phase 4 (Human Approval / Frozen Task Brief) -->

# frontend · T36 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-freeze-task.md` |

Fifteen findings. **Eleven accepted** (one of them reversing a frozen decision), **two rejected on
measurement**, **one answered by measurement**, **one no-action**. Every citation was checked against source
before it was accepted or rejected; where a check changed the answer, the check is shown.

---

## Resolutions

### F1 — ACCEPTED, and it reverses OQ-T36-1. The `id_token` gets its own module.

My phase-2 decision put the `id_token` in `auth/accessToken.ts` and leaned on a reading of that module's own
comment: that "do not move it into storage, a store, a React context or a second module" is an argument
against a second *location* for the access token, not against the module holding a second value. That reading
is still correct as far as it goes, and it is not the reason the decision was wrong.

The decision was wrong because of what **T35 established**. T35's design is one sentence: *every piece of
session state has a module that owns it and a primitive that clears it, and sign-out composes those
primitives.* `signOut()` asks four modules for four primitives. It does not reach into any of them. Under that
design, a new piece of session state getting a new owning module is **the pattern**, and folding it into an
existing owner is the exception that needs the argument. I had the burden of proof backwards.

Two further things the phase-2 note did not weigh:

- `accessToken.ts:45` exports `accessToken: LocalSession`, a shape chosen by tasks 5 and 6 and consumed by
  built code. The module says so at `:18-20`: "The shape is not this module's to choose." A second token in
  that file invites the next author to widen that shape, which is the one thing it says not to do.
- `accessToken.ts:97`'s `clearAccessToken` is documented as clearing *the token*. A version that cleared two
  tokens would make every existing caller — `cleanReauth.ts`, `CallbackScreen.tsx`'s discard — clear an
  `id_token` as a side effect none of them asked for. That is a behaviour change inside a closed task,
  which is strictly worse than a new file.

**Frozen: `frontend/src/auth/idToken.ts`.** `accessToken.ts` is returned to the "NOT to Modify" list.

Kimi's alternative — renaming to `sessionTokens.ts` — is **rejected**: a rename touches every importer, and
there are several.

### F2 — ACCEPTED. `takeIdToken()`, and the new module exports no plain reader.

Kimi's diagnosis of the temporal coupling is right, and the remedy is better than the one it proposes,
because the package already has the shape: `takeSignInNotice()` and `takeReturnTarget()` both read-and-clear
in one call, and T35's `signOut()` already calls the first of those and discards the value.

So `auth/idToken.ts` exports exactly two functions:

- `retainIdToken(token: string): void`
- `takeIdToken(): string | null` — returns what was held and clears it, in one operation

and **no `currentIdToken()`**. That omission is load-bearing: with no plain reader, there is no way to obtain
the `id_token` without consuming it, so the read-before-clear window F2 describes cannot be opened by a
future author. AC5 gains a direct instrument, as Kimi says, and AC8's sweep gains a much stronger one — a
module that wanted to log the token would have to clear it to get it.

This makes `signOut()`'s fifth call `takeIdToken()`, value discarded, exactly as it already treats
`takeSignInNotice()`. `endSession()` calls `takeIdToken()` first for its own use; `signOut()`'s later call
then returns `null` and clears nothing. That is not redundancy — it is what guarantees the `id_token` is gone
whether sign-out was entered through `endSession()` or through `signOut()` alone. Both call the same
idempotent primitive. The module comment says this.

### F3 — ACCEPTED. AC8 as written contradicted AC5, and would have failed against correct code.

Confirmed by reading: AC8 said "reaches no storage surface, no console channel and no analytics call" while
AC5 requires it in the URL. A sweep written from AC8's wording would flag the one place the token is supposed
to be. AC8 is rewritten to Kimi's shape, and gains the stronger assertion it suggests: the token appears
**exactly once** across the whole hand-over, and that once is the `id_token_hint` parameter.

### F4 — ACCEPTED, with the half the finding dropped.

Confirmed: the SPA has no access to the backend's registered allowlist at runtime, so AC4's "equal to the
registered allowlisted value" named a check that cannot be performed here. Reworded to the shape claim.

But Kimi's "drop the claim, or make it a backend integration test concern" gives away something real. The
claim is not dropped — it is **split across the two places that can each hold half of it**:

- the SPA pins that its derived value ends in `/app/signed-out` and is built from `location.origin` plus the
  de-slashed basename plus the route constant;
- the backend already pins that the registered default is reached and that a non-allowlisted target is
  refused with `400` (`SasLoginIntegrationTest:475`, `:502`).

Neither half is a runtime check of the other, and the artifact says so rather than implying a verification
that exists. What the suite can do is fail if the SPA's half drifts, and that is what it will do.

### F5 — REJECTED on measurement. The four-mechanism count stays.

Kimi's premise is that "in jsdom, intercepting the `href` setter on `location` is not as straightforward as
stubbing `assign`/`replace`" and that the harness could therefore be buggy. **This package already does it,
and has since task 16.** `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx:277-300`'s
`watchHandover` counts all four mechanisms; the technique (jsdom's `location.href` descriptor is
`configurable: false`, so the whole `location` object is replaced with `vi.stubGlobal`) is measured, written
down and in use. T36 reuses it rather than inventing it.

The second half of the finding — "`window.open` is not a realistic hand-over mechanism for this flow" — is
**the reason to watch it**. T16's own header records the hole: the failure this counter exists to catch is an
*addition*, a second navigation fired through `href` or `window.open` **alongside** the existing `assign`,
which leaves an `assign`-only spy reporting exactly one call while the SPA has grown a second hand-over. A
watcher narrowed to the mechanism the module uses today cannot see a mechanism added tomorrow. An
unrealistic second hand-over is precisely the one that would be missed.

Kimi's third suggestion is already in the brief: a source scan forbidding the others, and a control proving
the watcher catches a second call. Both kept.

### F6 — ACCEPTED as reading (a). My security argument was overstated.

Confirmed. Phase 2 said a `state` that is sent and never verified "is theatre" and leaned on L16 to rule out
storing one. Kimi is right that this overstates: an in-memory nonce needs no storage key, so L16 does not
forbid it, and the honest reason to omit `state` is simpler.

**Frozen: no `state`, justified as reading (a).** SAS does not require it; the **allowlisted
`post_logout_redirect_uri` is the security control** — the backend refuses anything outside the allowlist with
a `400` (`SasLoginIntegrationTest:502`), which is what stops a logout being redirected somewhere hostile, and
a `state` parameter adds nothing to that. What `state` would buy is *correlation*, and nothing needs it:
`/app/signed-out` is a public route rendering neutral copy with no action to authorize, so there is no
decision on that screen that knowing "this logout came from this tab" would change. If task 37 ever gives
that screen something to decide, it can add `state` then, with a reason.

Kimi's (b) — an in-memory nonce verified by task 37 — is rejected for that reason and one more: an in-memory
nonce cannot survive the full-page navigation to SAS and back, so verifying it would require the storage the
finding says it avoids.

### F7 — ACCEPTED, and the scan's allowlist is two files, measured.

Confirmed that AC10 named a rule with no instrument. The scan is added. Measured what it must permit:
`/connect/logout` appears in production today at exactly one place — `pwa/navigationDenylist.ts:19`, where it
is a denylist entry (R57) and not a URL anyone navigates to. After T36 there are **exactly two** production
files, each with a different reason, and the scan pins both by name with those reasons. A third fails.

Test files are excluded, as Kimi says; four of them mention the path.

### F8 — ANSWERED BY MEASUREMENT. No T19/T20 case needs updating.

The finding asks which tests need to change. I measured instead of guessing:

- `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` reads `CallbackScreen.tsx` in exactly one
  assertion, `:1019` "holds the token only AFTER the nonce check, never before it (AC14)". It takes the slice
  between `idTokenNonceMatchesStoredValue(response.idToken)` and `retainAccessToken(response.accessToken)`
  and requires `return;` in it. A `retainIdToken(response.idToken);` line added beside `retainAccessToken`
  leaves that slice containing the early return either way, so the case passes.
- `stateSurvivesRedirectAndIsConsumedOnce.test.tsx:880` counts `consumeAuthorizeState()` in that file at
  exactly one. T36 adds no such call.
- No suite counts imports or statements in `CallbackScreen.tsx`.

So the edit is additive and breaks nothing — which phase 6 will confirm by running, not by this reasoning.

Kimi's alternative (move the retention into a module imported by `CallbackScreen.tsx`) is **rejected**: it is
still a new import and a new call in the closed file, so it buys nothing, and it adds a module whose only job
is to forward one argument.

What the finding is right about, and does not say, is covered by **F11**: nothing pins the *new* line's
position. See below.

### F9 — ACCEPTED, verbatim.

T36's additions to T35's AC7 logic are marked with a comment naming this task, and T35's original cases are
left as they are. The same marking is applied to every other T35 structure T36 extends — `INVENTORY`,
`PERMITTED_CALLERS`, `DEFINERS` and the `SIGN_OUT_SET` monopoly case — so a reader can tell T35's intent from
T36's extension of it without consulting git.

### F10 — ACCEPTED as recorded, for the seventh task running.

Nothing in the SPA can trigger sign-out. T36 cannot resolve this and does not pretend to: **AC1–AC14 are
verified only by direct invocation.** Recorded as **OQ-T36-5**, unchanged and unsoftened. No task in
`tasks.md` owns the affordance.

### F11 — ACCEPTED, and it is the real content of F8.

The placement comment at `CallbackScreen.tsx:164-168` explains why retention sits on the far side of the
nonce check, and T19's `:1019` enforces that for the *access* token. Nothing would enforce it for the
`id_token`. A `retainIdToken` hoisted above the check would hold an identity assertion minted for a login this
tab did not start — the same defect T19 exists to stop, and invisible afterwards.

T36 adds the source-order scan, **in T36's own suite** rather than by editing T19's, with a control proving the
scan fires on a planted hoist.

### F12 — ACCEPTED as a pinned behaviour, not as a latch.

The brief was silent. Measured answer: a second `endSession()` clears nothing further (every primitive is
idempotent), builds a URL with **no** `id_token_hint` (the first call consumed the token), and performs a
second hand-over. In a real browser the first navigation has already left the page, so this is unreachable.

A latch would make it idempotent, and is **rejected**: a latch is module-level mutable state, which this
task's own constraint forbids, and it would trade an unreachable second navigation for a permanent piece of
state that nothing clears. Instead the real behaviour is pinned by a case, so a future author who needs
idempotence sees what they are changing. This is how T35 handled the same shape of finding (its I9).

### F13 — no action, as the finding itself concludes.

### F14 — REJECTED on measurement. Both scans are scoped to their own module.

The finding's premise is "the T33/T35 scans scan all production files; they may flag `endSession.ts`". Checked
both:

- T33's is `failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx:641`, reading
  `strip(await moduleSource())`, and `moduleSource()` at `:58` reads `cleanReauth.ts` alone.
- T35's are `signOutClearsMemoryAndEndsSasSession.test.tsx:879-894`, every one of them against `self!.code`,
  which is `signOut.ts` alone.

Neither sweeps `src/`. No exclusion is needed and none is added — an exclusion for a scan that would never
have fired is a comment that misleads the next reader about what the scan does.

**But the finding points at something real that it does not state.** T35's AC11 scan at `:888` forbids
`/navigate/` in `signOut.ts`. That is not a false alarm to be excused — it is a **binding design constraint**:
the hand-over **cannot** live in `signOut.ts`, and must live in `endSession.ts`, which is what the brief
already planned for other reasons. The constraint is now written down as the reason, so a future author who
tries to collapse the two modules finds out why they cannot. This is a better finding than F14, and F14 is
what produced it.

### F15 — ACCEPTED in part. One constant, published from `endSession.ts`, and no new dependency.

Confirmed the second spelling: `pwa/navigationDenylist.ts:19`. `END_SESSION_PATH = '/connect/logout'` is
published from `endSession.ts`.

Kimi's "reference it in both" is **rejected as written**: having `pwa/navigationDenylist.ts` import from
`auth/` would create a dependency from the service-worker layer to the auth layer, for a string, and that
module's list is deliberately a literal list of paths pinned by its own tests (T10). Instead **T36's test**
asserts the denylist contains `END_SESSION_PATH`. The two spellings are then held equal by a test rather than
by a coupling, which is the cheaper of the two and the one that does not edit a closed module.

---

## Frozen brief

Phase 2's brief stands except as amended here.

### Files to Create

- `frontend/src/auth/idToken.ts` — **new, per F1.** `retainIdToken`, `takeIdToken`. No plain reader (F2).
- `frontend/src/auth/endSession.ts` — builds the URL and performs the hand-over. Publishes
  `END_SESSION_PATH` (F15).
- No new test file. T36 appends to `auth/signOutClearsMemoryAndEndsSasSession.test.tsx`.

### Files to Modify

| File | Change | Note |
|---|---|---|
| `auth/signOut.ts` | one added call, `takeIdToken()`, value discarded | second, after `clearAccessToken()`. T35's AC1 pins `clearAccessToken` as the first statement; a credential-clearing call is right behind it, and the three untidy-store calls follow |
| `auth/CallbackScreen.tsx` | one line, `retainIdToken(response.idToken)`, beside `retainAccessToken` | breaks no existing case (F8, measured); its position is pinned by a new T36 scan (F11) |
| `auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | T36's criteria appended; T35's `INVENTORY`, `PERMITTED_CALLERS`, `DEFINERS`, `SIGN_OUT_SET` and AC7 extended, each marked with a T36 comment (F9) | |

### Files NOT to Modify

**`auth/accessToken.ts` — returned to this list by F1.** · `pwa/*` · `app/routes.tsx` · `i18n/catalogue.ts` ·
`api/*` · `auth/returnTarget.ts` · `auth/authorizeRequest.ts` · `auth/signInNotice.ts` · every other closed
suite · anything under `spec/`.

### Acceptance Criteria — frozen

| # | Criterion |
|---|---|
| **AC1** | `signOut()` completes **before** the hand-over, so no token is held when the browser leaves. |
| **AC2** | Exactly **one** hand-over, counted across `assign`, `replace`, the `href` setter and `window.open` (F5), with a control proving a watcher installed after the call sees nothing, and a control proving the watcher catches a second call. |
| **AC3** | The URL is `END_SESSION_PATH` on the SPA's own origin, derived from `location.origin`; no absolute host is spelled anywhere in production source. |
| **AC4** | `post_logout_redirect_uri` is `${origin}${de-slashed basename}/signed-out`, derived from the route table, **matching the shape the backend registers** (F4). A trailing slash on `APP_BASENAME` cannot produce `/app//signed-out`. The backend's half is `SasLoginIntegrationTest:475`/`:502`, named but not re-asserted here. |
| **AC5** | `id_token_hint` carries the value `takeIdToken()` returns, and that is the only reason the token is retained. |
| **AC6** | The parameter set is **exactly** `id_token_hint` and `post_logout_redirect_uri` — no access token, no account identifier, no scope, no `state` (F6). |
| **AC7** | A full-page navigation: no `react-router` import, no `history` use, no router navigation. |
| **AC8** | The `id_token` appears **exactly once** in the whole hand-over, and that once is the `id_token_hint` query parameter. It reaches no storage surface, no console channel and no analytics call (F3). |
| **AC9** | **Sign-out clears the `id_token`.** T35's inventory gains a row, and T35's AC7 is strengthened to pin declaration counts for *cleared* files as well as excused ones — today it counts only the excused, so a second mutable added to a cleared file and left uncleared would pass. T36 is the first task to add one, in `idToken.ts`. |
| **AC10** | Only sign-out ends the SAS session. A source scan: `END_SESSION_PATH`'s literal appears in **exactly two** production files — `endSession.ts` (the navigation) and `pwa/navigationDenylist.ts` (the R57 denylist entry) — each named with its reason, and a third fails (F7). |
| **AC11** | T36 adds no route, no catalogue key, no backend call and no storage key. |
| **AC12** | A tab holding **no** `id_token` still clears, and still navigates with the hint omitted rather than skipping the hand-over. |
| **AC13** | A second `endSession()` clears nothing further, omits the hint, and hands over again — pinned as the real behaviour, with no latch (F12). |
| **AC14** | `retainIdToken` appears **after** the nonce check in `CallbackScreen.tsx`, with the early return between them; control included (F11). |
| **AC15** | `auth/idToken.ts` exports no plain reader, so the token cannot be read without being consumed (F2). `auth/endSession.ts` holds no module-level mutable state. |
| **AC16** | `pwa/navigationDenylist.ts`'s list contains `END_SESSION_PATH`, so the two spellings cannot drift (F15). |

### Constraints — amended

Phase 2's constraints stand, with one restated as binding rather than advisory:

- **The hand-over cannot live in `signOut.ts`.** T35's AC11 scan
  (`signOutClearsMemoryAndEndsSasSession.test.tsx:888`) forbids `navigate` in that file. This is not an
  obstacle to excuse but the reason `endSession.ts` exists. Both modules say so (F14).
- **Full-page navigation is correct here** and is the opposite of T33's and T35's constraint, which forbid
  `globalThis.location` **in their own modules only** — measured, not assumed (F14). `endSession.ts` says this
  out loud so a reader who knows the other two rules does not read it as a violation.

---

## Open Questions

**OQ-T36-1 — REVERSED AND RE-FROZEN.** The `id_token` lives in **`auth/idToken.ts`**, its own module, with
`retainIdToken`/`takeIdToken` and no plain reader. Phase 2 put it in `accessToken.ts`; F1 is right that this
had the burden of proof backwards, and the reversal keeps a closed module closed.

**OQ-T36-2 — FROZEN, re-argued.** No `state`. The allowlisted `post_logout_redirect_uri` is the security
control, not `state`; correlation is the only thing `state` would buy and no screen needs it (F6).

**OQ-T36-3 — FROZEN, measured.** AC12's no-`id_token` tab is unreachable in production today (`renew()`
always rejects; `adopt` fires only from `crossTabRenewal.ts:103`). The behaviour is frozen as *navigate with
the hint omitted*, because skipping the hand-over would leave the SAS cookie alive and sign a visitor back in
by the act of visiting.

**OQ-T36-4 — open, the owner's.** Whether SAS 1.5.1 honours a `post_logout_redirect_uri` without an
`id_token_hint`. Both backend tests supply one (`SasLoginIntegrationTest:487,512`), so the green suite does
not answer it. It does not block T36; answering it is what would let a future task stop retaining the
`id_token` at all, and it needs a backend measurement.

**OQ-T36-5 — the owner's, seventh time.** Nothing in the SPA can trigger sign-out. T36 joins tasks 5, 6, 30,
31, 33 and 35 as built and uncalled; AC1–AC14 are verified by direct invocation only (F10). Same substance as
**OQ-T33-2** / **OQ-T35-2**.

**OQ-T35-3 — carried, unresolved, and T36 makes it slightly worse.** A signed-out tab still adopts a
sibling's renewal (`crossTabRenewal.ts:95-111`). T36 adds a second credential to clear, and the adoption path
restores only the access token — so after a sibling renewal a signed-out tab holds an access token and **no**
`id_token`, which is exactly AC12's shape. Task 39's. Recorded here because T36 is the task that makes that
combination producible at all.

**Phase 4 complete.** Brief frozen. Eleven findings accepted, two rejected on measurement, one answered by
measurement, one no-action.
