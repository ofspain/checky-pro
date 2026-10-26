# frontend · T18 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 18):**
> **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Order of work

**T18-D10 fixes the sequence:** the comparison lands before the tests that measure it.

### 1 · `CallbackScreen.tsx` — the comparison (T18-D1, T18-D2, T18-D3)

At the marked line `:67`, replacing the `// TASK 18 goes here` comment:

```ts
// R8's other half. The stored state is the only evidence that this callback answers a request THIS tab made:
// without the comparison, anyone able to open /app/callback?code=…&state=… in the visitor's browser could have
// the exchange run on a code of their choosing.
const stored = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state);
// Nothing stored is tested on its own, so no reader has to work out whether `null !== state` happened to be
// true for the right reason. The stored value is never trimmed or case-folded: T15 wrote it, so padding there
// would mean something is wrong, and anything that widens equality widens the injection surface.
if (stored === null || stored === '' || stored !== state) {
  discard();
  return;
}
```

One import added: `AUTHORIZE_STORAGE_KEYS` from `./authorizeRequest`. The key is never re-spelled as a literal.

**Note the import does not deepen the circular import.** `authorizeRequest.ts` already imports `APP_BASENAME`
from `../app/routes`, and `routes.tsx` already imports `CallbackScreen` — so `CallbackScreen → authorizeRequest
→ routes → CallbackScreen` is a cycle that **already exists** through `SIGN_IN_PATH`. The new strand adds no new
hazard, because `AUTHORIZE_STORAGE_KEYS` is a plain object literal with no dependency on `routes`, and it is
read inside a function. T17's AC12 scan already covers module-scope reads in this file; **step 6 extends it to
the new name.**

### 2 · `CallbackScreen.tsx` — the comment correction (T18-D9)

Replace the false "Task 19 owns…" sentence at `:43` with the frozen text. It names no task number.

### 3 · Split the `:570` case (T18-D4)

The existing case becomes two:

- **retained verbatim** for the early-return shapes — seeded storage, arrival at a `DISCARD_SHAPES` URL, and
  `getItem` asserted to have touched **none** of the three keys, because the effect returns before the
  comparison;
- **new** for the comparison path — seeded storage, arrival at a URL that reaches the comparison, and `getItem`
  asserted to have touched **`state` and nothing else**, with `nonce` and `code_verifier` still unread.

The second case must run on both a match and a mismatch, since T18-D4 makes the read identical either way.

### 4 · The mismatch cases (AC1, AC2, AC3, AC6)

A table of stored-versus-supplied pairs, every one a discard:

| stored | supplied | shape |
|---|---|---|
| `'stored-state'` | `'xyz'` | wholly different |
| *absent* | `'xyz'` | nothing stored (AC2) |
| `''` | `'xyz'` | empty stored (AC3) |
| `'abc'` | `'abd'` | one character |
| `'abc'` | `'ABC'` | case only |
| `'abc'` | `'abcd'` | supplied is longer, stored is a prefix |
| `'abcd'` | `'abc'` | supplied is shorter, a prefix of stored |
| `' abc '` | `'abc'` | stored padded — never trimmed (T18-D3) |
| `'abc'` | `'bc'` | suffix |

Short literals per T18-D7; each asserts the seam is unreached, the landed path is `/app/sign-in`, and the copy
is `signIn.discarded`.

### 5 · The positive control and the anti-tautology case (AC4, AC5)

- **AC4** — stored `'xyz'`, supplied `'xyz'`: the seam **is** called once, with `objectContaining({ code, state })`.
  Without it every row above would hold for a screen that can never exchange.
- **AC5 / T18-D6** — one case, two navigations carrying the **same** URL `state` and `code`, changing only the
  stored value between them, asserting the outcome flips from discard to seam-call. This is what proves the
  comparison reads storage rather than a constant.

### 6 · AC16 — the source scan for widened equality

Over `CallbackScreen.tsx`, reusing T17's `moduleScopeUses`-style approach but as a content scan: no `==` (other
than inside `!==`/`===`), no `.trim()` applied to the stored value, no `toLowerCase`, no `normalize`, no
`startsWith`/`endsWith`/`includes` on the state. **Each pattern carries its own synthetic violation** in the
same record, per T17's F6. Extend the existing AC12 module-scope scan to `AUTHORIZE_STORAGE_KEYS` as well.

The `==` pattern needs care: `/[^=!<>]==[^=]/` matches a loose equality without matching `===` or `!==`. Its
negative control is `if (stored == state) {`.

### 7 · AC7 — identical to T17's missing-state outcome

Collect the rendered text, landed pathname, `historyAction` and `location.search` for a mismatch arrival and for
a missing-state arrival, and assert the four are equal **to each other**. Not to the catalogue — that is the
tautology this suite avoids.

### 8 · AC8, AC10, AC11, AC12

The three items present and unchanged after a mismatch; nothing written (`setItem`/`removeItem`/`clear` spies);
a mismatch under `{ strict: true }` navigating once and reaching the seam zero times; one `main`, one `h1`, one
live region on the landed screen.

### 9 · Verification sweep

1. `vitest run` on the file; then the whole suite against **634/635** (T18-D11), confirming
   `callbackWithMismatchedStateIsDiscarded.test.tsx:570` is the **only** pre-existing case that changed.
2. `npx tsc --noEmit`; `npx oxlint --no-ignore -c .oxlintrc.json src`.
3. **Commit** — before any mutant runs, because the harness reverts with `git checkout --`.
4. Mutants, each expected to be killed:
   - drop the `stored !== state` term → the mismatch rows fail;
   - drop `stored === null` → the nothing-stored row fails;
   - drop `stored === ''` → the empty-stored row fails;
   - replace `!==` with a `toLowerCase()` comparison → the case-only row fails;
   - trim the stored value → the padded row fails;
   - compare against a constant instead of storage → **AC5 must fail**; this is the mutant that matters most;
   - break one AC16 pattern, leaving its control → the control fails.
5. The browser check against the live stack: a real sign-in still completes to the callback (the state matches,
   so the seam is reached), and a hand-edited `state` in the callback URL lands on `/app/sign-in`.

---

## Risks

- **The `:570` split is the one place this task can silently weaken an existing guard.** Mitigated by T18-D4
  naming exactly which assertion is retained verbatim and by T18-D11 naming it as the only expected change.
- **AC5 is the case most likely to be written vacuously** — two navigations that differ in the URL rather than in
  storage would pass against a constant comparison. The dedicated mutant in step 4 is the check.
- **The `==` scan pattern can misfire** on `===`/`!==` if written carelessly, which is why it carries a negative
  control.

---

## Next

Phase 6 — implementation.
