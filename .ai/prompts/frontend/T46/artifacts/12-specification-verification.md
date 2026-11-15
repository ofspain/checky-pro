<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T46 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T46 — Code points, not UTF-16 |
| **Model** | Claude Opus 5 |
| **Consumes** | every prior artifact, `artifacts/11-test-review.md` last |
| **Produces** | `artifacts/12-specification-verification.md` |

## Phase 11 findings — resolution

Seven findings. Three accepted as code, one accepted in a stronger form than it was put, one declined with
evidence, two positives needing nothing.

### F1 — the harness is not crash-safe against SIGTERM · **ACCEPTED, mechanism corrected**

The recommendation is right and is implemented; the stated mechanism is not. F1 says *"`revert()` happens
only after all rows finish"*. It does not — the per-row `try/finally` has been there since phase 9
(`07-mutation-harness.py:851-857`). What a default SIGTERM does is terminate **without unwinding**, so the
`finally` never runs. Measured rather than argued, with a probe that sleeps inside a `try/finally`:

| Signal | Exit | Marker after |
|---|---|---|
| SIGTERM | `-15` | `MUTANT LIVE` — the `finally` did not run |
| SIGINT (Ctrl-C) | `-2` | `REVERTED` — `KeyboardInterrupt` unwinds, so phase 9's Ctrl-C claim was right |

My first probe sent SIGINT to a shell background job and reported `MUTANT LIVE` for it too, which was
wrong: a shell without job control starts background jobs with SIGINT set to `SIG_IGN`, so nothing was
delivered and the process was simply still sleeping. The table above was measured from a Python parent,
where both signals have their default disposition.

Fix: `install_signal_reverts()` registers a handler for **SIGTERM and SIGHUP** that reverts and then
`os._exit(128 + signum)`. `os._exit` is deliberate — letting `SystemExit` propagate would revert a second
time through the `finally`, out of a signal handler, while a vitest child is still running. Verified end to
end by launching the harness, waiting until `git status` showed `passwordLength.ts` modified, sending
SIGTERM, and reading the tree afterwards:

```
mutant live on disk: 'M frontend/src/features/account/passwordLength.ts'
exit code: 143
  SIGNAL SIGTERM: reverting the live mutant before exit.
tree after SIGTERM: CLEAN
```

SIGKILL still cannot be covered by anything, which is what `dirty()` is for on the next run. F1's own
evidence — a concurrent foreground suite seeing 46 failures — is not evidence of this defect: that is what
running a suite *during* a mutation run looks like, and it is why the standing rule is not to.

### F2 — the AC6 control does not exercise every category · **ACCEPTED, and then one step further**

Confirmed by reading `ENVIRONMENT`: seven categories declared, five exercised. `network` and `platform`
were never shown firing, so `/\bfetch\s*\(/`, `/\bcrypto\b/` and `/\bprocess\b/` could all have matched
nothing with **both** AC6 cases green — the first case passes because `passwordLength.ts` does not reach
for those either. Three goes at this control, the same mistake each time, one level up: phase 7 asserted
that five patterns fired (a count), phase 9 asserted that those five **kinds** fired (a named set), and
neither asked whether five was all the kinds there are.

F2's recommendation — a sample line per kind, and assert seven categories — is not enough, and this is the
part the finding did not reach. **A category check cannot see one broken pattern with a working sibling.**
Blunt `/\bcrypto\b/` and `platform` still fires through `/\bprocess\b/`, so the fired set is unchanged. The
thing that breaks is a *pattern*. The control now asserts that **all twelve patterns fire** on the sample,
by `pattern.source`, so a failure names the one aimed at nothing; the seven kinds are pinned separately,
because a per-pattern check is satisfied by a shrinking list too.

Measured, not assumed, in both directions:

- **TR60** blunts `/\bcrypto\b/` — the pattern with a working sibling — and dies in exactly one case.
- **TR61** blunts `/\bfetch\s*\(/`, the only pattern in `network`, and dies in exactly one case. It is the
  contrast: F2's own fix would have caught this one, so having both rows measures the choice of unit
  instead of arguing it.
