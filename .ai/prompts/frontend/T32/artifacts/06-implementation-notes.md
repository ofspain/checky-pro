<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation) -->

# frontend · T32 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

**One new file, no production code** (G7):
`frontend/src/auth/emailVerifiedClaimIsNotAccountState.test.ts` — **20 cases**.

| Block | Cases |
|---|---|
| AC1 the decode bound | 6 |
| AC2 the channel rule | 5 |
| AC3 the truthful sources | 3 |
| AC4 the premise | 2 |
| AC7 the surface sweep as a dirty-proof | 3 |
| AC8 the limitation, asserted | 1 |

## What the file does, and the two things it refuses to claim

The header carries the task's argument and both of its limits, because AC8 asserts they are there — a test
asserts the header still says "source-side", "not data-flow guards", "forward guard" and "OQ-T32-1", so the
honesty cannot be deleted without a failure.

- **source-side, not data-flow.** The scans catch a module *reading* the claim. They cannot trace a value
  read off a token, renamed to `isEmailVerified`, and passed along as a prop.
- **a forward guard.** No consumer is commissioned (OQ-T32-1), so most of this fires on code that does not
  exist yet.

## Two defects found by running it

**1. `document.head.innerHTML` deletes the `<title>` I had just set.** The first run reported **six** of seven
surfaces. `document.title = x` writes a `<title>` element *into the head*, so assigning `head.innerHTML`
afterwards removes it and the title sentinel vanished. The fix is ordering — head first, then title — and the
reason is now a comment, because the next person to add a surface will reach for the same order.

This is T30's coupling seen from the other side. T30 learned that a *title* sentinel is also found in
`head.innerHTML`, so a single sentinel could not tell the two surfaces apart; here the same coupling deletes
one. Both lessons point the same way: **every surface needs a sentinel of its own.**

**2. A failing case leaked its URL into the next one.** The dirty case set `?email_verified=true` and reset it
on its last line, which never ran once the case failed — so "the surfaces are clean" then failed too, for a
reason that had nothing to do with it. The reset moved into `afterEach`. A cleanup that lives at the end of
the case it dirties is a cleanup that does not run exactly when it is most needed.

## Decisions taken while writing

- **`TOKEN_DECODE` stays narrow** and keeps T30's reason-comment. The five decode forms are applied only to
  the *named* file's accounting. `JSON.parse` matches every response body in the tree and `split` matches
  ordinary string work, so a broad pattern would turn AC1's bound into noise.
- **AC1 scans both spellings; AC2 scans only the OIDC one.** Inside a decoding file the channel is already a
  token, so spelling carries no information and excluding either would be a free gap. Tree-wide, only the
  OIDC spelling can be ambiguous — the camel spelling is the `AccountResponse` field, which is *the correct
  source*, and flagging it would be flagging T31's commissioned read.
- **A control proves the two spellings do not match each other.** They are one character class apart, and the
  whole instrument depends on the camel one not being caught by a tree-wide snake-case rule.
- **AC2's "silent" controls are two, not one** (Kimi finding 9): the rule fires on a mention with no
  `/userinfo` read, stays silent on a mention in a file that reads it, **and** stays silent on a verification
  **link** module. The admission rule is exercised rather than described, which is what an empty permitted
  set needs instead of a lower bound.
- **AC2 also asserts the stronger present-tense fact** — nothing in the tree names the claim at all — and
  names `profile/readProfile.ts` as the file the rule *would* permit, so the rule is not hypothetical.
- **AC3 parses YAML stanzas rather than grepping** the whole contract, so a match cannot come from a
  neighbouring schema.
- **AC4's failure messages say "re-read T32, do not patch this assertion."** If the contract's documented
  semantics change, this task's premise is gone and patching the assertion would hide that.

## Gates

| | |
|---|---|
| This file | **20 / 20** |
| Whole suite | **1528 passed / 1 failed (1529)** — `lintRejectsHandWrittenBackendFetch`, environmental, pre-existing |
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |

No production file was touched. No contract, no `spec/`, no config.

**Phase 6 complete.** Twenty cases, two defects found by running the file, both fixed with their reasons
recorded.
