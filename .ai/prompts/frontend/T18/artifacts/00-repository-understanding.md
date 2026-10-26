# frontend · T18 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 18):**
> **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

No code written. Everything below was read out of the repository, not recalled.

---

## 1 · Architecture summary

`frontend/` is a React 19.3 + TypeScript 7.0 SPA built by Vite 8.3, one package in a pnpm workspace. There is
**no persistence, no events, no outbox and no server** in this package — the prompt's JPA/Flyway/outbox/
resource-server questions are backend questions and have no counterpart here. The equivalents are:

| Backend concern | This package's equivalent |
|---|---|
| persistence | `sessionStorage`, under L16's closed carve-out; nothing else client-side |
| events | none; `BroadcastChannel`/Web Locks are planned for L18 renewal, not built |
| security boundary | the Caddy edge on `localhost:5173` as the **single origin** (L9), so SPA and backend share it |
| resource-server auth | OIDC authorization-code + PKCE against Spring Authorization Server, via `src/auth/` |
| error handling | RFC 9457 `problem+json` mapped to catalogue copy at the client boundary (`agents.md:66`) |
| configuration | **none** — every value is derived, never configured. No source may name `import.meta.env` or an absolute address |

Modules this task sits in: `src/auth/` (OIDC client, session owner, guards — L1–L3, L16, L18) and `src/app/`
(the route table and shell). Copy lives in `src/i18n/catalogue.ts`, typed into i18next's `CustomTypeOptions` so a
missing key is a **compile error**.

---

## 2 · Existing code this task touches

### Already exists

| File | What it gives T18 |
|---|---|
| `src/auth/CallbackScreen.tsx` (82 lines) | **the screen T18 modifies.** Line 67 is a comment reading "TASK 18 goes here: compare `state` with the stored value and call discard() on mismatch." The `discard()` closure at `:47-52` already sets the notice and navigates with `replace` |
| `src/auth/authorizeRequest.ts` | **the writer of the stored state.** `AUTHORIZE_STORAGE_KEYS.state` = `'themistra.auth.state'` (`:36`), written at `:82`. Its own doc comment at `:17-18` says "tasks 17-20 compare the callback against the STORED state" — this task is anticipated by name |
| `src/auth/signInNotice.ts` | the in-memory, single-read notice a discard carries to the sign-in screen |
| `src/auth/codeExchange.ts` | the seam. Still empty; task 20 fills it. T18 must leave it unreached on a mismatch |
| `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` (606 lines, 73 cases) | **the suite T18 extends.** Named for this task as much as for T17 — `package.md` §8 maps one named test to both |
| `src/auth/pkce.ts` | `randomUrlSafeToken()` — 32 CSPRNG bytes as 43 base64url characters, which is the shape of the value being compared |
| `src/i18n/catalogue.ts` | `signIn.discarded` already exists and is already neutral |

### New

**Possibly nothing.** T18 may be implementable as a few lines inside the existing effect plus cases in the
existing suite. Whether the comparison earns its own module is a phase 2 question, not a phase 0 claim.

### The one thing that will fight T18, by design

`callbackWithMismatchedStateIsDiscarded.test.tsx:566-583` asserts that the callback **reads none of the three
stored keys, even on the success path**:

```ts
const read = getItem.mock.calls.map(([key]) => key);
for (const key of Object.values(AUTHORIZE_STORAGE_KEYS)) expect(read, key).not.toContain(key);
```

**T18 cannot be implemented without making that assertion fail.** That is deliberate: T17 added it (from Kimi's
phase 11 gap 6) with the comment "the boundary T18 and T20 will cross. Pinned now so the suite says when it
moves rather than letting a read drift in unannounced." So T18's first visible effect is a tripwire firing, and
T18 is the authorised change — but it must *narrow* the assertion to "reads the state and nothing else" rather
than delete it, or the boundary stops being guarded at all.

**Verified: no production code reads any of the three keys today.** A grep for `AUTHORIZE_STORAGE_KEYS` across
`src/` returns the writer and test files only. T18 is the first reader in the codebase.

---

## 3 · Established patterns to follow

