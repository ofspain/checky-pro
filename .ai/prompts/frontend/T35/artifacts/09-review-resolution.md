<!-- MODEL: Claude Opus 5 — Phase 9 (Review Resolution) -->

# frontend · T35 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Consumes** | `artifacts/08-independent-review.md` (Kimi 2.7) |
| **Produces** | `artifacts/09-review-resolution.md` |

Twelve findings. **Nine went into code, one was answered with a correction, two were informational.** Every
citation was checked against source first, and two findings were **measured before being answered**, which
changed the answer in both cases.

## I9 — the finding was right about the cause and wrong about the fix

**Its recommended test would have been added broken.** I9 asked for a case spying `takeSignInNotice` to throw
and asserting `consumeAuthorizeState` and `clearReturnTarget` are "still reached". They are not: `signOut`
has no `try/catch`, so a throw from step 2 abandons steps 3 and 4.

What I9 was right about is that **my comment implied a resilience the code does not have** — "Each later call
is guarded inside its own module and so cannot stop another" reads as *a throw would not stop the others*,
when the true claim is *none of them can throw today*. Corrected in the module, which now states the
mechanism exactly: no `try/catch`, a throw abandons what follows, and the clear goes first because its
omission is a security defect while a later failure leaves only an untidy store.

Then the **true** behaviour is pinned, which turns out to be the better case anyway: a throw in step 2 leaves
the return target behind and the token gone. *What is left behind is residue; what is not left behind is a
token.* That asymmetry is the whole argument for the ordering, and it is now measured from both ends.

**SO18** records the inverse as what it is — a design change, not a repair: each step individually guarded, so
the one condition that should surface is hidden. It was first written against step one, where it would have
proved nothing, because phase 9's case forces the throw in step two. Re-anchored. *A mutant must measure its
own description.*

## The flake I9 exposed

Adding those cases made **AC9 fail with `null`**. It had passed three runs. The cause: it waited exactly one
macrotask for a `BroadcastChannel` delivery, and two extra cases ahead of it were enough to lose the race.

**The failure mode is why this matters more than an ordinary flake.** AC9's assertion message says "GOOD
NEWS, AND THIS CASE MUST CHANGE: a signed-out tab no longer adopts a sibling renewal." A flaky hazard pin
reports that the defect is *fixed* when nothing has been fixed, and teaches the reader to re-run it until it
agrees. Now polled with a bound; the assertion is unchanged, so a genuine fix still reports as one — the
token simply stays `null` through every attempt. Run three times clean.

## I2 — accepted, and it is the gap my own self-review missed

I named the T23 widening in S3#4 and **did not name the T24 one**. `clearReturnTarget` was added to
`returnTarget.ts` — task 24's module — and tested only through sign-out. That is a hidden contract change: a
refactor of that module would break the new function and only **T35's** suite would go red, so the failure
would read as a sign-out defect rather than a return-target one.

Four cases added to T24's own suite, holding the new export to the same contract `takeReturnTarget` is held
to: removes exactly that key, idempotent, never throws, never calls `clear()`. Plus the one claim it does
**not** share — it reads nothing, which is the entire reason it exists (T24-D4) — and a case asserting the two
functions are independent, which is the guard against the delegation refactor I deliberately did not make.
T24's suite: **188 passed.**

## I4 and I5 — measured first, which changed the answer

Kimi recommended *narrowing the claim* for I4 and *documenting the limitation* for I5. Both were measured
before being answered:

```
grep -rnE "^const [A-Za-z_]+ *(:[^=]*)?= *(new (Map|Set|WeakMap|WeakSet)\(|\[)" src   # I4 → nothing
grep -rn 'themistra' src | grep -E '\$\{|\+ *.themistra|themistra.* *\+'             # I5 → nothing
```

Neither pattern exists in production today, so **both scans were extended rather than their claims narrowed**
— extending costs nothing when the scan passes, and narrowing would have written the gap into the criterion
permanently.

- **I4:** AC7 now also flags a module-scope `const` holding a `Map`, `Set`, `WeakMap`, `WeakSet` or array
  literal. Its control exercises all three planted shapes, because a control that fires one of two branches is
  a control for half a scan. **SO14** still kills.
- **I5:** AC8 gains a case forbidding a key *assembled* at runtime — inside a template literal, or either side
  of a `+` — with its own three-shape control. **SO16** proves the new ban is load-bearing.

## Applied as recommended

| # | Severity | What changed |
|---|---|---|
| **I3** | Major | AC9's case renamed `KNOWN HAZARD: a signed-out tab still adopts a sibling renewal (task 39 must replace this)`, so the intent is visible in the test report and not only in the assertion message. |
| **I6** | Minor | The direct-call limitation is documented where the scan is: an alias, a wrapper or a callback reference would pass, accepted because the threat is someone assembling a second sign-out and that is written as direct calls. An AST pass is disproportionate. |
| **I7** | Minor | The suite's own module-scope `let tokenSequence` is gone, moved into a closure. A suite that enforces a rule it does not follow invites the reader to discount the rule. |
| **I8** | Minor | The caller allowlist is now asserted in **both directions**: a permitted caller that no longer calls its primitive is a failure, not a pass. **SO17** kills it. Same rule AC8 already applied to the inventory. |
| **I10** | Minor | `/\bPromise\b/` dropped from AC4's scan — `Promise.resolve()` defers nothing — and `setInterval` and `requestAnimationFrame` added, which the broad ban was never catching anyway. |

## Held

- **I1** (Blocker, dormancy) — recorded, not closeable here. **OQ-T35-2**, sixth task. AC10 is the available
  substitute and phase 7 S3#1 already declines to claim production satisfaction.
- **I11, I12** — informational, no action. (I11 recalls T33's baseline as 1570; it was 1575. Immaterial.)

## Gates after the repairs

| Gate | Result |
|---|---|
| T35 cases | **32 passed**, exit 0 — three runs, no flake |
| T24's suite | **188 passed** |
| whole suite | **1610 passed / 1 failed of 1611** — the known `oxlint` host case |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| mutation | **17/17 regressions killed · 1/1 canary · 0 survivors · 0 broken** |

**Three coarse kills verified narrowly**, rather than trusted: SO12 (306 failures) fails exactly AC2's two
cases, SO17 (9) exactly AC10's one, SO18 (2) exactly AC1's one.

**Phase 9 complete.**
