# frontend · T30 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

Four changes, in dependency order. The frozen brief's twelve ACs are the specification; this plan says only
how they will be met and what has been measured about the ground.

---

## Order of work

### 1 · `contracts/api/oidc.yaml` — `GET /userinfo`

Authorized by T30-D2. The file's own rule — "Only endpoints a client calls programmatically belong here" —
is satisfied: `/userinfo` is a request, unlike `/oauth2/authorize`, which is a navigation and is documented
nowhere.

```
GET /userinfo
  operationId: getUserInfo
  (no `security: []` — authenticated, unlike POST /oauth2/token)
  200 → UserInfoResponse
  401 → empty body; the detail is in WWW-Authenticate (measured, AC8)
  302 → documented as a response, because it is what a bearer-less call gets (measured, AC12)
```

`UserInfoResponse`: `required: [sub]`, with `email`, `email_verified`, `name`, `given_name`, `family_name`,
`preferred_username` and `picture` **optional**. Writing `sub` as the only required property is the contract's
statement of AC9 — the live server returns exactly that, and a schema requiring more would make a correct
response a validation failure.

Documenting the **302** is deliberate and slightly unusual. It is not an error the SPA provokes; it is what
SAS does when no bearer arrives, and leaving it out of the contract is what lets a reader assume 401 is the
no-session answer. It is not.

### 2 · Regenerate the client

`openapi-typescript 7.13.0` → `libs/ts/api-client/src/generated/oidc.ts`. Never hand-edited (AC2). The
generator is already wired as the package's `generate` script; it must be run through
`npx -y pnpm@12.14.0`, since bare `pnpm` is not on this host.

### 3 · `frontend/src/api/client.ts` — `OIDC_PUBLIC_OPERATIONS` and `createOidcApi`

```ts
export const OIDC_PUBLIC_OPERATIONS: ReadonlySet<string> = new Set(['POST /oauth2/token']);
```

`createOidcApi(boundary, rateLimit?)` mirrors `createAuthApi` exactly, including **registering the rate-limit
notice first** — openapi-fetch runs `onResponse` in reverse registration order, and `client.ts:21-23` already
records why. Mirroring rather than inventing is the point: two factories that differ only in their contract
and their public-operations set are reviewable; two that differ in middleware order are not.

This is the first authenticated OIDC operation. `POST /oauth2/token` is `security: []`, so until now the OIDC
client needed no boundary at all.

### 4 · `frontend/src/profile/readProfile.ts`

`readProfile(api): Promise<Profile>`, `Profile` derived from the generated `UserInfoResponse` (F10).

Three decisions the measurements forced, each of which would otherwise have been a plausible-looking defect:

| Decision | Why, measured |
|---|---|
| **Short-circuit with no token; issue no request** | A bearer-less call returns **302 → `/login`**, and `fetch` follows it into **HTTP 200 `text/html`**. There is no 401 for the boundary to act on |
| **`redirect: 'manual'`** | Without it, 1103 bytes of login HTML arrive with a success status and `openapi-fetch` tries to parse them as a profile |
| **Treat any 3xx as no-session** | Same reason, stated at the response end rather than relying on the request option alone |

Failure is a rejected promise carrying `ReadProfileError` — neutral, no cause, no detail, and in particular
**nothing from `WWW-Authenticate`**, whose `error_description` is `"An error occurred while attempting to
decode the Jwt: Malformed token"` on the live server. The reader takes its boundary as a parameter rather than
constructing one (T30-D4), so the unwired composition root stays exactly as unwired as it was.

### 5 · `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx`

T30 owns imports, setup, helpers and every `describe('T30: …')` block; T31 adds `describe('T31: …')` only (F6).

Twelve `describe` blocks, one per AC. Reused from T29 rather than rewritten: the literal-aware comment
stripper, `encodings()`, the six-channel console watcher with a per-channel sentinel, the surface sweep, and
the allowlist-as-upper-and-lower-bound shape.

**The AC5 scan is the file's centre of gravity**, because it is R20's prohibition and the only clause an
implementation can violate invisibly. Two grammars, as T28 and T29 settled: decode **forms** are identifiers
(`atob`, `JSON.parse`, `TextDecoder`, `Uint8Array.from`, `split`), and claim **names** are word-bounded. One
allowlist row, asserted as an exact count.

---

## Hazards measured in advance

**M1 · The AC5 scan will flag `idTokenNonce.ts` unless the row exists** — Kimi's F1, verified at
`idTokenNonce.ts:61,71,75`. The row must list five forms, not Kimi's three: the real code also uses
`TextDecoder` and `Uint8Array.from`.

**M2 · `split` is a dangerously broad form.** Scanning for `split` across the tree hits ordinary string work.
The scan must key on **a decode of a token**, not on `split` alone — so the form list is applied to the
allowlisted file's accounting, while the tree-wide scan keys on `atob`, a JWT-segment index, and the claim
names. Otherwise AC5 fails on unrelated code the day someone splits a path. **This is the T29 I1 lesson
arriving early:** a pattern that is right about intent and wrong about breadth fails on commissioned code.

**M3 · `redirect: 'manual'` in jsdom.** jsdom's fetch does not implement redirect modes the way a browser
does, so the no-session path must be driven by a **stubbed response** (a 302 with a `Location`) rather than by
a real redirect, and the case must say so. Asserting that the *option was passed* is the structural half; a
302 answered from the stub is the behavioural half. Both, because neither alone shows the hazard is closed.

**M4 · `generatedClientCompiles` must keep passing** after regeneration (AC2), and the generated file must be
byte-identical to the generator's output — checked by regenerating into a temporary directory and diffing.

**M5 · O6 means the end-to-end read cannot be demonstrated.** AC9 (a `{"sub"}`-only success) and AC11 (a
full-profile fixture) together cover both shapes. No skipped test (F9).

---

## Verification plan

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | clean, and **AC3 depends on it rejecting a hand-written fetch** |
| The new file alone | all green |
| Whole suite | 1398 passed / 1 failed, the one being the known `lintRejectsHandWrittenBackendFetch` environmental failure |
| `generatedClientCompiles` | still passing (AC2) |
| Mutation | a harness at `artifacts/07-mutation-harness.py`, with T29's baseline guard carried over from the start |

Tests are committed **before** the harness runs: it reverts with `git checkout -- src/`.

---

**Phase 5 complete.** Ready for Phase 6 — implementation.