- **Every value derived, never configured.** No `import.meta.env`, no absolute address, in any source file.
  Enforced by source scans in T01, T13 and T17's own suite.
- **Copy only from the catalogue**, never inlined in a component (`agents.md:97`), with an *inverted* scan
  asserting the component source contains none of the catalogue's values.
- **Neutral, enumeration-safe copy (L4, `agents.md:41-43`).** Identical copy, redirect target, styling **and
  timing** across every failure. This bears directly on T18: a mismatched state must be indistinguishable from
  a missing one. T17 already renders one string for every discard shape, and D-10 left open whether T18 adds a
  `SignInNotice` member or reuses `'discarded'` — **L4 points hard at reusing it.**
- **A named `discard()` path, reused rather than duplicated.** T17 made it a closure for exactly this.
- **Guards fire and are updated in place with a justification at the line**, not silenced. T17 did this twice
  (T02's screen list, T13's URL allowlist).
- **No deletion of stored items.** T17's D-7 locked deletion as **task 23's** by name. T18 reads; it does not
  consume.

---

## 4 · Testing conventions

`agents.md`'s JUnit / Testcontainers / fixed-`Clock` / ArchUnit conventions are **backend** conventions with no
counterpart in this package. What T01–T17 actually established:

- **Vitest 5.0.5 + jsdom 30.1.3.** Run with `npx -y pnpm@12.14.0 vitest run` — bare `pnpm` is not on this host.
- **The real route table, driven through `createMemoryRouter`** with `basename: '/app'`, rather than rendering a
  component in isolation.
- **Every matcher fed a deliberately wrong input.** A test that cannot fail is treated as a defect. This package
  has caught the "assertion compared against the very constant it is testing" error **eight times**.
- **Mutation testing as the acceptance standard for a fix**: undo it, re-run, confirm the suite notices. The
  harness compares **collected totals**, not failure counts, and tests are **committed before any mutant runs**
  because the harness reverts with `git checkout --`.
- **A guard that is the sole catcher of a defect class needs its own negative control** — T17's phase 11
  lesson.
- Known environmental failure: `lintRejectsHandWrittenBackendFetch` **AC4**, because this host's
  `oxlint@1.87.0` prints no summary line. Baseline is **634/635**, not green. AC2 of that file is a *different*
  fault (`spawn pnpm ENOENT`) and passes under the `npx` invocation. Two review phases have already conflated
  these two.

---

## 5 · Known gaps and unknowns

**A real tension in the specification, which phase 1 must resolve rather than paper over.** L16 says the items
are "single-use and **deleted at callback**" (`design.md:58-59`). T17's **D-7** decided *not* to delete, on the
grounds that task 23 ("PKCE state single-use ... deleted at callback") owns it by name. So between T18 and task
23 the stored state is compared but never consumed, which means **a replayed callback URL with the correct state
would compare equal more than once.** Whether that is acceptable, or whether "single-use" is partly T18's,
is a phase 1/2 question. I am not deciding it here.

**Open questions I cannot answer from the repository:**

- **Is a mismatch distinguishable from a missing state in the copy?** L4 and D-10 together suggest no, but D-10
  explicitly left it open. Phase 1.
- **Where does the comparison belong** — inline in the effect, or a named module alongside `codeExchange.ts`?
  Phase 2.
- **What happens when there is no stored state at all** but the callback carries one? That is neither "missing"
  (T17) nor strictly "differs from the stored value", since there is no stored value. It is the shape a visitor
  produces by opening a callback link in a fresh tab, and it is the likeliest real-world mismatch. T17's AC3
  reasoning touches it but T17 discards that case only when the *URL* lacks a state. **I do not know** which
  task owns it; phase 1 must claim it or name its owner.

**Deliberately out of scope, verified as another task's:** deleting the stored items (task 23), the token
exchange (task 20), and anything shown about an authorization-server `error` response (task 19).

**Carried from T17, gating nothing:** OQ-3 (the callback copy is unreviewed); OQ-5 (this prompt's header lists
`stateSurvivesRedirectAndIsConsumedOnce`, which is **task 23's** by name — the same inherited-test noise, since
both named tests map to R8).

---

## Next

Phase 1 — specification extraction.
