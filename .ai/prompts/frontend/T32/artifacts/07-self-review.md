<!-- MODEL: Claude Opus 5 — Phase 7 (Self-Review) -->

# frontend · T32 · Phase 7 — Self-Review and Mutation Score

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |

## Mutation score: 11 of 11, no survivors, none broken

```
baseline whole suite: 1 failed of 1529, exit 1
baseline T32 cases:   0 failed of 20, exit 0

  KILLED    own            MR01 the decoding file reads email_verified off the payload it already has  (5)
  KILLED    own            MR02 the same read, in the camelCase spelling  (3)
  KILLED    own            MR03 a second production file starts decoding a token  (7)
  KILLED    own            MR04 a module names email_verified without reading /userinfo  (3)
  KILLED    own            MR05 the permitted decoder stops decoding  (154)
  KILLED    own            MR06 the SPA stops requesting the email scope  (6)
  KILLED    own            MR07 AccountResponse stops REQUIRING emailVerified  (4)
  KILLED    own            MR08 the contract's disclaimer is deleted  (3)
  KILLED    own            MR09 PENDING_VERIFICATION leaves the status enum  (5)
  KILLED    own            MR10 the decoding file LOGS the claim  (12)
  KILLED    INHERITED-ONLY MR11 a verification flag is cached in sessionStorage (T31 owns this kill)  (6)

survivors: none
broken:    none
```

**MR11 came back `INHERITED-ONLY`, which is what it was written to check.** Caching verification state is
T31's store-emptiness assertion, not anything of T32's, and the row exists to say so out loud. If that column
ever reads `own`, a T32 case has grown a reach it was not given.

| | |
|---|---|
| T32's cases | **20 / 20** |
| Whole suite | **1528 passed / 1 failed (1529)** — the known environmental one |
| `tsc --noEmit` | exit 0 · `oxlint src` exit 0 |

## Findings

### S1 — `USERINFO_READ` is a literal call-shape match, and it is the first thing I would attack

```ts
const USERINFO_READ = "GET('/userinfo'";
```

AC2 decides a file's *channel* by looking for that exact text. A legitimate `/userinfo` read written any
other way — through a path constant, a wrapper, a different quote style, a formatter that breaks the line —
would be judged **off-channel and flagged**, and the next author's correct code would fail a test owned by
this one. That is the same failure mode T31 had to repair three times in other tasks' instruments, pointed
the other way.

I left it literal rather than loosening it, because a looser channel test (any mention of `userinfo`) would
let a file claim the channel in a comment or a variable name while reading the claim from a token. But the
trade is real and this is the weakest line in the file. **Kimi should attack this first.**

### S2 — AC2 covers only the OIDC spelling, by design, so the broader claim is not held

G3 narrowed AC2 after Kimi's phase 3 finding 8, and the narrowing has a cost worth stating plainly: a
tree-wide mention of **`emailVerified`** outside `readAccount.ts` is **not** caught. It cannot be — that is
the `AccountResponse` field name, the *correct* source, so a rule that flagged it would flag T31's
commissioned read.

Consequence: a component keyed on a verification flag named `emailVerified`, whose value was read off a token
somewhere else and passed along, passes everything here. **No mutant covers this, deliberately** — a mutant
for it would be a designed survivor, and a harness that reports a survivor it intends to keep teaches the
reader to ignore the survivor list. The limit is in the file's header instead (AC8) and in the artifacts.

### S3 — the task's real risk is its value, not its correctness

Ten of eleven mutants are killed by T32's own cases and the scans are sound. But **nothing commissions the UI
this protects** (OQ-T32-1). Tasks 48–51 are the verification *link* flow, which never touches a claim. So
this is a forward guard, and its worth depends on a consumer being written later and being written wrong.
I have stated that in the file rather than letting the mutation score imply more than it does. If the owner
decides no signed-in verification surface will exist, the honest read is that T32's standing value is the
**contract pins** (AC3, AC4) and the decode bound (AC1), not AC2.

### S4 — MR05 kills with 154 failures, which is a coarse signal

Breaking the nonce decode fails ~150 tests across the suite, so the kill says little about T32's own case
beyond the `own` column confirming at least one T32 case fired. A mutant that breaks a third of the suite is
not discriminating. I kept it because the allowlist row's **lower** bound is a real property — a row that
outlives its decode is how an allowlist stops being evidence — but the score it contributes is close to free.

### S5 — AC3's `T32` citation is the most brittle assertion here

AC3 asserts the `/userinfo` field description contains `NOT the access`, `token-claims.md` and `T32`. A
meaning-preserving rewording of that contract prose would fail it. I think that is the right trade — the
contract line is load-bearing for this whole task and MR08 proves the pin fires — but a reword is a plausible,
innocent event, and the failure message should probably name the artifact to re-read rather than only the
missing phrase. **Unresolved, and worth Kimi's opinion.**

### S6 — AC8 asserts the file's own prose, which is a word game if it rots

The limitation case greps this file's own header for four phrases. It protects the honesty paragraphs from
quiet deletion, which is why it is there. But it couples the file to its own wording: a rewrite of the header
must update the phrase list, and a reader could satisfy it by scattering the four words without the meaning.
A self-referential assertion is the weakest kind of guard, and it is doing real work here only because the
thing it protects is a disclosure rather than a behaviour.

### S7 — one case will need an edit by a future author, and says so in its title

`'and today nothing names it at all, which is the stronger statement while it is true'` fails the day a
legitimate `/userinfo` consumer names the field — even though AC2's rule *permits* that file. That is
deliberate: while it is true it is the strongest form of the claim. The title carries the warning; whoever
writes that consumer deletes this case and keeps the rule.

### S8 — the harness mutates contracts shared with the backend

Four mutants edit `contracts/api/*.yaml`, which the Java services also consume. `revert()` restores them and
runs **before** preflight as well as after each mutant, so an interrupted run self-heals on the next start.
T30 carried the same exposure with the same mitigation. Still worth knowing before running it on a dirty
tree: **commit first**, as the docstring says.

## What I would have Kimi look at, in order

1. **S1**, the literal `USERINFO_READ` channel match, and whether a better discriminator exists that does
   not open the comment-claims-the-channel hole.
2. **S2**, whether the camel-spelling gap should be closed by some other means, or accepted and documented.
3. **S5**, the brittleness of pinning contract prose by phrase.
4. Whether **AC1's `TOKEN_DECODE`** is still narrow enough now that a fourth file reads it — the pattern is
   copied across T30, T31 and T32, and a drift between copies would be invisible (OQ-T32-4).

**Phase 7 complete.** 11 of 11 mutants killed, eight findings, three of them limits I chose rather than
defects I missed.
