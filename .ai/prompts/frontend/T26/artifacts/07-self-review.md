# frontend · T26 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |

Reviewed the 42-case file against the frozen brief, then stopped arguing and measured it: 22 mutants, each one
an extra item in a store L16 governs. Six findings. Two are real holes in the instruments, one is a defect in
the test file's own hygiene, and the remaining three are statements of what the file cannot do that the file
should make itself rather than leave to this artifact.

---

## The measurement

`artifacts/07-mutation-harness.py`, committed before it was run (it reverts with `git checkout -- src/auth/`).
Baseline: auth suite 718 passed / 0 failed, T26's file 42 / 0.

**22 of 22 killed. No survivors. 21 of 22 killed by T26's own file.**

| Family | Mutants | Result |
|---|---|---|
| A fifth key beside the four | MA1–MA4 | killed, own |
| A third writing module, and a reader that starts writing | MA5–MA6 | killed, own |
| The three indirect call forms a text scan misses | MA7–MA9 | killed, own |
| The wrong store, or a wipe | MA10–MA13 | killed, own — **MA12 is the `clear()` mutant that escaped T25's own rejection rows**, and this file kills it with 34 failures |
| A forbidden surface named | MA14–MA16 | killed, own, and precisely: `navigator.storage` fails **one** case in the whole auth suite |
| A secret under a legitimate key | MA17–MA18 | killed, own |
| The transient write that leaves a clean store | MA19–MA20 | killed, own |
| **Written to escape** | MA21–MA22 | MA21 killed by this file; **MA22 killed only by a neighbour** |

## S1 · A dormant write with a computed method name is invisible to both of this file's instruments

**Severity.** High — it is the one hole measurement found, and the remedy already exists in the package.

**Evidence.** MA21 and MA22 were written to escape, not to pass.

- **MA21** computes the method name (`const method = 'set' + 'Item'`) and writes a fifth key on a path every
  flow drives. The source scan cannot see it — every pattern needs the literal `setItem` — but the runtime watch
  catches it, because the write runs. **Killed by this file, 63 failures.** That is the two-net argument working
  exactly as T22-D14 claims.
- **MA22** is the same write behind `navigator.userAgent.includes('ZZZ-NEVER-MATCHES')`. The scan cannot see the
  name and the watch never runs. **This file does not catch it.** One case in the whole auth suite fails, and
  it is T23's: `stateSurvivesRedirectAndIsConsumedOnce.test.tsx:851`, "spells no storage key of its own",
  which counts occurrences of the key prefix in `authorizeRequest.ts` and found four where three belong.

**Why it matters.** A guard whose job is the package-level claim should not depend on a neighbour for the one
family it cannot otherwise see, and dormant code is not exotic: a feature flag, a user-agent branch, an error
path no test drives. The write's *key* survives in source even when its method name does not and its path never
runs — which is precisely what T23 noticed and T26 did not.

**Recommendation.** Add a **third instrument**: derive the common prefix of the four inventory keys
(`themistra.auth.`) from the constants rather than spelling it, scan production source for every key-shaped
literal carrying that prefix, and require each one to be an inventory key. It is text, like the method scan, but
it keys on the *name of the thing stored* rather than on the call that stores it, so it sees dormant and computed
writes alike. Phase 10, with MA22 as its proof.

**Confidence.** High — measured, with the failing case named.

## S2 · The `localStorage` scan forbids something `design.md` permits, and says nothing about it

**Severity.** Medium — a guard that will fail a legitimate future change without explaining itself.

**Evidence.** The case "finds no production module that writes to localStorage at all" asserts
`hits(targets, /\blocalStorage\b/)` is empty, with the message "localStorage holds public preferences only
(design.md §5)". But `design.md:193` **permits** exactly that: "localStorage: public preferences (theme, locale)
only." So the day a theme toggle lands, this case fails, and its message states the rule the change complies
with rather than the rule it broke.

**Why it matters.** This is the same hazard the inventory count pin was deliberately given a long message for.
The pin fails loudly and names clause (b) as the expected next entrant; this case fails loudly and names nothing.
A guard that cannot distinguish "a preference arrived" from "a token leaked" will be edited by whoever hits it
first, and the edit will be a deletion.