- **TR60 applied to the pre-phase-11 suite passed 27/27** — the hole was real, and it was real twice over
  (no `crypto` line in the sample, and `platform` absent from the expected list).

### F3 — the circularity regex is still formatting-sensitive · **DECLINED, with evidence**

`/const CASES[\s\S]*?\n\];/` is punctuation-sensitive, which F3 states correctly and calls optional. It is
declined because the failure mode is **fail-closed and already named**: `exec(...)?.[0] ?? ''` yields an
empty region, the row-count guard then compares 0 against `CASES.length`, and the case fails with *"the
extracted region does not hold every row"*. A formatting change therefore cannot make this control pass
while scanning a fragment — the outcome F3 is protecting against. A parser-aware scan would add a
dependency and a second way to be wrong for no measured gain.

### F4 — `Intl.Segmenter` assumed present · **ACCEPTED as an assertion, declined as a skip**

Both halves of F4's recommendation were on the table and they are not equivalent. A skip would quietly
remove the only number in the file that nobody here chose — T174's lesson that a gap recorded is not a gap
closed, with the recording automated. The case now states the assumption:
`expect(typeof Intl.Segmenter).toBe('function')` with a message naming the missing API. Measured while
resolving this: the package pins **no** runtime — no `engines` field in `frontend/package.json`, no
`.nvmrc` — so F4's premise about a widened matrix is live, and a loud failure is the right answer to it.

### F5, F6, F7 — observations and positives · **no action**

F5 agrees with phase 10's own recording that case 24 has no mutant. F6 and F7 confirm TR57–TR59's
isolation and that phase 8's I1 and I2 are closed. Nothing to do.

---

## Traceability matrix

Scoped requirement: **R1** — *"the SPA SHALL check that the password is 12 to 128 Unicode code points as
advisory guidance only"* (`spec/frontend/requirements.md:9-11`). Task 46 verifies the **counting unit**
of that sentence; the screens that consume it were tasks 43–45.

| Requirement / AC | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R1** — count is in **Unicode code points** | Yes, by task 43 | `features/account/passwordLength.ts:19-21` | **Yes** — the whole named suite | No | No |
| **R1** — the bound is **12 to 128** | Yes, by task 43 | `passwordLength.ts:24-25` | **Yes** — `…test.ts:350` | No | No |
| **R1** — **advisory only**, the server is the authority | Yes, by tasks 43–45 | `passwordLength.ts:16-17`, screens out of scope | **Yes** — totality, `…test.ts:223`: a count that threw would be a gate | No | No |
| **AC1** — astral is 1 to the count, 2 to `.length` | n/a (test-only task) | — | **Yes** — `…test.ts:96` ×15 rows | No | No |
| **AC2** — the units disagree **across both bounds** | n/a | — | **Yes** — `…test.ts:159` (11 cp / 22 units), `:168` (128 cp / 256 units) | No | No |
| **AC3** — expectations stated **independently of the implementation** | n/a | — | **Yes** — literals at `…test.ts:56-93`; controls at `:104` and `:133`; the arithmetic property at `:252` | No | No |
| **AC4** — the function is **total** | Yes, by task 43 | `passwordLength.ts:20` | **Yes** — `…test.ts:223`, `:236`, and three lone-surrogate rows | No | No |
| **AC5** — a **grapheme cluster is not one code point** | n/a | — | **Yes** — `…test.ts:178` (2, 2, 5) and `:193` (the ordering, against `Intl.Segmenter`) | No | No |
| **AC6** — depends on its **argument alone** (R1, **L3**) | Yes, by task 43 | `passwordLength.ts:19-21` — no import, no global | **Yes** — source scan at `…test.ts:299`, control at `:311` | No | No |
| **AC7** — **three exports**, bounds are 12 and 128 | Yes, by task 43 | `passwordLength.ts:19,24,25` | **Yes** — `…test.ts:350` | No | No |

