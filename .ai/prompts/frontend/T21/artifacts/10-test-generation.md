# frontend · T21 · Phase 10 — Test Manifest

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

**35 new cases**, all in the named test, which goes from T20's 69 to **104**. Suite **927 passed / 1 failed of
928**, the one failure being the known environmental `oxlint` case, **verified by exit code** (AC15). **Mutation:
23 mutants, 23 killed, no survivors.** No production code was changed in this phase.

---

## Files

| File | Cases | Why |
|---|---|---|
| `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` | **+35** (69 → 104) | The named test (`package.md:111` → R7). T20 built the verifier half; this is the in-memory half the file's name has promised since then — Phase 8 I1. |

Nothing else needed a file. The holder has no contract, no copy and no new dependency, so there is nothing for
`generatedClientCompiles` or the lint table to say about it; `crossTabRenewal.ts` staying byte-identical is itself
one of the assertions below rather than a reason to touch its tests.

Three layers inside the one file, by what each claim is a property of:

- **the holder directly** — retention, absence, repeated reads, `adopt`, the seam's rejection. These are
  properties of a function, and a component harness would only obscure which input produced which answer.
- **through the real screen** — every failure path, the stale-token case, the leak sweeps. R8, R22 and L4 describe
  what the *screen* does, and the whole point of AC3 is that the screen's failure paths leave nothing held.
- **through the real consumers** — `createSessionBoundary`, `createAuthApi` and `shareRenewalAcrossTabs`, each
  imported and run, never stubbed and never satisfied by a type assertion (AC5, AC6).

Only the network is stubbed. No module is mocked anywhere in the file.

## The trap this harness is built around

`arriveAt` imports the route graph dynamically and `vi.resetModules()` runs between cases, so a module the test
file imported statically is **a different instance** from the one the screens hold. T20's Phase 11 lost three
`takeSignInNotice()` assertions to exactly that, and only a probe revealed they were inert.

So `Rendered` carries a `holder` field, and `arriveAt` does `await import('./accessToken')` **after** the routes
import — the same instance the screen wrote to. Every screen-driven assertion reads `rendered.holder`, and the
control case *holds the access token after a callback that completes* fails outright if the instance is wrong.
That is what makes the nine `holds nothing` rows below it non-vacuous: they share a mechanism with a case that
can only pass when the mechanism works.

## Acceptance criteria → cases

