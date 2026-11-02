<!-- MODEL: Claude Opus 5 — Phase 9 (Review Resolution) -->

# frontend · T31 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Consumes** | `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |
| **Commit** | `ec8cf25d` |

## Citation verification, before any resolution

Every finding's cited `file:line` was read against source before it was accepted or rejected. Three of Kimi's
findings across T30–T31 have been wrong on fact while pointing at a real weakness, so this is a standing step
rather than a courtesy.

| | Citation | Verified |
|---|---|---|
| I1 | `stateSurvivesRedirectAndIsConsumedOnce.test.tsx:718` | **Accurate** — the line does call `log.findLastIndex`, and `log` is `string[]` (`:184`) |
| I2 | `identityErrors…test.tsx:250-651` | **Accurate** — the bound is exactly `.ts` + no-translator |
| I3 | `loginChallenge…test.tsx:172-177` | **Accurate** |
| I4 | shared test `:1431-1436` | **Accurate** — `ACCOUNT_VALUES` omits `emailVerified` |
| I5 | `readAccount.ts:157-158`, `sessionBoundary.ts:89` | **Accurate** — `if (token)` attaches a truthy whitespace token |
| I6 | `07-mutation-harness.py:75` | **Accurate** |
| I7 | shared test `:1947-1958` | **Accurate** |
| I8 | `readAccount.ts:127-137` | **Accurate** |
| I9 | `readAccount.ts:40-65` | **Accurate** |

Nine for nine on location. One, **I1**, is wrong on its conclusion, and being wrong about it uncovered
something larger than the finding.

---

## I1 — REJECTED on the facts, and ESCALATED as a bigger finding

**The claim:** `tsc --noEmit` exits 1 on current HEAD, so the phase 7 gate record of exit 0 is not
reproducible.

**Measured.** `npx -y pnpm@12.14.0 exec tsc --noEmit` exits **0**, run twice, on the committed
`package.json` (`typescript: 7.0.2`, and `tsc --version` reports 7.0.2). The phase 7 record stands.

Kimi's own guessed cause was right: `node_modules/.pnpm/typescript@5.9.3` exists in the store, pulled in by
`openapi-typescript@7.13.0`, and TypeScript 5.x does reject that line. The two error codes quoted (TS2550 +
TS7006, the second a cascade of the first) are exactly what a 5.x run prints.

**But the probe that confirmed it found something worse.** Compiled with an explicit `--lib ES2022`, the
pinned compiler *does* reject `findLastIndex` — same two codes. Under the project's own `tsconfig.json`,
which declares `"lib": ["ES2022", "DOM", "DOM.Iterable"]`, it accepts it. A probe placed in `src/`:

```ts
const zs: string[] = ['a'];
export const j = zs.findLastIndex((s) => s.startsWith('a'));   // ES2023 — accepted
export const k = Object.groupBy ? 1 : 0;                       // ES2024 — accepted
```

The only diagnostic was `TS2774: This condition will always return true since this function is always
defined` — i.e. `Object.groupBy` is typed as *present*. `tsc --listFiles` confirms the program loads
`lib.es2023.*`, `lib.es2024.*`, `lib.es2025.*` and `lib.esnext.*`.

**So the declared `lib` ceiling is not enforced by TypeScript 7.0.2.** Any file in `src/` may use an ES2024
API against a declared ES2022 target and nothing will say so. That is a real risk for *production* code and a
browser runtime, not for the test file Kimi found — `findLastIndex` is a 2023 method available in every
engine Vite targets, and that file runs only under jsdom.

**Resolution:** no T31 change. This is a toolchain and config matter spanning every task in the frontend, and
narrowing it belongs to whoever owns `tsconfig.json` — adding `"ES2023"` to `lib` would not even help, since
`lib` is what is being ignored. Logged as **OQ-T31-4** and flagged to the owner, because it is the kind of
thing that is cheap to know and expensive to discover later.

