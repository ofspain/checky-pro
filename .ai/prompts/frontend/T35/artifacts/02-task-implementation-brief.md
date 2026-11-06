<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief) -->

# frontend · T35 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## Task

Build the clearing half of sign-out: one function that leaves the tab holding no session state of any kind.
R19's second clause — the end-session navigation — is task 36 and is out of scope.

## Purpose

So that a visitor who signs out is signed out **in this tab**, before anything navigates. The SPA's session
is one module-level variable (L3) plus four sessionStorage keys (L16); sign-out is the single place that puts
all of them back to their pre-sign-in values. R19.

## Scope

**In**

- A sign-out function that clears every piece of session state the package holds, through the primitive that
  owns each one.
- Idempotence, so a tab that holds nothing can run it (task 39 will make sibling tabs do exactly that).
- An instrument that **enumerates the state inventory from source** and fails when a piece exists that
  sign-out does not clear.

**Out**

- The end-session navigation to `/connect/logout`, the `id_token_hint`, the
  `post_logout_redirect_uri`, and the `state` round-trip — **task 36**.
- Broadcasting sign-out to sibling tabs — **task 39**.
- The `/app/signed-out` screen and any copy on it — **task 37**.
- Any sign-out button, menu or shell affordance — unowned; see Open Questions.
- Retaining an `id_token` — see Open Questions.

## Business Rules

- **R19 cl. 1** — sign-out clears in-memory token state. *(This task.)*
- **R19 cl. 2** — sign-out ends the SAS session via the end-session endpoint with an allowlisted
  `post_logout_redirect_uri`; no `/oauth2/revoke` (Q5, 2026-10-10). *(Task 36; constrains ordering only.)*
- **R22** — sign-out propagates to every open tab. *(Task 39; requires this function be idempotent and
  callable without a visitor gesture.)*
- **R57** — `/connect/logout` is an identity navigation, never cached, never served from the fallback.
  *(Built. Means task 36's step is a full-page navigation, so this task's clearing must be synchronous and
  complete on return.)*

## Locked Decisions

**L3** tokens in memory only · **L16** the sessionStorage carve-out is exhaustive and is the inventory's
other half · **L18** tabs share sign-out through BroadcastChannel or a Web Locks leader · **L4**
enumeration-safe copy and timing · **L11** catalogue-only strings · **L14** the route set is fixed ·
**L12** phase additivity — do not edit what tasks 5, 6, 20, 21, 24 and 33 are built on.

## Dependencies

All four already exist, each idempotent, each owning exactly one piece of state:

| Primitive | Clears | Source |
|---|---|---|
| `clearAccessToken()` | the held access token | `auth/accessToken.ts:97-99` |
| `takeSignInNotice()` | the pending sign-in notice | `auth/signInNotice.ts:41-45` |
| `consumeAuthorizeState()` | `themistra.auth.state`, `.nonce`, `.code_verifier` | `auth/authorizeRequest.ts:121-130` |
| `takeReturnTarget()` | `themistra.auth.return_to` | `auth/returnTarget.ts:120-131` |

`consumeAuthorizeState` and `takeReturnTarget` are already guarded against a `sessionStorage` that throws,
and `consumeAuthorizeState`'s header states its contract: exactly its three keys, idempotent, never throws,
never clears the store. **Note its caution** — "It is NOT a general cleanup helper" — which forbids reaching
for it to tidy unrelated keys, not calling it for the three keys it owns.

No network call. No contract. No config key. No generated-client use (**L6** vacuous).

## Inputs

**None.** The function takes no parameter. There is no session identifier to pass — the state is
module-scoped — and no reason or outcome to accept, which is what makes L4 a type-level property rather than
a discipline.

## Outputs

**None.** `void`. It reports nothing about the session it ended: not what was held, not whether anything was,
not whether a storage write was refused.

## State Changes

- `auth/accessToken.ts` held token → `null`.
- `auth/signInNotice.ts` pending notice → `null`.
- `sessionStorage` keys `themistra.auth.state`, `themistra.auth.nonce`, `themistra.auth.code_verifier`,
  `themistra.auth.return_to` → removed.
- Nothing else. No other storage surface, no cookie, no IndexedDB, no console, no network.

## Files to Create

- `frontend/src/auth/signOut.ts`
- `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` — the name `tasks.md:63` and `:64` give.
  **Task 36 extends this file**, so its harness and its describe blocks must be arranged for a second
  task's cases rather than written as one flat list.

## Files to Modify

**None expected.** Every primitive needed already exists and is already exported.

## Files NOT to Modify

