# frontend · T24 · Phase 10 — Test Generation

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

`frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` — **59 cases and 15 `it.todo` rows**,
the named test's T24 half. No production code changed in this phase.

**Suite: 1067 passed, 15 todo, 1 failed** by exit code, the failure being the one known environmental case
(`lintRejectsHandWrittenBackendFetch.test.ts:118` — this host's `oxlint@1.87.0` prints no summary line).
`tsc --noEmit` exits 0. Three consecutive full runs gave identical counts, so no flake.

**Mutation score: 20 of 20 detected, 19 KILLED by assertion and 1 by collapse.** No survivors. The mutants are
the allowlist and the fallback, as the Phase 5 plan required, plus three that revert Phase 9's accepted changes —
because a fix nothing tests is a fix that comes back.

---

## 1 · The two risks this file was written against

**Vacuity.** "Landed on home" is satisfied by a flow that never navigated at all, if home happens to be where it
started. "Landed on the target" is satisfied by a harness that can reach no other route. So every destination case
asserts the destination **and** that the other destination was reachable from the same harness:

- the honoured case watches the router **sit on `/app/callback`** with the exchange in flight, releases the
  response, and only then asserts the target — which is AC1's "would otherwise have stayed" control stated as a
  measurement rather than remembered from T21;
- `remember()` refuses to continue if the store did not take the value, so no honoured case can pass against a
  target that was never remembered;
- `startSignIn()` refuses to return a triple it cannot read back, which is T23-D13's control inherited;
- the derivation case proves a path **added** to the route table becomes legal, not that today's list matches
  today's list.

**Tautology in the derivation.** `EXPECTED_ALLOWLIST` is computed in the test file from `AUTHENTICATED_ROUTE_PATHS`
by an independently written filter, and the `it.each` over the whole route table asserts the predicate's answer
equals "has no parameter segment" per entry. A list written out by hand would have been the second list AC6
forbids.

## 2 · Test manifest

### `an allowlisted target is honoured (AC1, R7, L1)`

| Case | Verifies |
|---|---|
| navigates to the remembered target, from a callback it would otherwise have stayed on | **AC1**, R7, L1 — with the in-flight `/app/callback` control |
| honours `%s`, the whole way through a sign-in — 9 rows, the derived allowlist | **AC1**, **AC6**, **AC7** (the key is gone after each) |
| replaces the callback entry rather than pushing the target on top of it | R8's `replace`, asserted through `historyAction` |

### `no target at all goes home (AC3)`

| Case | Verifies |
|---|---|
| goes home, and removes nothing on the way | **AC3**; and the conditional removal, from the side T23's three inherited assertions see |
| is indistinguishable from a target the allowlist refused | **AC3**, **AC9** — same pathname, same search, same rendered text |
| refuses a target carrying the basename, by membership and nothing else | **AC2**, **T24-D8** (direct) |

### `the target is single-use (AC7, L16)`

| Case | Verifies |
|---|---|
| reads the target before deleting it, and deletes it exactly once | **AC7** as an ORDER, through the storage log |
| does not hand the target to the next sign-in | **AC7**'s point; two different tokens, so neither success is a constant that matched |
| still honours the target when the removal throws, and says so about what is left behind | **AC16**, **T24-D9** |
| consumes a target it refuses, rather than leaving it for the next attempt | **R-I6** (Kimi I6) |
| returns the target and empties the store in one call | **AC7** direct; a second call gets the fallback |

### `no discard honours a target (AC4, T24-D14)`

| Case | Verifies |
|---|---|
| sends `$label` to sign-in and leaves the target where it is — 7 rows | **AC4**, **T24-D14** |
| and the retry then honours it | **R-I3** (Kimi I3): the use is the arrival, not the attempt |

The seven shapes are T23's: no state, a mismatched state, no code, an authorization error, a refused exchange, a
dropped connection, and a nonce this tab never generated.

### `the allowlist is derived, not written (AC6, AC17)`

| Case | Verifies |
|---|---|
| answers for `%s` exactly as the route table implies — 11 rows, every entry | **AC6** forward |
| admits a path added to the route table, with no edit of its own | **AC17**; the route table is replaced and the fresh instance checked for being fresh |
| refuses a parameterised route while its plain parent stays legal | **T24-D5**, and **R-I4**: the invented table carries `time/12:30`, `reports/:id` and `:secret` |
| admits exactly the derived set and nothing beside it | **AC6**: no second list |
| reads the route table only inside a function, never at module scope | the TDZ trap, with **three** controls |

### `when the store will not cooperate (AC10, AC15, T24-D10)`

| Case | Verifies |
|---|---|
| is guarded because the discard handler could not catch it anyway | **T24-D10**'s premise, measured here rather than cited from T21 |
| returns the home path for the stored value `%j` without throwing — 9 rows | **AC15** |
| never throws when the store itself is unreachable | **AC15**, **T24-D10**; the producer too |
| completes the sign-in and goes home when the target cannot be read | **AC10** end to end |

### `a visitor who left is not navigated (AC14, T24-D7)`

| Case | Verifies |
|---|---|
| does not navigate after the screen unmounts, and consumes the target anyway | **AC14**, **T24-D7**, and **R-I1** (Kimi I1) — the two promises pulled apart |
| still navigates under StrictMode, where the cleanup runs between the passes | the StrictMode ordering defect, as a case |

### `nothing else enters storage (AC8)`

| Case | Verifies |
|---|---|
| adds one key and no secret to a flow that remembers a target | **AC8**: four keys, and no token or code among the values |
| does not store a target the allowlist would refuse | **R-I2** (Kimi I2), **T24-D15**; a legal one is the control |
| round-trips a legal target through the producer and the consumer | the contract T41 will call |

### `a target outside the allowlist falls back to home (T25)` — 15 `it.todo` rows

F7's partition, visible in the source: absolute URL (two origins), protocol-relative host, `javascript:`, `data:`,
backslash path, traversal, encoded traversal, prefix match, public route, nonexistent route, a parameterised route
with a real id substituted, an allowlisted path with a query, one with a fragment, and the AC9 sweep over all of
them. **T24 does not pre-empt a single row.** It proves the fallback with one representative only —
`/app/home`, the basename-carrying form, which is T24-D8's own decision and belongs to no T25 family.

## 3 · Mutation harness

Twenty mutants, over `returnTarget.ts` and `CallbackScreen.tsx`, run against the six auth test files (baseline
**516 passed, exit 0**). Script:
`/private/tmp/claude-501/-Users-macbookpro-personal-crypto-dispute/d6c7daa6-f34e-40ce-afe5-23ce45bf781e/scratchpad/mutate-t24.py`.
The tests were committed and pushed before it ran, because it reverts with `git checkout`.

| Mutant | Verdict | Killed by |
|---|---|---|
| M1 membership becomes a prefix test | KILLED (3) | the parameterised-route case; `/invoices/:id` starts with `/invoices` |
| M2 membership becomes the reverse prefix test | KILLED (1) | the corrupted-value rows; **every** member starts with `''` |
| M3 membership becomes a substring match | KILLED (7) | the derivation and basename cases |
| M4 the parameterised-route filter is dropped | KILLED (3) | T24-D5's case |
| M5 the filter looks for a colon anywhere (the pre-Phase-9 rule) | KILLED (5) | **only** the invented table's `time/12:30` — R-I4 earning its place |
| M6 the fallback returns `null` | KILLED (15) | every no-target case |
| M7 the stored value is returned unvalidated | KILLED (11) | AC2, AC15 |
| M8 the removal precedes the read | KILLED (23) | the ordering log |
| M9 the removal is unguarded (T24-D9 gone) | KILLED (1) | AC16's case, alone |
| M10 the whole body is unguarded (T24-D10 gone) | KILLED (2) | the unreachable-store case |
| M11 the removal is unconditional | KILLED (5) | "removes nothing on the way", plus T23's inherited key-set assertions |
| M12 the write-side filter is dropped (**I2 reverted**) | KILLED (1) | R-I2's case, alone |
| M13 the allowlist is built at module scope | **detected by collapse** | see below |
| M14 the take moves inside the guard (**I1 reverted**) | KILLED (1) | R-I1's case, alone |
| M15 the `onScreen` guard is dropped | KILLED (2) | the unmount case, here and T21's |
| M16 the effect cleanup is dropped | KILLED (2) | the same two |
| M17 the navigation does not replace | KILLED (1) | the `historyAction` case, alone |
| M18 the target is ignored; every sign-in goes home | KILLED (32) | every honoured case |
| M19 a discard honours the target | KILLED (89) | the whole discard suite across five files |
| M20 the navigation never happens | KILLED (32) | every destination case |

**M13 is reported honestly rather than counted as a kill.** Building the allowlist at module scope makes
`AUTHENTICATED_ROUTE_PATHS` `undefined` inside its temporal dead zone, and `undefined.filter` throws at import, so
the test file never collects: `exit 1`, **no tests run**. The harness scores an unparseable run as BROKEN, not
KILLED, and that is the right rule — a mutant that stops the suite from running could be hiding anything. The
mutant *is* detected, loudly, and the standing scan exists for the case where it would not be: in the **production**
graph the cycle resolves the other way and T16 measured Vite yielding `undefined` rather than throwing, where an
empty allowlist **fails closed silently** — every target refused, the SPA apparently fine.

**The scan had a hole, and the mutant found it.** Its first version tested indentation, and M13's module-scope
`new Set(` has its continuation line indented exactly like a function body, so the scan reported nothing. It now
counts brace depth, and the multi-line control that found the hole stays in the file, beside a control asserting a
use *inside* a function is not reported — otherwise the scan would be a prohibition on using the route table at
all.

## 4 · What this phase did not do

No production change. No T25 rows. No home screen (T24-D11) and no assertion about what renders at `/app/home`,
because the answer today is nothing. No real-browser round trip: `oidcRoundTripEndToEnd` is owned by no task in
this package and remains one of the five unowned pieces.

**AC11, AC12 and AC13** are not cases in this file by nature — no new catalogue string or dependency, every
inherited assertion updated with its reason restated, and the suite green by exit code. They are Phase 12's to
verify.

---

## Next — Phase 11 — test review (Kimi 2.7).