A second, smaller lesson: the gate record should name the compiler it used. Phase 7 recorded `tsc --noEmit
exit 0`, which was true but did not say *which* `tsc`, and that ambiguity is what made the finding possible.

---

## I2 — ACCEPTED and fixed: a bound on the PLACE, not just the file

Kimi is right, and this was the finding my own phase 7 self-review named as the first thing to attack (S4).
The allowlist row T31 added to T29's state-word scan was bounded by two structural checks — the file is
`.ts` (so it cannot hold JSX) and names no translator. Both hold for a file that does this:

```ts
throw new ReadAccountError('suspended');     // or
console.warn('account suspended');
```

The row licensed `readAccount.ts` to *name* three states; it did not confine them to the construct the
licence was granted for. The row now carries that construct explicitly:

```ts
confinedTo: /export const ACCOUNT_STATUSES = \[[^\]]*\]/,
```

and the control cuts it out and asserts nothing is left:

```ts
expect(row.confinedTo.test(code)).toBe(true);                       // the construct is still there
expect(outsideTheLicence(code, row)).not.toMatch(stateWords);       // and nothing names a state outside it
```

With a dirty-proof case driving both escapes above, each of which keeps the file `.ts` and translator-free —
which is precisely why the structural pair could not see them.

**S4's residual risk is now closed differently than I expected.** I had flagged that no mutant of mine can
reach this instrument, because the harness mutates production source while the instrument lives in a test
file. That is still true, and it is why the dirty-proof case had to carry the weight. But the place bound is
reachable from production source: a mutant adding a state word to `readAccount.ts` outside the list would
now fail the T29 scan. The instrument went from unreachable-by-mutation to partly reachable.

## I3 — PARTIALLY ACCEPTED: the control added, the camelCase gap shown to be unfixable here

Kimi asks for a T27-scoped control proving every spelling T27 means to catch still is. Added, with four
caught spellings (`tfa`, `tfaCode`, `tfa_method`, `TFA`) against three `*tFailure` types that fired before
the narrowing.

Kimi's substantive worry — that `\btfa` no longer catches a camelCase suffix like `paymentTfa` — is correct
and **not repairable in that pattern**. It is compiled case-insensitively at both call sites (`:201`,
`:205`), and under `i` there is nothing to distinguish the `tFa` inside `ReadAccountFailure` from the `Tfa`
inside `paymentTfa`: both are `tfa` followed by a letter. Telling them apart needs case, which `i` discards.
Restoring the unbounded form to recover `paymentTfa` brings back a false positive on every `*tFailure` in the
tree — and the payment tasks are specified to write those. That is the worse trade: a scan that cries wolf
gets its exceptions widened until it means nothing.

The cost is now stated in the case itself, so the bound cannot be removed as a "fix" without reading why it
is there. `twoFactor` and `multiFactor` are caught by the `multi[-_ ]?factor` and `2fa` alternatives, not by
this one. Deferring the whole thing to T27's next review, as Kimi suggests, would have left the narrowing
unpinned in the meantime; the narrowing is T31's, so its control is T31's.

**FU-T27-1 logged:** `two[-_ ]?factor` is in no alternative. Adding it is probably a free win, but widening
someone else's pattern is not T31's call.

## I4 — ACCEPTED as a gap, fixed by rejecting the recommendation

The citation is exact: `ACCOUNT_VALUES` omits `emailVerified`, while its comment claimed "every value a leak
could carry". The comment overclaimed and has been corrected.

Kimi's fix — add `String(SUSPENDED.emailVerified)` to the needles — is the one thing not done, because
`'true'` and `'false'` are among the least distinctive substrings in any document. Their absence from a
surface proves close to nothing, and a sweep that reports a hit whenever a React warning says "false" gets
its needles deleted by the next maintainer. This is the same reasoning the file already applies to keep
`ACTIVE` out of the list, written three lines above the gap.