| AC | Where |
|---|---|
| **AC1** retained in a module-scoped variable | `the token is held in memory` → *holds the access token after a callback that completes* |
| **AC2** two unrelated token values | → *holds %s token the server sent, so no constant can pass* (×2) |
| **AC3** null before, and after every failure | → *holds nothing before any exchange*; → *holds nothing after %s* (×9: no `state`, mismatched `state`, no `code`, an authorization error, a refused code, a 5xx, a dropped connection, no usable tokens, a bad nonce); → *CLEARS a token from an earlier sign-in when a later callback is discarded* |
| **AC4** the same value on repeated reads | → *returns the same value on repeated reads* |
| **AC5** the real boundary, `Bearer <token>` on the wire | `the holder satisfies what tasks 5 and 6 already consume` → *puts the held token on the wire as a bearer, through the REAL boundary*; → *sends no bearer at all when nothing is held* |
| **AC6** the real `shareRenewalAcrossTabs` | → *is accepted by the REAL cross-tab renewal, and adopt reaches it* |
| **AC7** `adopt` replaces, last writer wins | → *adopts with nothing held*; → *lets the last writer win when %s* (×2, both orders) |
| **AC8** nothing in storage, URL, DOM, title, console | `one copy, and nowhere else` → *puts no part of the token in storage, the DOM, the title or the URL*; → *writes the token to no console channel*; and the `FORBIDDEN` scan rows for a storage API and a console call |
| **AC9** held once (as T21-D8) | → *hangs the token off no global property*; → *keeps exactly one mutable binding, and it is the token* (one `let`, exactly two writes to it, `adopt` delegating); → the thrown-message scan row |
| **AC10** a discard holds nothing and clears | the nine-row table, and the stale-token case |
| **AC11** the three L16 items unaffected | → *leaves the three L16 items exactly as task 15 wrote them* |
| **AC12** no new visible string, no catalogue key | nothing to assert: T21 adds no copy and `src/i18n/catalogue.ts` is untouched in the diff. The discard outcome the failure paths land on is T17's existing `signIn.discarded`, and the cases above read it through the rendered page. |
| **AC13** no env key, no absolute address, no new dependency | the `FORBIDDEN` scan rows for an environment key and an absolute address; `package.json` untouched in the diff |
| **AC14** T19's check and R8's ordering unchanged | → *holds the token only AFTER the nonce check, never before it* — structural, and necessarily so: a token retained before the check and then cleared by `discard` leaves `current()` null at the end of a case, exactly as the correct order does. The two are distinguishable only in the source, which is why the case also asserts the early `return;` sits between them. |
| **AC15** exit code, not counts | recorded below |
| **AC16** one fixed renewal message, `onRenewalFailed` once | → *rejects renewal with one fixed message carrying nothing*; → *makes a 401 a defined outcome through the real boundary: one failure hook, original 401* |
| **AC17** a clear is exported, `LocalSession` not widened | → *leaves the cross-tab interface untouched, rather than widening it* — asserts `crossTabRenewal.ts` still spells `adopt(token: string): void` and never `string | null` |

## Phase 8 and Phase 7 findings, closed

| Finding | Closed by |
|---|---|
| **I1** the in-memory half is not committed | This phase. All 35 cases, and the minimum set I1 named is a subset of them. |
| **I2 / F1 (self)** AC9 is literally false | The `one copy, and nowhere else` block asserts T21-D8's form — no copy T21 introduces, no durable second copy — not the literal one. The boundary's transient `tokenUsed` is exercised by AC5's own case rather than denied. |
| **I5** `renew()` could become an unhandled rejection | → *makes a 401 a defined outcome through the real boundary*, which drives the seam through the real boundary and asserts the hook ran exactly once with the fixed message; the run's exit code is what proves no rejection escaped. |
| **I7** a forged callback ends an existing session | → *CLEARS a token from an earlier sign-in when a later callback is discarded*, with the tradeoff written into the case so it is not later read as a regression. |
| **I3** two assignment sites | Already fixed in Phase 9; now pinned by the one-mutable-binding case, which asserts `adopt` contains `retainAccessToken(token);` and that `held` is written exactly twice. |
| **I4** `retainAccessToken` validates nothing | Left as documented-by-design (Phase 9). No case asserts a guard that does not exist. |

## A scan that read its own explanation

The holder's source scans run against the source **with comments stripped**. A whole-file scan reads a file's own
explanation as the defect it warns about: `accessToken.ts:12` names `sessionStorage` in order to say the token is
never written there, and `/sessionStorage/` cannot tell the rule from the violation. T20 hit the same thing with
`charset` and fixed it by scanning one line; here the patterns are several, so the prose comes out instead.

The stripping proves itself — each scan row first asserts the stripped code still contains `let held: string |
null = null;` and `export function retainAccessToken(token: string): void {`, so a pass is the absence of the
pattern rather than the absence of a file. Each row also asserts its pattern matches a sample violation, the
house guard against a regex that matches nothing.

## Mutation

`accessToken.ts` and the two T21 lines in `CallbackScreen.tsx`, run against the named file plus
`callbackValidatesNonce.test.tsx` and `callbackWithMismatchedStateIsDiscarded.test.tsx` (**361** cases, baseline
0 failed). Tests were committed first — the harness reverts with `git checkout`.

