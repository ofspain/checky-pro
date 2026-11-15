<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation). Phase 12 verdict: PASS. -->

# frontend · T45 · Phase 13 — PR Preparation

## Commit title

```
feat(frontend/T45): say the advisory rule out loud, and tie it to the field it is about
```

## Commit message

```
Render R1's "advisory guidance only" for the first time. The check has existed since
task 43 and the bounds since task 44, but nothing ever said anything: a visitor who
typed eleven characters saw an outlined box, a status line announcing that their
account was being created, and no explanation anywhere. This task gives the
out-of-range state a sentence, and gives the sentence to the field it is about.

Add one catalogue key - the FIRST INTERPOLATED STRING IN THIS APPLICATION. The
mechanism was configured in task 16 and never used, and interpolation is not
decoration here: R1's bounds live in passwordLength.ts, so a literal "12 to 128" in
the catalogue would be a second copy that goes stale the day a bound moves. The
screen interpolates ADVISORY_MINIMUM_CODE_POINTS and ADVISORY_MAXIMUM_CODE_POINTS,
and two assertions keep both ends of that wire honest - the catalogue must contain
{{min}}/{{max}}, and the screen must name those two constants. Both are source scans
because provenance is INVISIBLE TO THE DOM: a catalogue with the numbers typed in, or
a screen passing { min: 12, max: 128 }, renders a byte-identical sentence. The second
of those passed 126 of 126 cases until it was measured.

The sentence names the RULE and delivers no verdict. R1 makes this check advisory and
the server the authority, so the client has no standing to tell anyone their password
is wrong - it can only say what the rule is. One string therefore serves both ends of
the range and the screen never branches on which bound was missed. The words are
pinned once, non-derived, because every other assertion builds its expectation FROM
the catalogue and would move with any rewrite.

Give PasswordField an 'aria-describedby' prop. Rule 2 permits it: it forbids a
primitive accepting the attribute that IS its contract, and permits a relationship the
consumer computes. The screen owns the message, so only the screen can know its id.
ActionButton has taken this exact prop since task 16 and Text carries `id` "for one
reason: aria-describedby". One condition drives both the mark and the sentence, because
a field marked invalid with nothing to read is what task 43 shipped and what this task
exists to finish, and a sentence nothing points at is the same defect for anyone who
cannot see the layout.

Add the named suite, 19 cases, and grow the harness to 48 rows. Two cases measure
things the brief did not ask about and nothing held. The guidance table's astral row
sat at 129 code points, which is 258 UTF-16 units and therefore out of range on BOTH
counts - it cannot tell which unit the screen compared. At the lower end the same bug
inverts: 11 astral code points are 22 units, 22 is inside 12-to-128, and a screen
counting units says NOTHING to someone R1 requires be told. Task 43 catches that
comparison through the mark; the sentence was uncovered at the one end where the
failure is silence. And `advisory` is assigned in submit() and nowhere else, so a
sentence raised by one submit stays up while the visitor fixes the password - nothing
measured that either, so it could have been made to vanish on a keystroke with 18 of
19 cases green.

Four harness runs across three phases, identical verdicts in all four: 44/44
regressions killed, three canaries killed, one expected unfalsifiable survivor, no
broken rows, no disagreements.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

> **Trailer note.** The phase 13 prompt template still asks for a
> `Claude Opus 4.8 (1M context)` trailer. The session's standing attribution is
> `Claude Opus 5`, which is what every commit in this task used. The template is stale;
> flagged for the owner for the third task running, and not followed.

## Files changed

**Production (3 files, +52 / −6)**

| file | change |
|---|---|
| `frontend/src/i18n/catalogue.ts` | one key, `register.passwordGuidance` — a **template**, not a sentence |
| `frontend/src/app/ui/PasswordField.tsx` | one prop, `'aria-describedby'?: string`, forwarded to the `<input>` |
| `frontend/src/features/account/RegisterScreen.tsx` | `GUIDANCE_ID`, the interpolated lookup, and one condition driving the mark and the sentence together |

**Tests (3 files, +544 / −2)**

| file | change |
|---|---|
| `…/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` | **new**, 19 cases — the named test from `package.md` §8 |
| `…/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | one line — the new key joins the reachable-keys list |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | the catalogue key-set pin admits the key (**deviation D-1** — the freeze said this file would not move) |

