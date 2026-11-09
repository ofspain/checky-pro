<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation) -->

# frontend · T41 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T41 — Unauthenticated redirect |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md`, `artifacts/10-mutation-run.txt` |

## Gates

| | |
|---|---|
| `vitest run src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | **34 / 34** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1779 / 1780** — the one failure is the known host-environmental `lintRejectsHandWrittenBackendFetch` case |
| `python3 -I …/07-mutation-harness.py` | **20/20 regressions killed, every kill `KILLED/own`, 0 survivors, 0 unfalsifiable, 0 broken, 0 disagreements** |

## The file

`src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` — the name `package.md` §8 gives. 34 cases in
eight describes, every one prefixed `T41` from the first (phase 3, F13), because task 42 extends this file and
T40's phase 12 found an AC-numbering collision in a file two tasks had both numbered from one.

**It mounts the real route table.** T40's suite derives a probe table because a blocked route rendered nothing
there and an arm needs something to look at; after T41 a blocked arrival renders the sign-in screen, so the real
table is the honest instrument and a probe would be a second variable.

**`callback` is the one public route excluded from the mounted arms.** Its screen acts on arrival — what it does
with a missing grant is task 6's behaviour — so mounting it here would measure that and call it task 41's.
Its public-ness is asserted by **matching**, which happens when the router is created and needs no render. That
same render-free arm is what pins the index route's **narrowness** (phase 8, I5): `/app/sign-in` matches
`sign-in`, not the index route.

## Written in the survivors' order, not in AC order

Phase 9 left six rows alive, and the harness's list was the specification for this file:

| row | the case written for it | now |
|---|---|---|
| **RD06** the arrival made absolute | *"remembers the arrival for a real authenticated path, basename-free"* — the exact stored value, and `.not.toBe('/app/account')` beside it | **KILLED/own**, by four cases |
| **RD07** the basename strip put back | *"remembers nothing for a DOUBLED basename, which is the defect phase 9 removed"* — `/app/app/account`, the one arrival where that strip could fire | **KILLED/own** |
| **RD08** the target never remembered | the three AC14 value cases and the AC8 key-inventory case | **KILLED/own**, by six |
| **RD09** a branch where the allowlist belongs | *"puts no branch on the arrival before remembering it"* — a **source** scan, which is what phase 8's I3 said this rule needs | **KILLED/own**, and by that case **alone** |
| **RD02** the authenticated clause dropped | the two F7 cases: with a session, neither an unmatched path nor the bare `/app/` redirects, and nothing is stored for them | **KILLED/own** |
| **RD05** the destination hardcoded | the two AC11 source cases | **KILLED/own** |

**Every kill is by a case whose description is the mutant's description.** That is the rule T36 learned the hard
way, and it is checked here row by row rather than inferred from a count: the harness prints the failing case
names, and `artifacts/10-mutation-run.txt` keeps them.

**RD07 is killed by two cases, and the second one is informative.** Besides the doubled-prefix case, it fails
the no-branch scan — because the strip phase 9 removed *was* a branch standing between the arrival and the
allowlist. The scan written for Kimi's I3 turns out to forbid the defect I6 found, which is two findings landing
on one line of source from opposite directions.

## What the 34 cases hold

| describe | cases | the claim |
|---|---|---|
| **AC1** arrival (a) | 3 | all eleven land on sign-in, `REPLACE`, with the heading really rendered — asserted as literal English **and** against the catalogue, because `t()` returns the key when an entry is missing; plus StrictMode; plus the empty-string token |
| **AC2** arrival (b) | 2 | an unmatched path, and one that **resembles** a public path (`sign-in-x`), which is what would fail if the catch-all ever matched by prefix |
| **AC3** arrival (c) | 2 | `/app/` and `/app` |
| **AC4** the nine public | 6 | matching for all nine (callback included, render-free); rendering for the eight; the **signed-out landing** by name, because R19's landing is reached *without* a session by definition and a redirect one level too high would eat it; the sign-in screen at its own path with no loop; query string; trailing slash |
| **AC5** with a session | 3 | none of the eleven redirects; and F7's two arms, which keep T40's blank page for the signed-in visitor at an address that matches nothing |
| **AC7, AC14** the return target | 6 | the exact value; every rememberable path; nothing for a parameterised path; nothing for (b) or (c); nothing for a doubled prefix; the right value under StrictMode — **and no case counts writes**, which is the constraint I2 made binding |
| **AC8, AC10** nothing added | 3 | only L16's key is written; no catalogue entry mentions a redirect; and the markup of a redirected arrival is **byte-identical** to a direct arrival at sign-in, so the redirect contributes no wrapper, no notice and no word |
| **AC9** indistinguishable | 1 | the three arrivals collapse to **one** destination, **one** history action and **one** markup string, by `Set` size rather than by three pairwise comparisons |
| **AC11** destination and source | 7 | the `to` prop built from `SIGN_IN_PATH`; no literal spelling; no module-scope read; `replace` in the source as well as the behaviour; the no-branch rule; a 25-row forbidden-surface sweep; and the control |
| **AC13** the set | 1 | nine, eleven, and a non-empty rememberable subset strictly smaller than the eleven — so an arm that loops cannot silently loop over nothing |

**The AC7 control that matters is the sweep's.** Every pattern must fire on its own planted probe **and** the
real source must not already trip it — the half T40's AC7 shipped without, where two rows were measuring the
probe only. The length is pinned at 25 and no two rows may share a pattern, which a length check cannot see.

## One thing phase 10 measured that I had wrong

`import.meta.url` is **not** a `file:` URL under vitest's jsdom environment, so `readFile` rejects it: all seven
source cases failed with *"The URL must be of scheme file"* while every behavioural case passed. The reader now
uses `expect.getState().testPath`, as T40's does. Worth recording because the failure mode is a **whole family
of cases failing for one reason that has nothing to do with the claims** — and the 27 that passed were what made
it obvious the fault was in the reader rather than in the sweep.

## Deliberately not written

- **No write-count assertion** anywhere (I2, phase 3 F4). The write happens during render and StrictMode doubles
  it; the value is the claim.
- **No end-to-end return** (deep link → sign-in → back to the deep link). It needs a screen behind the gate, so
  it is **OQ-T41-2** and task 42's. What is pinned here is both ends separately: the value stored, and
  `takeReturnTarget()` handing back the same path and deleting it.
- **No case for the basename-spelled destination**, which is OQ-T41-1: it exhausts the V8 heap rather than
  failing. Phase 9 narrowed it instead — `APP_BASENAME` is no longer imported by the module at all.

**Phase 10 complete.** One file created, 34 cases, 20/20 mutants killed by cases in this task's own files, no
survivors, no unfalsifiable rows left, no disagreements.
