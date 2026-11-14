<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding). No code. -->

# frontend · T44 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` · **Task** T44 — Registration accepts 128 characters |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

> **Registration accepts 128 characters.** A 128-character password is accepted client-side as advisory.
> Verifies `registrationAcceptsTwelveAndOneTwentyEightCodePoints`. Cites R1.

**This task arrives one commit after the task that built its instrument.** T43 closed PASS on 2026-11-14
with the named suite this task also verifies, and it was written for this arrival: the length cases are a
**table**, so the upper bound is a row rather than a copied case.

## 1 · Architecture summary

A browser bundle, so the template's persistence/outbox/resource-server sections have no counterpart. What
exists instead:

| | |
|---|---|
| **Stack** | React 19 + TypeScript, Vite 8, react-router 8 under the `/app` basename, Vitest 5 + jsdom, Tailwind 4 CSS-first (tokens in one `@theme` block) |
| **Modules** | `app/` (shell, routes, the 13-primitive design system in `app/ui/`), `auth/` (the OIDC flow: sign-in, callback, signed-out, token in memory), `api/` (the generated client and its middleware), `features/account/` (registration — created by T43), `i18n/`, `profile/`, `pwa/` |
| **Persistence** | None by design. L3 keeps tokens in memory; `sessionStorage` holds only the three L16 PKCE items, and the registration journey writes to neither storage (measured by a live `Storage.prototype` spy) |
| **Security** | No credential collection except registration's own, enforced by T16's credential-shape scan: a three-row table naming `Form.tsx`, `PasswordField.tsx` and `RegisterScreen.tsx` by file, shape **and count**, excusing no `one-time-code` and no `current-password`/`new-password` shape anywhere |
| **Events** | None. The only cross-tab channel is sign-out propagation (`api/signOutPropagatesToAllTabs`) |

## 2 · Existing code this task touches

**Everything this task needs already exists.** Nothing here is new as far as phase 0 can see:

| | | |
|---|---|---|
| `features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | **exists**, 40 cases | holds `PASSWORDS`, six rows: 12, 13, 12-astral in range; 11, 7-emoji, empty out of range. **No row at the upper bound** |
| `features/account/passwordLength.ts` | **exists** | `countCodePoints` spreads the string; `ADVISORY_MAXIMUM_CODE_POINTS = 128` is already declared **and already asserted as a value** by T43 |
| `features/account/RegisterScreen.tsx` | **exists** | counts on submit, marks `aria-invalid` out of range, sends either way |
| `app/ui/EmailField.tsx`, `PasswordField.tsx` | **exist** | **neither sets `maxLength` or `minLength`** — grepped, no occurrence anywhere in `app/ui/` or `features/` |
| `contracts/api/auth.yaml` | **exists** | `password: type: string` with **no `minLength`/`maxLength`**; `email` has `maxLength: 254` |

So the task's surface is a **table row**, and phase 1 must establish whether anything else is implied.

## 3 · Established patterns to follow

| | |
|---|---|
| **The table** | `it.each(PASSWORDS)` with a `{ name, password, codePoints, inRange }` row; each row asserts the request **went**, the body carried what was typed, `countCodePoints` agrees with the row, nothing is disabled, and `aria-invalid` matches `inRange`. A control asserts the `inRange` column is load-bearing in both directions |
| **Instruments are grown, never rewritten** | Six closed tasks' instruments were extended by T43; a change that would require *rewriting* one is a signal the design is wrong and gets reported |
| **A mutation harness per task** | 28 rows in T43's copy, every row predicting `own` / `inherited` / `unfalsifiable`; a BROKEN mutant is not a kill; **read which cases die, not how many** |
| **Commit tests before any harness run** | `revert()` is `git checkout` + `git clean` over `frontend/src/` |
| **Catalogue-only strings** | Every word from `i18n/catalogue.ts`; the registration group is frozen at six keys (T43's D7) because **task 45** owns the advisory guidance copy |
| **Run recipe** | `npx -y pnpm@12.14.0 exec …` (bare `pnpm` is not on this host); **the exit code is the verdict, not the pass count** |

## 4 · Testing conventions

No Testcontainers, no `Clock`, no ArchUnit — the service's equivalents:

| | |
|---|---|
| **Unit/DOM** | Vitest + jsdom, `createRoot` + `act`, the screen mounted under a memory router; a fake `fetch` transport (T05's) plus `installBrowserRequest()` for relative-URL resolution |
| **Typing into a controlled input** | the `HTMLInputElement.prototype` value setter, then an `input` event — a plain assignment is invisible to React, and the helper asserts the descriptor exists |
| **Source scans** | comment-stripped, word-boundary matched where a catalogue sentence could be a substring of an identifier (T43 learned this twice) |
| **The one known environmental failure** | one case of 33 in `api/lintRejectsHandWrittenBackendFetch.test.ts` — this host's `oxlint@1.87.0` prints no summary line. The same file holds the repository's **only end-to-end build gate** (a real `pnpm run build`), which passes and has caught a dropped brace twice |
| **Baseline** | **1938 passed of 1939**, `tsc` 0, `oxlint` 0 at T43's close |

## 5 · Known gaps and unknowns

| | |
|---|---|
| **What else the 128 end implies — the one real question for phase 1** | At the LOWER bound a `.length` implementation over-counts *into* range (a false accept). At the UPPER bound the same bug over-counts *out of* range: 128 astral code points are **512 UTF-16 units**, so `.length` would mark a perfectly valid password invalid. That is a falsification direction T43 could not test, and whether this task owes a row for it is phase 1's to decide, not phase 0's |
| **`maxLength` is absent and nothing pins its absence** | A `maxLength={128}` attribute would truncate at 128 **UTF-16 units** — 64 astral characters — silently cutting a valid password in half before the count ever ran. Today no primitive sets it; whether this task owes a pin is phase 1's |
| **Where 129 belongs** | Task 45 owns *"a password of 11 or 129 code points shows the advisory guidance"* — the **copy**. T43's table already asserts `aria-invalid` for out-of-range rows without copy. Whether the 129 row is this task's (to make "128 is the maximum" falsifiable at all) or task 45's is a scope question for phase 2's gate. **I do not know** which the owner intends |
| **The server's own bound** | The contract constrains the password not at all, so the real 12–128 enforcement is the backend's. Whether auth-service agrees on **code points** rather than characters at the 128 end is outside this package; I have not read it, and this task's claim is client-side and advisory either way |
| **Still open from T43** | OQ-T43-7 (no `autoComplete`), OQ-T43-9 (a rejected send explains nothing), OQ-T43-10 (focus on arrival), OQ-T43-11 (double submit). None blocks this task |