| # | Mutant | Verdict |
|---|---|---|
| M1 | `current()` hands back `''` instead of nothing | KILLED (12) |
| M2 | `current()` answers with a constant | KILLED (13) |
| M3 | `retainAccessToken` keeps nothing | KILLED (15) |
| M4 | `retainAccessToken` keeps only the first token (`??=`) | KILLED (4) |
| M5 | `clearAccessToken` does nothing | KILLED (5) |
| M6 | `clearAccessToken` leaves `''` behind | KILLED (12) |
| M7 | `adopt` ignores a sibling's token | KILLED (4) |
| M8 | `adopt` only takes a token when none is held | KILLED (2) |
| M9 | `adopt` assigns rather than delegating | KILLED (1) |
| M10 | `renew()` resolves with whatever is held | KILLED (2) |
| M11 | `renew()` throws synchronously instead of rejecting | KILLED (1) |
| M12 | `renew()`'s message carries the held token | KILLED (3) |
| M13 | the seam's message names a server detail | KILLED (2) |
| M14 | the token is also written to `sessionStorage` | KILLED (5) |
| M15 | the token is also written to `localStorage` | KILLED (5) |
| M16 | the token is also hung off `globalThis` | KILLED (1) |
| M17 | the token is logged | KILLED (3) |
| M18 | a second module-level copy is kept | KILLED (1) |
| M19 | the screen retains nothing | KILLED (8) |
| M20 | the screen retains the ID token | KILLED (8) |
| M21 | the screen retains **before** the nonce check | KILLED (1) |
| M22 | the discard path stops clearing | KILLED (1) |
| M23 | a bad nonce keeps the token | KILLED (1) |

**23 / 23 killed.** Four are worth naming:

- **M11** is the one that justifies `Promise.reject` over a `throw`. A synchronous throw is caught by the
  boundary's `try` at `sessionBoundary.ts:120-124` but escapes the `attempt.catch(...)` at `:59`, so the
  difference is real and a case that only awaited the rejection would have missed it.
- **M9** and **M18** are structural-only: both behave identically to the original. They die to the
  one-mutable-binding case alone, which is the case's entire reason for existing.
- **M21** is the ordering mutant. It dies to the structural case only — every behavioural assertion in the file
  passes under it, because `discard` clears the token the mutant retained early. That is the measurement behind
  AC14 being pinned in the source rather than in an outcome.

## Verification

```
$ npx -y pnpm@12.14.0 -C frontend exec vitest run src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx
  Test Files  1 passed (1)
       Tests  104 passed (104)
  exit 0

$ npx -y pnpm@12.14.0 -C frontend exec vitest run
  Test Files  1 failed | 17 passed (18)
       Tests  1 failed | 927 passed (928)
  exit 1   ← src/api/lintRejectsHandWrittenBackendFetch.test.ts:118, AC4: this host's oxlint@1.87.0
            prints no summary line. The known environmental failure, unchanged since T18, and the only one.

$ npx -y pnpm@12.14.0 -C frontend run typecheck
  tsc --noEmit   exit 0
```

The named-file run's **exit 0** is the AC15 evidence that matters: an unhandled rejection is reported *below* the
all-passed line and would leave a run showing 104 passed with exit 1. The seam's rejection and the 401 path both
cross that line.

## Handoffs

- **Phase 11** should look hardest at the three structural-only cases (AC9's binding count, AC14's ordering,
  AC17's interface text). They are the assertions a refactor can falsify without changing behaviour, and the
  mutants show they are the only thing standing behind those claims.
- Still unassigned by the specification, carried from the frozen brief §6: **no task implements silent
  re-authentication** (the seam is a seam), and **no task wires the composition root** — `mountShell.tsx` still
  constructs no boundary, so AC5 and AC6 are proven by isolated use of the real consumers, as T21-D5 freezes.
- **Task 33** must never render `RENEWAL_NOT_BUILT`. It is an engineering message, not copy, and L4 forbids it
  reaching a visitor.
