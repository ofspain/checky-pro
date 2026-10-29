# frontend · T24 · Phase 11 — Test Review Resolution

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Human Approval gate |
| **Consumes** | `artifacts/11-test-review.md` (Kimi 2.7) |
| **Produces** | `artifacts/11-test-review-resolution.md` |

**Every claim in the Phase 11 artifact was checked against the committed test source before this gate decided
anything.** All six gaps are factually correct about what the file did and did not assert. Kimi ran static-only
again (its environment cannot start `vitest`), so the citations were the thing to verify.

**Six accepted, one of them resolved the opposite way round from the recommendation**, and no production code
changed. Five gaps became five new cases and two new assertions on existing ones; Gap 6 became a *narrowing* of
the module-scope scan rather than the widening Kimi asked for, with the reason recorded below.

**Then the mutation harness earned its keep twice over.** Five new mutants were added, one per new case, to prove
each is load-bearing rather than decorative — and two of them **survived**, both because *my new cases were
vacuous*, neither because production was wrong. Both are repaired and both mutants now die. That is the second
time in this task that writing the mutant before believing the case has caught a test that could not fail.

**Final state.** Named test **67 cases + 15 `it.todo` rows**. Suite **1075 passed / 15 todo / 1 failed** by exit
code, the failure being the known environmental case (`lintRejectsHandWrittenBackendFetch.test.ts:118` — this
host's `oxlint@1.87.0` prints no summary line). `tsc --noEmit` exits 0. Mutation: **25 of 25 detected, 24 KILLED
by assertion and 1 by collapse, no survivors.**

---

## Resolution log

### Gap 1 · The fallback navigation's `replace` was unasserted — **ACCEPTED**

Verified: the `replaces the callback entry` case used the honoured target, and the AC9 indistinguishability case
compared pathname, search and rendered text only. Kimi's reasoning is exactly right — `replace` is part of the
outcome, not a detail of the honoured path, and a fallback that pushed would leave a consumed callback reachable
with the back button while all three compared fields still agreed.

**Change:** `historyAction` joined the AC9 comparison object on both sides, plus an explicit assertion that the
shared value is `REPLACE`. **Mutant M21** (`replace: target !== '/home'`, so only the honoured path replaces)
kills on 32 cases.

### Gap 2 · A throwing write was only asserted not to throw — **ACCEPTED, then repaired**

Verified: the unreachable-store case asserted `rememberReturnTarget` does not throw and nothing more.

**Change:** a new case, `leaves the store exactly as it was when the write itself throws`.

**And then M23 survived it.** The first version stubbed `Storage.prototype.setItem` to throw for **every** key,
which makes the claim unfalsifiable: under that stub nothing can land in the store under *any* implementation, so
the assertion was true by construction rather than by the code's behaviour. The stub now refuses **only** the
return key, so a retry under a sibling name or a fall back to `localStorage` is observable, and the case asserts
the whole of both stores instead of one key. **M23** is now `catch { setItem(`${RETURN_TARGET_KEY}.pending`, …) }`
and dies on exactly the one case.

### Gap 3 · No discard case reached the absent-target path — **ACCEPTED**

Verified: all seven `FAULTS` rows call `remember(A_REAL_TARGET)` first, so the absent-target path was exercised on
the success side only.

**Change:** a new case, `leaves the store untouched when a discard had no target to begin with` — the discard
lands on sign-in and the key was neither written nor removed. **Mutant M22** (a discard removes the return key)
kills on 30 cases; before this gate it would have been caught too, but by the *honoured* rows rather than by the
path it breaks.

### Gap 4 · Overwriting an existing target was untested — **ACCEPTED**

Verified: no case called `rememberReturnTarget` twice with different values.

Kimi rates this polish, and it is slightly more than that. `setItem` overwrites by definition, so the case is not
a test of the platform — it is the contract T41 depends on when a visitor abandons one journey and starts another
in the same tab, and it is the reason the retry case cannot pass by accident for reusing one target.

**Change:** a new case, `replaces an earlier target rather than keeping or appending to it`. **Mutant M24** (the
producer returns early when a target is already stored) kills on 2 cases.

### Gap 5 · Whitespace-padded targets were untested — **ACCEPTED, then repaired**

Verified: the corrupted-value table held `' '` and `''` but no padded path.

**Change:** rows added to the table. Nothing trims them, and nothing should — a trim is a normalisation, and not
having one is the single thing the membership rule buys.

**And then M25 survived it.** Kimi's suggested rows were `' /home'` and `'/home '`, and I used them as given.
They cannot fail: `' /home'` trimmed is `/home`, which **is the fallback**, so an implementation that normalised
before validating returned precisely what the row asserted. The padded value is now `/account/sessions`
(plus a tab-padded row), which only a normalising implementation can return, and only the home path can come
back from a correct one. The two `/home`-padded rows stay as shape. **M25** (`isAllowlistedTarget(stored.trim())`)
now dies on 3 cases.

This is the one place where a Phase 11 suggestion was wrong in its particulars while right in its substance, and
it is recorded rather than quietly fixed, because the lesson is reusable: **a boundary case whose expected answer
is the fallback cannot distinguish a wrong answer that is also the fallback.**

### Gap 6 · The scan's treatment of a re-export was undecided — **ACCEPTED as a question, DECLINED as proposed**

Verified: the scan excludes lines beginning with `import`, so
`export { AUTHENTICATED_ROUTE_PATHS } from '../app/routes';` would match the identifier at depth 0 and be
reported. Kimi's observation is correct and worth deciding. Its suggested assertion — that a re-export *should*
be reported — is the part that is declined.

**A re-export names the binding and never reads it.** `export { X } from './y'` and `export { X }` are wired up
by the module graph and never evaluated, so neither can touch the temporal dead zone this scan exists to keep out
of. Reporting one would be a false positive in a guard whose whole value is that it fires only on the thing that
**fails closed silently** — and a guard that cries wolf is a guard the next person in a hurry loosens. So the scan
**narrows**: re-export forms join the import exclusion.

The distinction is pinned by the pair that keeps it from drifting: a re-export passes, and
`export const EAGER = new Set(AUTHENTICATED_ROUTE_PATHS);` does **not** — because an exported module-scope read
is still a read, and it is the shape that would actually break production.

**Change:** `moduleScopeUsesOfTheRouteTable` excludes `^export\s*\{…\}\s*(from '…')?;?$`; two controls added.

---

## What changed

| File | Change |
|---|---|
| `auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | +5 cases (59 → 67 with the new `it.each` rows), +2 assertions on existing cases, scan narrowed, 2 inert cases repaired |
| *(production)* | **nothing** |

## Mutation harness — final

Twenty-five mutants, baseline **524 passed, exit 0** over the six auth test files. Script:
`/private/tmp/claude-501/-Users-macbookpro-personal-crypto-dispute/d6c7daa6-f34e-40ce-afe5-23ce45bf781e/scratchpad/mutate-t24.py`.
Tests were committed and pushed before each run, because it reverts with `git checkout`.

M1–M20 as Phase 10 recorded them, all KILLED except **M13** (module-scope allowlist), which is **detected by
collapse**: the identifier is `undefined` inside its temporal dead zone, `undefined.filter` throws at import, and
the file never collects — `exit 1`, no tests run. The harness scores an unparseable run as BROKEN rather than
KILLED, which is the right rule, so it is reported and not counted.

| Phase 11's five | Verdict | Killed by |
|---|---|---|
| M21 only the honoured navigation replaces | KILLED (32) | Gap 1's assertion |
| M22 a discard removes the return key | KILLED (30) | Gap 3's case |
| M23 a refused write retries under a sibling key | KILLED (1) | Gap 2's case, alone |
| M24 the producer keeps the first target | KILLED (2) | Gap 4's case |
| M25 the stored value is trimmed before validation | KILLED (3) | Gap 5's repaired rows |

## Still open, and not this task's

The five unowned pieces in the package: the composition root (`mountShell.tsx`), silent re-authentication, a
real-browser end-to-end check (`oidcRoundTripEndToEnd`, no Playwright suite, no owning task), and — added by this
task — a `/app/home` screen (T24-D11), the blank route this task's fallback navigates to. T25 owns the 15
rejection rows. Task 26 owns the two-constant storage carve-out (T24-D4).

---

## Next — Phase 12 — specification verification.
