<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction) -->

# frontend · T35 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## Business Rules

- **R19** (`requirements.md:62-65`) — when the user signs out, the SPA clears in-memory token state **and**
  ends the SAS session through the OIDC end-session endpoint with an allowlisted `post_logout_redirect_uri`.
  Revised 2026-10-10 (Q5): **no `/oauth2/revoke` call**, because this client holds no refresh token. The
  end-session navigation is served by SAS, not the SPA shell (R57). **T35 owns the first clause only**; the
  second is task 36.
- **R22** (`requirements.md:70-73`) — sign-out propagates to every open tab. **Task 39 owns this.** It is
  listed because it constrains T35's shape: whatever T35 builds must be callable by a tab that learns of a
  sign-out from a sibling, not only by the tab whose visitor clicked.
- **R57** (`requirements.md:203`) — a navigation to `/connect/logout` is an identity path: it bypasses the
  service-worker fallback and is never answered from cache. Already built (`pwa/navigationDenylist.ts:19`).
  Named because it is what makes the end-session step a **full-page navigation**, which is why T35's clearing
  must complete *before* it and cannot rely on anything surviving it.

## Locked Decisions

| ID | What it constrains here |
|---|---|
| **L3** (`design.md:13-20`) | Tokens in memory only — never storage, logs or analytics. This is why there is something to "clear" at all, and why the clearing is a variable assignment rather than a storage delete. |
| **L16** (`design.md:58-60`) | The sessionStorage carve-out is **exhaustive**: `state`, `nonce`, `code_verifier`, the post-login return target, and R29's mid-task form input. Nothing else is stored client-side. So the complete inventory of what sign-out could possibly have to clear is: the module-level mutables in `src/`, plus these five named items. |
| **L18** (`design.md:63-65`) | Tabs share renewal **and sign-out** through BroadcastChannel or a Web Locks leader. T39 builds the sharing; T35 must leave a seam it can use. |
| **L4** (`design.md:21-26`) | Copy and timing identical across account states. A deliberate sign-out is the visitor's own action, so it discloses nothing — but the *copy* on the way out, if T35 produces any, is still bound by this. |
| **L11** | Every user-facing string comes from the i18n catalogue. |
| **L14** (`design.md:53-56`) | The route set is fixed; `signed-out` is already in `PUBLIC_ROUTE_PATHS`. T35 adds no route. |
| **L12** | Phase additivity — T35 must not edit what tasks 5, 6, 20, 21 and 33 are built on. |

## Files involved

**Read and extend:**

| File | Why |
|---|---|
| `src/auth/accessToken.ts` | `clearAccessToken()` at `:97-99` is the primitive. It already exists and is already idempotent. |
| `src/auth/signInNotice.ts` | The package's second module-level mutable (`:34`). In scope as a thing to clear or to deliberately leave; see AC3. |
| `src/auth/returnTarget.ts` | L16 carve-out item (a) — the post-login return target. In scope for the same reason. |
| `src/auth/pkce.ts`, `src/auth/idTokenNonce.ts` | L16 carve-out items `state`, `nonce`, `code_verifier`. Their readers already delete on use; T35 must establish whether a sign-out mid-flow can leave one behind. |
| `src/api/crossTabRenewal.ts` | The channel T39 will carry sign-out on. Read only — T35 writes nothing here. |

**New:** one module, `src/auth/signOut.ts` (name proposed, frozen in phase 2), plus its test file.

**Not touched:** `src/app/routes.tsx` (T37 attaches the screen), `src/i18n/catalogue.ts` (T37 adds the
`signedOut` group — T35 needs no copy of its own because it renders nothing).

## Dependencies

- `clearAccessToken` from `src/auth/accessToken.ts` — the one writer of `null`.
- `takeSignInNotice` from `src/auth/signInNotice.ts` — reading clears, which is a clearing primitive already.
- `takeReturnTarget` from `src/auth/returnTarget.ts` — same shape.
- **No contract, no API call, no config key.** R19's revision removed `/oauth2/revoke`, and the end-session
  navigation is T36's. T35 touches no `contracts/api/*.yaml` and no generated client, so **L6** (generated
  client only) is satisfied vacuously.
- `client_id`, `redirect_uri` and `post_logout_redirect_uri` are derived, not configured
  (`FIRST-UI-READINESS.md:40-46`) — relevant to T36, listed so phase 2 does not re-derive it.

## Acceptance Criteria

