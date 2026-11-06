<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation) -->

# frontend · T36 · Phase 13 — Merge Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12's verdict is **PASS**, so this phase proceeds.

---

## Commit title

```
feat(frontend/T36): sign-out ends the SAS session
```

## Commit message

```
Build R19 clause 2: clear locally through task 35's signOut(), then hand the
browser to SAS's end-session endpoint with an id_token_hint and the allowlisted
post_logout_redirect_uri.

Without clause 2 the local clear is cosmetic. The SAS session cookie survives,
so the next /oauth2/authorize returns a code without asking for anything, and a
visitor who believed they signed out is signed back in by the act of visiting.

auth/idToken.ts (new) holds the ID token and exports NO plain reader:
takeIdToken() returns and clears in one operation, so there is no way to obtain
the value without consuming it. Its own module rather than a second variable in
accessToken.ts, because task 35 established that every piece of session state
has an owning module and a clearing primitive — so a new piece of state getting
a new owner is the pattern, and folding it in would have been the exception.

auth/endSession.ts (new) takes the token, calls signOut(), then navigates.
A full-page navigation, which is the opposite of task 33's and task 35's rule:
both forbid globalThis.location in their own modules. SAS serves the end-session
response itself (R57 keeps the service worker off that path), so no router
navigation can satisfy clause 2. It cannot live in signOut.ts either — that
file's own scan forbids `navigate` in it.

signOut() gains takeIdToken() second, for the first step's reason rather than a
reason of its own: an ID token is a credential, so it belongs with the token
clear rather than among the three stores. Second because, between two
credentials, the bearer token is the one an attacker can spend.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

**The template's trailer is stale and was not reproduced.** `.ai/generate.py:476` instructs this phase to end
the message with `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. That is the wrong
model and the wrong identifier for this session, and it has now been wrong for **eighteen consecutive tasks**
from a single source line. The correct trailer is used above. Flagged rather than silently followed, and
flagged rather than fixed: `.ai/generate.py` is generator machinery, not this task's to edit.

## Files changed

| File | | |
|---|---|---|
| `frontend/src/auth/idToken.ts` | **new** | 70 lines — `retainIdToken`, `takeIdToken`, one `let`, no reader |
| `frontend/src/auth/endSession.ts` | **new** | 122 lines — `END_SESSION_PATH`, `endSession`, two private helpers |
| `frontend/src/auth/signOut.ts` | +12 | one added call, second in the body |
| `frontend/src/auth/CallbackScreen.tsx` | +7 | one added line beside `retainAccessToken` |
| `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | +792 | 46 cases appended; five task-35 structures extended, each marked |

**997 insertions, 6 deletions** across 5 files. No `spec/` file touched. No `pwa/`, `api/`, `app/routes.tsx`,
`i18n/`, `auth/accessToken.ts`, `auth/returnTarget.ts`, `auth/authorizeRequest.ts` or `auth/signInNotice.ts`
modified.

## Testing performed

| | |
|---|---|
| own suite | **84 passed (84)** — 38 task 35's unchanged, 46 task 36's |
| whole suite | **1662 passed / 1 failed of 1663** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| mutation | **33/33 regressions killed · 0 survivors · 0 broken · 0 canaries · exit 0** |

The one whole-suite failure is the **pre-existing environmental** case
`src/api/lintRejectsHandWrittenBackendFetch.test.ts`: this host's `oxlint@1.87.0` prints no summary line for
the assertion to parse. Present before T36; baseline was 1616/1617 at T35's close.

Reproduce:

```
npx -y pnpm@12.14.0 exec vitest run
npx -y pnpm@12.14.0 exec tsc --noEmit
npx -y pnpm@12.14.0 exec oxlint src
python3 -I .ai/prompts/frontend/T36/artifacts/07-mutation-harness.py
```

**Four coarse kills were verified narrowly** by name rather than accepted on their failure counts — ES04,
ES26, ES05, ES25 in phase 7, and ES31, ES32 in phase 11. ES04 and ES26 produce identical top-eight failure
lists, so the counts could not separate them; running AC7's case alone confirmed ES26 dies on the scan written
for it.

## Specification references

**Task 36** (`spec/frontend/tasks.md:64`) · **R19** clause 2 · **R57**, **R58** · **L2**, **L3**, **L4**,
**L9**, **L10**, **L11**, **L12**, **L14**, **L16**, **L17** · **Q5** (2026-10-10, no `/oauth2/revoke`).

Eight deviations are recorded in `artifacts/12-specification-verification.md`. The two that most affect a
reader: **D2**, AC4 is *checked against* the route table rather than derived from it, because `routes.tsx` has
no `SIGNED_OUT_PATH` export and is closed to this task; and **D1**, L3 names *access tokens* only, so sweeping
fourteen surfaces for the ID token is a stricter reading by interpretation rather than compliance with a
clause.

---

## Open for the owner

Ranked by what blocks something.

1. **OQ-T36-5 / OQ-T35-2 / OQ-T33-2 — nothing calls `endSession()`.** Seventh consecutive task built and
   dormant (5, 6, 30, 31, 33, 35, 36). AC1–AC16 are verified **by direct invocation only**. No task in
   `tasks.md` owns a sign-out affordance. The import scan's empty `PERMITTED_IMPORTERS` is the handoff: its
   failure message tells whoever wires it to add themselves and close this.
2. **R19 has no end-to-end proof, and the task that would give it is blocked.**
   `signOutThenSignInRequiresCredentials` is **task 38**, *blocked on Q15*. T36 proves the SPA sends the right
   request; nothing in this repository proves SAS honours it.
3. **OQ-T35-3 — now producible, and T36 is what made it so.** `crossTabRenewal.ts:95-111` adopts a sibling's
   renewal without asking whether this tab signed out. After T36 that tab holds an access token and **no ID
   token** — precisely AC12's shape, which phase 2 recorded as *unreachable in production today*. That
   sentence is now false. Task 39's.
4. **OQ-T36-4** — whether SAS 1.5.1 honours `post_logout_redirect_uri` with no `id_token_hint`. Needs a
   backend measurement; both existing backend tests supply the hint. Answering it is what would let a future
   task stop retaining the ID token at all and delete `idToken.ts`.
5. **`package.md` drift, fourth instance.** `:123` still names
   `signOutRevokesTokensClearsMemoryAndEndsSasSession` though Q5 removed the revoke step; `:171` maps
   struck-out task 34; §11 leaves Q5 unstruck. Not fixed — `spec/` is not this task's to edit.
6. **`.ai/generate.py:476`'s stale trailer**, eighteen tasks running.
7. **FU-T36-1..4** — the fourteen-surface sweep not applied to T33's and T35's modules; the de-slash
   duplication with `authorizeRequest.ts`; the broken-counts-as-killed arithmetic in four earlier harness
   copies; the mirror `retainIdToken` order case for whoever next opens T19's suite.
8. **OQ-T32-4**, ten reviews across four tasks — `strip` is on its fourth copy and `watchHandover` its fifth.
   The extraction needs a task scoped to touch closed suites.

**Phase 13 complete. T36 CLOSED — PASS.**