**Named test from `package.md` §8:** `passwordLengthCountsCodePoints` →
`frontend/src/features/account/passwordLengthCountsCodePoints.test.ts`. The package's other two named
tests in this header (`registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints`,
`registrationAcceptsTwelveAndOneTwentyEightCodePoints`) were delivered by tasks 45 and 44 and are
unchanged by this task; both still pass in the whole-suite run.

## Verification

| | |
|---|---|
| named suite | **27 / 27** |
| whole suite | **1991 / 1992** — the one known environmental failure, `lintRejectsHandWrittenBackendFetch.test.ts` (this host's `oxlint` prints no summary line) |
| `tsc --noEmit` · `oxlint src` | **exit 0** · **exit 0** |
| harness | **61 rows**: **54 / 54 regressions killed**, 4 canaries killed, 2 expected survivors (TR24, TR50), **0 broken**, **0 disagreements** |

`artifacts/12-mutation-run.txt`. Stack stopped, tree committed before mutating. **Verdicts identical row
for row to phase 10's run** for all 58 rows that existed then; TR60 and TR61 are the only new verdicts.
Sixth full run of this harness.

## The four questions

**1 · Is the task fully complete?** Yes. `tasks.md:80` asks that length be counted in Unicode code points
and names `passwordLengthCountsCodePoints`; that suite exists, is named exactly that, and holds the claim
in the module where the count lives rather than only in the screens that read it. Every AC in the frozen
brief has at least one case, and the two the task exists for — AC2 and AC5 — have the cases no other task
could have written.

**2 · Does it satisfy every acceptance criterion?** Yes, AC1–AC7, with the matrix above as evidence. Two
criteria are held by instruments rather than by behaviour and both are recorded as such: AC6 is a source
scan (because T42 measured that a negative behavioural assertion about an absent side effect is vacuous),
and AC7's `typeof` clause is held by the type system (phase 8's I10).

**3 · Does it violate any LOCKED decision?** No. **L3** (tokens in memory only) is the one in range, and
AC6's scan is stricter than L3 needs: the module reaches for no storage at all. **L6** (generated client
only) is not engaged — nothing here calls a backend. **L12**'s prohibition on rewriting an earlier task's
work is the reason this task changed **no production file**: `passwordLength.ts` is its read-only subject,
and the one place where the task could have been tempted — OQ-T46-2, normalisation — is pinned as it
behaves and left to the owner rather than decided here.

**4 · Remaining risks**

| | |
|---|---|
| **OQ-T46-2** | Is R1 meant to be normalisation-sensitive? A composed `é` is 1 code point and a decomposed one is 2, so the same password typed on two keyboards is two lengths. Held by a table row and by TR52 — whichever way the owner answers, a test changes. |
| **OQ-T46-3** | `Intl.Segmenter` is available in this runtime and used nowhere in `src`. If R1's visitor-facing "characters" copy (task 45) is ever meant to count what a visitor sees, the instrument exists — and OQ-T46-2 follows from the same decision. |
| **Case 24 has no mutant** | The composition property is weight-bearing as a law, not as a mutant killer. Recorded in the case's own comment and in phase 11's F5. |
| **SIGKILL** | Still uncoverable. `dirty()` refuses the next run on a tree a crash has edited; SIGTERM and SIGHUP now revert. |
| **OQ-T32-4** | Sixteenth copy of the harness machinery, now 61 rows for a three-line module. Not this task's business to trim. |
| **FU-T42-1** | Quiet through all three of this task's runs with the stack stopped. |
| **Pipeline record** | The commit carrying the phase 10 artifact is titled with phase 9's subject line; `10-test-generation.md` records it, because the force-push to correct it was refused. |

---

**PASS** — R1's counting unit is now held by the module's own suite across both bounds, both neighbouring
units and a witness from outside the file, with 54/54 harness rows killed, 0 disagreements, and no
production file changed.
