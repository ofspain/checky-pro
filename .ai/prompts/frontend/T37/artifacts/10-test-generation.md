<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation) -->

# frontend · T37 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

## Final measurement

| Gate | Result |
|---|---|
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **28 passed (28)** |
| `vitest run` | **1690 passed / 1 failed of 1691** — the known `oxlint` host case |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `07-mutation-harness.py` | **29/29 regressions killed, 0 survivors, 0 broken, 0 canaries** |

28 cases over 13 frozen criteria plus one phase-10 addition, 5 controls, 29 mutants.

## What this phase added, and why each was a gap rather than a nicety

### FU-T37-1 is closed: a side effect is not a contract

Phase 7 opened it after noticing that three structural regressions all died **incidentally**:

| Mutant | Died on, before this phase | What that case is about |
|---|---|---|
| **SL19** the landmark becomes a `<div>` | AC1's `expect(main).not.toBeNull()` | *routing* — that the declared path resolves to this screen |
| **SL20** `<h1>` drops to `<h2>` | AC3's `querySelector('h1')?.textContent` | *the catalogue* — that the heading text comes from `en` |
| **SL21** the action rendered above the heading | AC11's whole-text equality | *identity* — that nothing identifying is rendered |

Each kill was a by-product of a case asserting something else, so each would evaporate the moment that
case was refactored for its own reasons — and none of the three failure messages would tell a reader that
the screen's *shape* had changed. Nothing in T37's criteria, in L4, or in R19 says a word about landmarks
or heading level, so the shape was never anyone's contract.

It is now one case: one landmark, at the top of the screen rather than nested, holding exactly one
level-one heading, no subheadings, and its children in reading order — asserted **as tags, not as text**,
which is precisely the half AC11's string equality cannot give. All three mutants now die on it by name,
verified narrowly:

```
SL19 → PHASE 10 ... is one landmark holding a level-one heading, a paragraph and the way back, in that order
SL20 → the same case
SL21 → the same case
```

It carries a control, because `toEqual` on a three-element array can be mistaken for a set comparison: a
hand-built landmark with the same three tags in a *different* order must not satisfy it, and the control
proves the three tags are the same three so that only order separates them.

**It is labelled as not one of the thirteen frozen criteria.** Phase 4 froze the brief; this closes a
follow-up the measurement opened, and the file says so where a reader will see it.

### AC11 retains an ID token

AC8 (phase 9) proves this screen does not **take** the ID token. Nothing proved it does not **display**
it, and those are different failures: T36's whole reason for `idToken.ts` is that an ID token is identity,
so L3 governs it, and this is a public page reachable by typing its URL. Retained and asserted absent,
with its own message distinct from the access token's.

## The gaps I looked for and did not close, with the reason

| Considered | Why not |
|---|---|
| A behavioural `document.cookie` sweep | The surface is in `FORBIDDEN_REACHES` with a probe, and a cookie spy in jsdom would measure jsdom. The source scan is the stronger instrument here. |
| Pinning `PUBLIC_ROUTE_PATHS`'s **order** | A reorder changes nothing: the route table maps over it. AC10 pins the length and AC2 the membership, which is the whole claim. |
| Asserting the component takes no props | `tsc` is the instrument; a prop added and unused is not a regression anyone can observe. |
| Asserting `SignedOutScreen` is a *named* export | A rename breaks `routes.tsx` at compile time. `tsc --noEmit` is in the gates. |
| A case per forbidden surface in `FORBIDDEN_REACHES` | Phase 9's single control iterates all 22 pairs and pins the count. Twenty-two cases would measure the same thing and hide the table. |
| Reaching this route through `createAppRouter` | Task 16 owns the browser router and already pins it. T37 asserts routing through the real table, which is the part it owns. |

## Where the suite still cannot reach

- **Nothing calls `endSession()`**, so no case is an arrival *from a sign-out*. Every one is a direct
  arrival, which is exactly what AC12 says out loud. **OQ-T36-5**, eighth consecutive dormant task. This
  is the one limit no amount of test writing in this task can remove.
- **OQ-T37-1** — the copy is derived, not specified. The suite proves the words cannot change silently
  (AC3's whole equality, AC11's whole text, AC4's vocabulary scan with a control); it cannot prove they
  are the right words. The owner's.
- **AC13 proves non-branching, not truthfulness** — carried from phase 6's D3, unchanged. A screen that
  renders identically with and without a token is not asserting session state; that is the strongest
  statement available without a gate that does not exist.
- **FU-T37-2** — `routes.tsx:5`'s false consequence, in task 16's closed file.

## The 29 mutants

Unchanged from phase 9 except that **SL19, SL20 and SL21 now die on a case written for them** rather than
on three unrelated ones. Every mutant's intended case was verified narrowly — applied alone, only T37's
file run, every failing case named — because this task produced three separate occasions where a kill
count said one thing and the named failures said another.

## Phase 11

Kimi 2.7's adversarial test review. Nothing self-produced.