**Pipeline artifacts:** `.ai/prompts/frontend/T45/artifacts/` — phases 00–13, the 48-row harness, and five
run logs (`07`, `09-1`, `09-2`, `10`, `12`).

## Summary

T45 renders the only clause of R1 that had never reached a screen. The check and both its bounds were
already live; what was missing was the guidance the requirement names, and the association without which a
message about a field is a sentence some visitors never meet.

Three design decisions carry it. The copy **names the rule and delivers no verdict**, because an advisory
check has no standing to deliver one — which also means a single string serves both ends of the range. The
bounds are **interpolated from the module that enforces them**, so the sentence cannot drift from the check;
both ends of that wire are pinned in source, because the DOM cannot see where a number came from. And the
sentence is **described on the field, not announced** in the live region, which is immediately overwritten
by "Creating your account…" — an accepted residual risk (D-T45-1) rather than an oversight.

## Testing performed

| | |
|---|---|
| named suite | **19 / 19** |
| whole suite | **1964 / 1965** — one known environmental failure (`lintRejectsHandWrittenBackendFetch`, this host's `oxlint` prints no summary line) |
| `tsc --noEmit` · `oxlint src` | **0** · **0** |
| mutation harness | **48 rows**, run four times across phases 9, 10 and 12 — **44/44 regressions killed**, 3 canaries killed, 1 expected survivor (TR24, unfalsifiable in jsdom), 0 broken, 0 disagreements, verdicts identical in every run |
| harness conditions | local Docker stack **stopped** for every run (FU-T42-1 is load-sensitive); tests committed before each run, because the harness reverts `frontend/src` |

Every acceptance criterion is held by a named case, and every case by a mutant that kills it and little
else: TR44 dies in the source scan alone, TR46 in the association case alone, TR48 in the persistence case
alone, TR47 in the 11-astral row and task 43's 7-emoji row.

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` task **45** |
| **Requirements** | **R1** (12–128 code points, advisory only, server is the authority); **R2** adjacent and undisturbed |
| **LOCKED decisions** | **L11** (WCAG AA, catalogue-only strings) · **L12** (additive) · **L4** (enumeration-safe) · **L6** (generated client) · **L2/L3/L14** (no credential surface, storage or route) · **T173 rules 1–3** · **T44's AC4** |
| **Named tests** | `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints` (new) · `registrationAcceptsTwelveAndOneTwentyEightCodePoints` (one line) |
| **Decisions** | D-T45-1 (described, not announced) · D-T45-2 (one string naming the rule) · D-T45-3 (association measurable in the DOM) |
| **Deviations** | **D-1** the design-system key-set pin had to change (phase 3's F9 said it would not) · **D-2** the copy scan asserts the sentence, not its words |

## Open for the owner

| | |
|---|---|
| **OQ-T45-5** | Should the guidance clear when the visitor edits the password, or stay until the next submit? It stays today, matching what task 43 chose for the mark. Pinned by a case; TR48 is the mutant that becomes the fix. |
| **OQ-T45-4** | AC4's email-field exemption is enforced by the type system, not a mutant — `EmailField` accepts neither prop, so the mutant would not compile. |
| **FU-T42-1** | Fifth sighting. Phase 9 measured the mechanism: it can only invert a row with no genuine kill of its own. Cause in code still unknown. |
| **FU-T43-2** | Third sighting. No shared test-util module; ~120 lines of helpers copied again, and the copies have begun to diverge. |
| **Carried** | OQ-T44-1 · OQ-T43-6..11 · the stale phase 13 trailer · `/app/verify` renders blank until task 48 |

**Phase 13 complete. T45 is ready to merge.**