The needle-free form of the same claim is stronger: **the success path writes nothing to either store at
all.**

```ts
expect(Object.keys(globalThis.sessionStorage)).toEqual([]);
expect(Object.keys(globalThis.localStorage)).toEqual([]);
```

Proven non-vacuous by **MQ26**, a cache holding nothing but `String(data.emailVerified)` — exactly the
regression Kimi described, invisible to every needle, killed with 6 failures.

## I5 — ACCEPTED and fixed: whitespace is not a token

```ts
if (token === null || token.trim() === '') throw new ReadAccountError('no-session');
```

`'   '` is truthy, so `sessionBoundary.ts:89`'s `if (token)` *does* attach it and `Authorization: Bearer    `
goes out — a malformed bearer, which the live server answers with the measured 401, its `Set-Cookie:
JSESSIONID`, and one renewal that cannot succeed. Both side effects this guard exists to prevent, reached by
a value the `=== ''` guard waved through. Kimi is right that it is the same class of gap T30 paid a phase 11
finding for.

The token value itself is never trimmed — only the decision about whether it is a token at all. Two cases
added (a single space; a tab and a newline), and **MQ27** narrows the guard back.

**FU-T31-4 logged:** `readProfile.ts` still guards `null || ''` and has the same gap by the same mechanism.
It is T30's module, closed PASS, and reaching into it was not worth widening this diff — S4 named cross-task
edits as the largest risk here. Flagged for whoever opens that file next.

## I6 — ACCEPTED and fixed, plus a defect of my own found while fixing it

The count pin was what caught T29's baseline drift, so it stays. But Kimi is right that a count absorbs a
*swap*: if the environmental failure were fixed on the day an unrelated test broke, the floor would still
read 1 and every verdict would be measured against a different test. The failing files are now pinned by
name as well:

```python
KNOWN_ENVIRONMENTAL_FAILURES = {"src/api/lintRejectsHandWrittenBackendFetch.test.ts"}
```

**And while in there, a worse hole:** `BROKEN` never reached the exit code — `return 1 if survivors else 0`.
`preflight()` guards the anchors it can see before the first verdict, but `score()` prints `BROKEN` too, when
a run yields no parseable count. That is T30's exact score-inflation defect in its one remaining hiding
place. Both now fail the run, and the run prints a `broken:` line next to `survivors:` so the record cannot
be read as clean when it isn't. This is the third time this class of defect has cost something, and it is now
closed structurally rather than by remembering to look.

## I7 — ACCEPTED as documentation, no code change

Correct and deliberate. AC9's "internal message" property is a **source-level pin**: it proves the three
messages are not catalogue copy, so rendering one cannot be mistaken for rendering commissioned copy. It does
not prove no screen ever renders them, and it cannot — no consumer exists yet. Recorded here so the next
reader does not mistake the pin for the whole property. **The obligation transfers with `failure`:** a
consumer maps that closed set of three onto keys of its own, and whoever writes the first one owns proving it.

## I8 — ACCEPTED and fixed: blankness, not emptiness

`isNonEmptyString` → `isNonBlankString` (`value.trim() !== ''`). A required field present as one space is
the same absence wearing a type: an email of one space renders as nothing and reads as a bug in whatever
shows it. Kimi rated it informational and within the brief's "missing or empty"; it is taken anyway because
it is one character of code and it pairs with I5 — emptiness now means the same thing in both places in this
module. UUID and ISO-8601 *shape* validation is not taken: that is a different claim, it belongs to whoever
treats those fields as trustworthy, and guessing at it now would pin a format the contract does not state.
**MQ28** narrows it back.

## I9 — ACCEPTED and fixed

`export` removed from `NoUnlistedStatus`. Nothing imported it — the only other mention in the tree is a
comment in the shared test explaining what it does. The compile-time assertion fires exactly as before; a
type alias is checked at its declaration whether or not it is exported.