**Recommendation.** Keep the behavioural claim as it is — `localStorage` is empty after every flow, which is
true today and true after a preference lands only if the preference is not written during a sign-in. Narrow the
structural claim to what L16 and L3 actually forbid: **no inventory key and no secret-shaped value in
`localStorage`**, with the message naming public preferences as the permitted entrant. Phase 10.

**Confidence.** High — the spec text is explicit and the current assertion contradicts it for a future change.

## S3 · Two arrivals in one case leave the first screen mounted

**Severity.** Medium — a test-hygiene defect that can make a later assertion pass or fail for the wrong reason.

**Evidence.** `arriveAt` assigns `mounted = { root, container }`, overwriting the previous entry **without
unmounting it**, and `afterEach` unmounts only the last. Three cases in the repetition block call `arriveAt`
twice and clear `mounted` by hand (`mounted = undefined`), which does not unmount anything — it only hides the
first root from the cleanup. T24 and T25 use `arrival.unmount()` between arrivals for this reason
(`returnTargetOutsideAllowlistFallsBackToHome.test.tsx:589`, `:644`, `:772`, `:1018`).

**Why it matters.** The abandoned screen's effects stay live. In this file that means a second `CallbackScreen`
can still be running while the next one's store assertions are taken, and a storage write from the orphan would
be attributed to the flow under test. No case fails today, which is exactly why it should be fixed now rather
than when one does.

**Recommendation.** Replace each `mounted = undefined` with `arrival.unmount()` on the arrival being abandoned.
Phase 10.

**Confidence.** High — structural, and the idiom to copy is three lines away in a neighbouring file.

## S4 · Four of the swept values are shapes no flow here produces

**Severity.** Low — honest limitation, currently stated in the header but not at the cases.

**Evidence.** `secretsOf` includes a password, a recovery code, an API key, an email address and an account
UUID. The SPA handles none of them in any flow this file drives — passwords are SAS-hosted (L2), and no screen
in the package collects the others yet. So those five needles prove only that the sweep read the surface.

**Why it matters.** It is not a defect: R29 names them, and a sweep that drops a needle because today's flows
cannot produce it is a sweep that will not notice when tomorrow's can. But a reader counting green cases will
over-read it, and the two sentinel cases that make the search credible are in a different block.

**Recommendation.** Say it at the case rather than only in the header: name which needles are live (the tokens,
their claims, the authorization code) and which are forward-looking, and point at the sentinels. No new case.

**Confidence.** Medium — a documentation improvement, and the kind this package treats as part of the work.

## S5 · The discard rows assert a subset where they could assert the exact set

**Severity.** Low.

**Evidence.** Each of the six discard cases calls `expectOnlyInventory`, which asserts no stranger is present. A
discard deletes nothing (T23-D2) and does not consume the target (T24-D14), so the exact set is known: all four.
Only the discard-then-retry case asserts it.

**Why it matters.** A mutant that *deleted* a key on a discard path would pass all six rows. T23 owns the
deletion claim, so this is not T26's hole — but the stronger assertion costs one line and makes each row say
what it measured rather than what it tolerated.

**Recommendation.** Assert the exact four-key set in the six discard rows, keeping `expectOnlyInventory` for its
message. Phase 10.

**Confidence.** Medium.

## S6 · The forbidden-surface list's limits are stated once, in the header

**Severity.** Low — intentional, recorded so it is not mistaken for an oversight.

**Evidence.** `FORBIDDEN_SURFACES` has six entries and its comment says the list is "enumerative, not
exhaustive". The case that scans it carries the design-decision message, which is right. But the standing claim
"nothing else is stored client-side" (L16) is broader than any six names, and a reader may take the green case
for the whole of it.

**Recommendation.** Leave the code as it is. Record in the Phase 9 resolution that the structural claim is
bounded by the list, that `design.md` §5 governs additions, and that this is open question OQ4 rather than a
coverage gap.

**Confidence.** Low — documentation.

---

## Summary

S1 and S2 are the ones that change the file: a third instrument keyed on the stored name rather than the storing
call, and a `localStorage` claim that matches what the spec permits. S3 is a defect in the test's own hygiene and
should be fixed before it costs a confusing failure. S4–S6 are statements the file should make about itself.

No production change is indicated. The twelve acceptance criteria all hold as written; S1 narrows how strongly
AC3 can be claimed until the third instrument lands, and the Phase 12 verification should say so in those terms.