`auth/accessToken.ts` · `auth/signInNotice.ts` · `auth/authorizeRequest.ts` · `auth/returnTarget.ts` ·
`api/crossTabRenewal.ts` · `api/sessionBoundary.ts` · `app/routes.tsx` · `i18n/catalogue.ts` ·
anything under `spec/`. If the work appears to require editing one of these, **stop and log it** — do not
widen an interface tasks 5, 6, 20, 21, 24 or 33 are built on.

## Acceptance Criteria

| # | Criterion |
|---|---|
| **AC1** | After sign-out, `accessToken.current()` is `null`. |
| **AC2** | Sign-out is idempotent: twice in a row, and once with nothing held, both leave the same state and neither throws. |
| **AC3** | No sign-in residue survives: no pending notice, and no remembered return target. A later sign-in is not redirected to where this visitor was, and the sign-in screen shows no stale sentence. |
| **AC4** | The clearing is synchronous and complete when the call returns — nothing is deferred to a microtask, a timer or a promise — so task 36's full-page navigation cannot interrupt it. |
| **AC5** | The function takes no parameter and returns nothing. |
| **AC6** | Sign-out writes to no storage surface and emits to no console channel; the only storage operations are the four removals its primitives perform. |
| **AC7** | **The inventory is complete and stays complete.** A scan over `src/` enumerates the package's module-level mutable state and L16's carve-out keys and fails if one exists that sign-out does not clear. A declared exclusion list, with a justification per entry, is the only permitted escape. |
| **AC8** | T35 adds no route, no catalogue key, no network call, no new storage key, and modifies no file outside the two it creates. |

## Required Tests

Named test `signOutClearsMemoryAndEndsSasSession`, in the file above. Beyond one case per AC:

- Sign-out with **a token held and a mid-flight authorize triple** present — the realistic state of a tab
  whose visitor signed out while a second sign-in was in progress.
- Sign-out with **nothing ever stored**, proving idempotence from the cold state task 39's siblings will be in.
- Sign-out in a tab that **adopted** a sibling's token rather than exchanging one.
- Sign-out when `sessionStorage` **throws on every operation**, proving the token is still cleared and the
  call still does not throw.
- **Controls** for AC6 and AC7: each sweep and each scan must be shown firing on a planted positive, so a
  clean report is evidence rather than a silent pass.
- A **dormancy canary**, as T33's AC12: no production module calls sign-out today; the day one does, a case
  fails and its message hands that author the next step.

Gates: the T35 file green, whole suite no worse than the recorded baseline (**1574 passed / 1 failed of
1575**, the one failure being this host's `oxlint` environmental case), `tsc --noEmit` exit 0, `oxlint src`
exit 0, and a mutation harness with canaries scored apart from regressions.

## Constraints

- **Security.** No token, account identifier, storage key value or failure detail reaches a log, a console
  channel, an analytics call or a thrown error message (L3, L4).
- **Module boundaries.** Clear through the owning primitive; never assign a module variable or call
  `sessionStorage` directly from this module. One new module, no new exported state.
- **No module-level mutable state** in the new file — T19's and T20's suites scan for it, and
  `accessToken.ts:7-8` names itself as the only legitimate holder in the package.
- **Null handling.** Every piece of state has an absent value and sign-out must accept it; nothing it calls
  may distinguish "was held" from "was not".
- **No thread-safety or transaction concern** — single-threaded, and `sessionStorage` offers no transaction,
  so "all or none" is not a property this code can promise. Say so rather than imply it.
- **Synchronous throughout.** No `async`, no promise, no timer.
- **Must not throw**, ever, for any storage condition — a sign-out that fails to tidy is still a sign-out,
  and the next authorize request overwrites all three keys regardless (the reasoning
  `authorizeRequest.ts:127-128` already records).

## Open Questions

**OQ-T35-1 — FROZEN, not blocking: task 36 retains the `id_token`, not task 35.** `design.md:166-167` has
end-session carrying "the SPA's `id_token_hint`" and T10's frozen brief pins the URL shape, but nothing
retains an `id_token` today — `codeExchange.ts:76-79` returns one and `CallbackScreen.tsx:156` drops it after
checking its nonce. Frozen this way because adding state is the wrong act in a task whose statement is about
removing it, and because **AC7 makes the handover safe**: the day task 36 retains an `id_token`, AC7 fails
until sign-out clears it. Task 36 should settle whether the hint is needed at all by reading
`SasLoginIntegrationTest.endSession*`, which `design.md:168-169` names as the proof end-session works.

**OQ-T35-2 — the owner's, raised a sixth time, not blocking.** Nothing in the SPA offers sign-out and
nothing can: no authenticated route has an element and `AppShell.tsx` renders a bare `<div>`. T35 joins
tasks 5, 6, 30, 31 and 33 as built and uncalled, and tasks 36–39 will all land in the same place. Same
substance as **OQ-T33-2**.

Otherwise **no blockers**.
