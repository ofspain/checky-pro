# frontend · T26 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md`, `artifacts/07-mutation-harness.py` (extended) |

Every accepted finding is in. **50 cases, 22 of 22 mutants killed, every one by T26's own file.**

---

## What changed

| Change | Finding | Cases |
|---|---|---|
| **The namespace scan — a third instrument** | I1=S1, I5 | +7 |
| `localStorage` narrowed to what `design.md:193` permits, with a control | I2=S2 | 1 replaced, +1 |
| `arrival.unmount()` between arrivals, in three cases | I3=S3 | 0 (fix) |
| Exact key sets in the six discard rows | I4=S5 | 0 (strengthened) |
| Live vs forward-looking needles named at the case | S4 | 0 (documentation) |
| The enumerative-list boundary recorded in Phase 9 | I6=S6 | 0 |
| The harness path and run command recorded | I7 | 0 |

42 → **50 cases**. `tsc --noEmit` clean. Suite **1243 passed / 1 failed**, the pre-existing environmental
`lintRejectsHandWrittenBackendFetch.test.ts:118`.

## The third instrument, and the measurement that justifies it

```ts
function namespaceRoot(): string            // derived from the inventory, read inside a function
function namespaceMatches(code: string): string[]   // every namespace literal, minus the npm package scope
function nonStorageNamespaceNames(): Array<{ name; file; why }>   // imported, not spelled
```

It keys on **the name of the thing stored** rather than on the call that stores it, which is the one thing the
other two instruments cannot do. Seven cases:

1. No namespace literal in production source outside the inventory and the non-store allowlist.
2. **The scan is reading something**: all four inventory keys and both non-store names are found. Without this,
   a pattern that matched nothing would satisfy case 1 forever.
3–5. Three sibling shapes flagged: `…auth.evil`, `…auth_state` (no dot — I5's first example) and `…authx.foo`
   (a sibling namespace — I5's second). A prefix scan catches none of the last two, which is why this one scans
   the whole namespace.
6. The npm package scope `@<root>/api-client` is **not** flagged: an import path, not a name in any store.
7. **MA22 as a case, not only as a harness line.** The dormant write with a computed method name: every
   `WRITE_METHODS` pattern finds nothing in it (the literal is not in the text), and the namespace scan finds the
   key (the name is).

The allowlist has two rows, both `crossTabRenewal.ts`'s and both L18 rather than L16 — `RENEWAL_CHANNEL`, a
BroadcastChannel name, and `RENEWAL_LOCK`, a Web Locks name. Neither persists anything: a channel delivers to
live tabs, a lock exists while it is held. They are **imported** so a rename moves the allowlist with them.

## The mutation result

Harness: `.ai/prompts/frontend/T26/artifacts/07-mutation-harness.py`, committed before each run (it reverts with
`git checkout -- src/auth/`). From the repository root:

```
python3 -I .ai/prompts/frontend/T26/artifacts/07-mutation-harness.py        # all 22
python3 -I .ai/prompts/frontend/T26/artifacts/07-mutation-harness.py MA1    # one family
```

Baseline: auth suite 726 / 0, T26's file 50 / 0.

**22 killed, 0 survived, 0 inherited-only.**

| Family | Mutants | Verdict |
|---|---|---|
| A fifth key beside the four | MA1–MA4 | killed, own |
| A third writing module; a reader that starts writing | MA5–MA6 | killed, own |
| Indirect call forms | MA7–MA9 | killed, own |
| Wrong store, or a wipe | MA10–MA13 | killed, own — MA12 is T25's escapee |
| A forbidden surface named | MA14–MA16 | killed, own (MA16 fails exactly **one** case in the auth suite) |
| A secret under a legitimate key | MA17–MA18 | killed, own |
| The transient write | MA19–MA20 | killed, own |
| Written to escape | MA21–MA22 | killed, own |

**MA22 moved from `INHERITED-ONLY` to `own`**, from one failure to two. That single column change is the entire
justification for the third instrument, and it is why the mutant was written to escape before the instrument was
designed rather than after.

## Case manifest (50)

| Block | Cases | AC |
|---|---|---|
| the inventory is derived from the two modules that own it | 3 | AC1, AC8, AC10 |
| every state of the flow holds only inventory keys | 10 (4 states + 6 discards) | AC1, AC2 |
| no key outside the carve-out is ever written, even transiently | 2 | AC3, AC4 |
| the two instruments, and why both are needed | 5 | AC3, AC9 |
| only the two modules that own the carve-out write to a store | 4 | AC3, AC6 |
| **every name in the namespace is a store key or declared not to be** | **7** | AC3, AC4, AC9 |
| no forbidden client-side surface is even named | 3 | AC7 |
| nothing R29 forbids is in the store, as a key or as a value | 4 | AC5, AC11 |
| an unreachable store is not an empty one | 3 | AC12, AC9 |
| the instruments are proved against a dirty store | 6 | AC9 |
| repetition and resumption add no fifth key | 3 | AC1, AC2, AC8 |

## What this file still cannot do, carried to Phase 12

- **The forbidden-surface list is enumerative.** Six names; `design.md` §5 governs additions (OQ4, deferred to
  the author).
- **Five needles are forward-looking** — a password, a recovery code, an API key, an email address, an account
  UUID. No flow here produces one; the sentinels are what make the search credible.
- **It does not build the bundle.** A store opened at runtime by a dependency is
  `bundleContainsNoSecretEnvKeys`' and dependency review's concern (T22-D1).
- **L16 clause (b) remains unbuilt and unowned.** The inventory pin names it as the expected next entrant.

---

**Phase 10 complete.** 50 cases, three instruments, 22 of 22 mutants killed by this file alone.