| # | Criterion | Requirement |
|---|---|---|
| **AC1** | After sign-out, no access token is held: `accessToken.current()` is `null`. | R19 cl. 1 |
| **AC2** | Sign-out is **idempotent** — running it from a tab that holds no token is harmless and leaves the same state, because T39 will have sibling tabs run it on a broadcast. | R19 cl. 1, R22 |
| **AC3** | Sign-out leaves **no in-flight sign-in residue**: no pending sign-in notice and no remembered return target survive it. A deliberate sign-out is not an interrupted session, so a later sign-in must not be silently redirected to where this visitor was, nor greeted with a stale `session-ended` sentence. | R19 cl. 1, L16 |
| **AC4** | Sign-out clears **before** anything that navigates. The clearing is synchronous and complete on return, so the full-page end-session navigation T36 adds cannot interrupt it half-done. | R19, R57 |
| **AC5** | Sign-out reads nothing and returns nothing about the session it ended — no token, no account, no reason — and takes no parameter that could carry one. | L4 |
| **AC6** | Sign-out writes to no storage surface and logs to no console channel. | L3, L16 |
| **AC7** | **The inventory is complete and stays complete.** A source scan over `src/` enumerates the package's module-level mutable state and L16's carve-out keys, and fails if one exists that sign-out does not clear. This is the criterion that makes T35 survive T36: if T36 retains an `id_token` for its `id_token_hint`, this case fails until sign-out clears it too. | R19 cl. 1, L3, L16 |
| **AC8** | T35 adds no route, no catalogue key, no network call and no storage key. | L14, L11, L6 |

AC7 is the one worth arguing about and the reason this task is not a one-liner. R19's words are "clears
in-memory token state"; the literal reading is satisfied by a function that already exists. The reading that
is actually worth building is **an instrument that knows what "in memory" contains**, because the inventory
is about to change underneath it.

## Tests required

**Named test (`package.md` §8):** `signOutClearsMemoryAndEndsSasSession` — the spelling in `tasks.md:63`
and `:64`. Tasks 35 and 36 cite the same name, so **T35 creates the file and T36 extends it**; T35's half is
the "ClearsMemory" clause and the file must be written so T36 can add the "EndsSasSession" cases without
restructuring it.

Boundary cases implied, beyond the eight AC:

- Sign-out run twice in a row, and run before any sign-in ever happened.
- Sign-out while a sign-in is **mid-flight** — a `state`/`nonce`/`code_verifier` triple written but no
  callback yet. Does the triple survive, and should it?
- Sign-out from a tab that adopted a sibling's token rather than exchanging one itself.
- A **control** proving each scan and sweep fires when the thing it watches is present, so a clean report is
  evidence rather than a silent pass.
- A **dormancy canary**, as T33's AC12: nothing in production calls sign-out today, and the day something
  does, a case should fail and hand its author the wiring instruction.

## Open Questions

**OQ-T35-1 — blocking for phase 2, not for this phase. Who retains the `id_token`, and does L3 permit it?**
`design.md:166-167` states the end-session step uses "the SPA's `id_token_hint`", and T10's frozen brief
already pins the URL shape as
`GET /connect/logout?id_token_hint=<JWT>&post_logout_redirect_uri=…&state=…`. **Nothing retains an
`id_token`:** `codeExchange.ts:76-79` returns one, `CallbackScreen.tsx:156` validates its nonce, and it is
then dropped. So by the time T36 runs, the value it needs is gone. Three readings, and the choice changes
T35's scope:

1. **T36 retains it.** Then T35's clearing is written against an inventory that is about to grow, and AC7 is
   what stops that being a silent hole. *This is my reading*, because retaining a value for a caller that
   does not exist yet is T36's business, and AC7 makes the handover safe without T35 guessing the shape.
2. **T35 retains it**, since T35 is "the memory task". Rejected unless the owner says otherwise: it would
   have T35 add state in a task whose statement is about removing it.
3. **Neither — `client_id` substitutes for `id_token_hint`.** OIDC RP-Initiated Logout permits it, and the
   SPA's `client_id` is a known constant. I do not know whether this repository's SAS honours a
   `post_logout_redirect_uri` without the hint; `design.md:168-169` records that end-session "did not work
   before 2026-10-21" and names `SasLoginIntegrationTest.endSession*` as the proof it now does. **That test
   is where the answer is, and it is T36's to read, not mine** — citing it here so T36 does not re-derive it.

Recording, not deciding, because T35 can be built correctly under reading 1 and 3 alike. Phase 2 freezes it.

**OQ-T35-2 — the dormancy, now sixth in a row.** Nothing in the SPA offers sign-out and nothing can: the
authenticated routes have no elements, and `AppShell.tsx` renders a bare `<div>`. T35 will join tasks 5, 6,
30, 31 and 33 as built and uncalled. Same substance as **OQ-T33-2**, raised again because this task makes it
six and because sign-out is the first of five consecutive tasks (35–39) that all land in the same dormant
place.

**Not blockers:** `package.md:123`'s stale `signOutRevokesTokensClearsMemoryAndEndsSasSession` (Q5 removed
the revoke step from R19 on 2026-10-10 and §8 was not updated) — recorded as drift, `tasks.md`'s spelling
used. **Q15** blocks task 38, not this one.

**Phase 1 complete.**