---

## What changed in code

| File | Finding | Change |
|---|---|---|
| `src/profile/readAccount.ts` | I5, I8, I9 | whitespace guard; `isNonBlankString`; `NoUnlistedStatus` unexported |
| `src/auth/identityErrors…test.tsx` | I2 | `confinedTo` place bound + dirty-proof control |
| `src/auth/loginChallenge…test.tsx` | I3 | T27-scoped control for the `\btfa` narrowing |
| shared test file | I4, I5, I8 | store emptiness on the success path; 2 whitespace cases; blank required fields; 3 instrument rows |
| `07-mutation-harness.py` | I6 + own | name-pinned baseline; BROKEN fails the run; MQ26–MQ28; anchors follow the source |

## Measured after the fixes

| | |
|---|---|
| T31's own cases | **52 / 52** |
| Shared file | **105 / 105** (T30's 53 untouched) |
| `identityErrors…` | **58 / 58** · `loginChallenge…` **51 / 51** · `tokensAreNever…` **48 / 48** |
| Whole suite | **1506 passed / 1 failed (1507)** — the known environmental one, pinned by name |
| Mutation | **28 / 28 killed, 0 broken, no survivors**, every kill by T31's own cases |
| `tsc --noEmit` | exit 0 (TypeScript **7.0.2**, named this time) |
| `oxlint src` | exit 0 |

## Open questions and follow-ups raised by this phase

- **OQ-T31-4** — the `lib` ceiling in `tsconfig.json` is not enforced by TypeScript 7.0.2; `src/` may use
  ES2024 APIs against a declared ES2022 target silently. Cross-task, owner's call.
- **FU-T31-4** — `readProfile.ts` has I5's whitespace gap by the same mechanism; T30 is closed.
- **FU-T27-1** — `two[-_ ]?factor` is in no alternative of T27's MFA identifier pattern.
- **OQ-T31-3** stays open by owner decision: nothing owns *acting* on a readable-but-not-ACTIVE account.

**Phase 9 complete.** Nine findings: six fixed in code, one rejected on the facts and escalated, two resolved
as recorded. One defect of my own found and fixed while resolving I6.

---

## CORRECTION, added in phase 11 (2026-11-02)

**The I1 resolution above misattributes the cause.** It says "the declared `lib` ceiling is not enforced by
TypeScript 7.0.2". The measurement behind that sentence was real, but the explanation was wrong, and Kimi's
phase 11 A1 is what sent me back to it.

The actual cause is a **transitive dependency**:
`type-fest@0.16.0/source/basic.d.ts` declares `/// <reference lib="esnext"/>`, and it enters the program
through `workbox-build` → `tempy@0.6.0` → `type-fest`. That reference pulls the entire esnext lib set into
the program and lifts the declared `"lib": ["ES2022", …]` ceiling for **every file in `src/`**, under any
compiler. It explains each measurement at once:

- both TypeScript 7.0.2 **and** the 5.9.3 in the store accept `findLastIndex` under this `tsconfig.json`;
- a config-less probe, which has no program and therefore no `type-fest`, rejects it;
- `tsc --listFiles` loads `lib.es2023.*` through `lib.esnext.*`.

So it is not a compiler quirk. **OQ-T31-4 stands but reads differently**: a dependency's `.d.ts` silently
raises this project's language-level floor, and the remedy is dependency or `types` hygiene, not an edit to
`lib` — editing `lib` would change nothing, which is what made the original diagnosis look right.

The concrete breakage is gone either way: the one ES2023 call in `src/` is now an ES2022 `reduce`
(`stateSurvivesRedirectAndIsConsumedOnce.test.tsx:718`), and a sweep found no other post-ES2022 array or
object API in the tree. The suite and both compilers are green without depending on the widening.

Recorded here rather than silently rewritten, because the phase 12 verdict rests on this artifact.
