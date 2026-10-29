# frontend · T26 · Phase 11 — Test Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/11-test-review.md` (Kimi 2.7) |
| **Produces** | `artifacts/11-test-review-resolution.md` |

Six gaps. **Four accepted and implemented, one rejected on measurement, one already in the repository** — though
checking that one found a stale comment of my own. 50 → **53 cases**, and the mutation score is unchanged at
22 of 22, every one by this file.

---

## Gap 1 · The secret sweep never ran over `localStorage` — **ACCEPTED, and it is AC5's own wording**

Verified: `expectNoSecretIn` was called on `sweepSession()` twice and on `sweepLocal()` never. AC5 says "any
stored value or inside any stored key", and `localStorage` is a store.

The behavioural cases do assert `keysIn(sweepLocal())` is empty, so a write during a driven flow is already
caught — but Kimi's point is about **which sentence a reviewer reads when it fails**: "a value R29 forbids was
found in localStorage" rather than "expected `[]` to equal `['debug']`". For a guard whose entire output is a
failure message, that is not cosmetic.

**Implemented.** Both value-sweep cases now sweep `localStorage`'s keys and values for every needle. The
assertion is trivially true over an empty store today, and the comment says so: its value is the instrument being
in place for the day it is not.

## Gap 2 · The namespace regex would match a URL hostname — **ACCEPTED as a message defect, not a hole**

Verified: `(?<!@)\b<root>(?:[.\w-]+)?` matches `<root>.example.com` in `https://<root>.example.com/api`, because
the lookbehind excluded only `@`.

Worth stating precisely, because it changes what the fix is for: **that shape cannot reach production source at
all.** `bundleContainsNoSecretEnvKeys` forbids a scheme-qualified absolute address in production source under L9,
L10 and R58 — comments included, which is why `returnTarget.ts` writes its own examples without a scheme. So
there is no hole here. What there is, is an instrument that would report a hostname with a sentence about L16's
four items, and an instrument whose failures can be wrong is one that gets deleted rather than fixed.

**Implemented.** The lookbehind is now `(?<![@/])`, dropping both the package scope and anything preceded by a
slash, with a control: a synthetic file carrying `https://<root>.example.test/api` yields zero matches.

## Gap 3 · No control for a namespace name in a template literal — **ACCEPTED**

The scan is text, so backticks are irrelevant to it — but "irrelevant" is a claim, and an untested claim about
one's own instrument is how T24's module-scope scan came to test indentation instead of brace depth.

**Implemented.** One case: `` const k = `<root>.auth.evil`; `` is reported as a stranger.

## Gap 4 · Exclude `*.stories.*`, `*.mock.*` and `__mocks__` from the walker — **REJECTED**

**Measured before deciding.** `find src -name '*.stories.*' -o -name '*.mock.*' -o -name '__mocks__'` returns
nothing, and `package.json` names neither Storybook nor `msw`. There is no such file and no tool that would
create one.

Two reasons to decline rather than defer:

1. **It is speculative scope.** The frozen brief and `agents.md` both forbid it: no speculative improvements, no
   scope beyond the task. An exclusion for a file pattern that does not exist cannot be tested against a real
   instance, so it would ship as an untested widening of the one instrument whose breadth is its value.
2. **It would weaken the guard in the direction that matters.** A story demonstrating a storage write and a mock
   naming `indexedDB` are exactly the files a leak hides in: they are compiled, they can be imported by
   production code, and nothing stops a demo key from becoming a real one. If Storybook ever lands, the right
   response is to decide — in the open, in a task — whether its files are production source, not to have
   pre-emptively answered "no" in a guard nobody is reading.

If a story is added and this scan fails, that failure is the design conversation, and it will cost one line.

## Gap 5 · The structural half of Gap 1 — **ACCEPTED in the form Kimi itself preferred, with the residual declared**

Kimi notes this "may be impractical to implement statically" and that Gap 1 is the robust fix. Both are right,
and measurement sharpens it:

- A **third module** writing a token to `localStorage` under any key **is already caught** — not by the namespace
  scan, which sees no name, but by the method scan, because a `setItem` call from a file that is not one of the
  two permitted writers is a violation whatever store it addresses.
- What is **not** caught is that write made from **inside a permitted writer**, under a non-namespace key, on a
  path no flow drives: the namespace scan sees no name, the method scan permits the file, and the runtime watch
  never runs. Gap 1's value sweep covers it the moment the path is driven.

**Implemented** as a control that measures both halves — the namespace scan finding nothing and the method scan
finding the call — with the residual limitation written at the case rather than left in an artifact.

## Gap 6 · The harness output is not self-describing — **ALREADY IN THE REPOSITORY, and checking it found a defect of mine**

The harness has carried a docstring since it was committed: why the list looks as it does, the seven families,
how to read the two columns, and that BROKEN is not KILLED. So the gap as written does not hold.

But reading it against the current table showed **my own family ranges had gone stale**: MA13 was inserted during
Phase 7 and the header still said "a wrong store or a wipe (MA10-MA12)", "a forbidden surface (MA13-MA15)" and
"a secret hidden under a legitimate key (MA16-MA18)", each off by one, with MA21–MA22 unmentioned. Corrected, and
the expected baseline (auth suite 726/0, this file 50/0, every mutant `own`) is now stated in the header so a run
can be read without the artifacts. A stale comment in an instrument is worse than no comment, because it is
believed.

---

## Verification after the changes

- `tsc --noEmit` clean.
- This file: **53 passed**.
- Whole suite: **1246 passed / 1 failed**, exit 1 — the pre-existing environmental
  `lintRejectsHandWrittenBackendFetch.test.ts:118`.
- Mutation: **22 of 22 killed, no survivors, none inherited-only**, re-run after the edits with tests committed
  first. MA22 remains `own`, at two failures.
