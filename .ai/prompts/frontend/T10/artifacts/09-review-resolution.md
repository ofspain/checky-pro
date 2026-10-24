# frontend · T10 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

## Dispositions

Kimi's two fresh findings are both Low and both carry the recommendation *no change*. I agree with both, for the
reasons it gives, so **this phase changes no code**.

### Phase 8 finding 1 — the harness-duplication warning is one-way — **ACCEPTED, recorded, no change**
Correct, and it is the declared cost of D4. The reciprocal warning cannot be added without editing
`sasNavigationBypassesServiceWorker.test.ts`, which the frozen brief and L12 both forbid. Kimi's own
recommendation is to accept the risk and record it in phase 13, which is what happens. If a later gate allows a
shared test-only helper, both copies go at once — that is the right moment, because the helper would then be
imported by a file the task has already opened for its own reasons.

### Phase 8 finding 2 — `repoRoot` still computed at module load — **ACCEPTED, no change for T10**
Kimi states it plainly: no change required, the `repoFile()` helper already makes a wrong root fail with a sentence
naming the computed path. This is the T08 owner decision applied for the third time (keep the pattern, add the
existence check), and `import.meta.dirname` remains unavailable under the Vite transform in jsdom, which is why the
pattern exists.

### Self-review 1 — AC6 overstates cross-tab coverage — **ACCEPTED, scope narrowed in the artifacts**
Both reviews agree. The case proves the decision is **stateless**; it does not prove cross-tab cache isolation,
because nothing in this task writes to a cache. The phase 12 traceability matrix therefore credits AC6 against
statelessness only, and **does not** claim R22 cross-tab coverage — that belongs to tasks 11 and 12 and to T06's
machinery. No test change: the assertion is sound, only the claim around it was too broad.

### Self-review 2 — the harness returns `undefined` for unknown paths too — **ACCEPTED, mitigated, to be confirmed by mutation**
AC1 and AC4 each assert `isDenylistedNavigation(...) === true` alongside the harness result, and AC7 asserts
membership, so a dropped `/connect/logout` fails three ways. Phase 10 must demonstrate that explicitly with AC-M
mutant 1 rather than assume it.

### Self-review 3, 4, 5, 6, 7 — **ACCEPTED, no change**
No production footprint (the declared shape of the task, D1/D5); a fourth copy of the `agents.md` parse (extracting
a shared reader would touch frozen files); the one-way warning (finding 1 above); the `400` variant asserting a URL
rather than a response (this task has no responses); `../app/routes` coupling (D3's typed import, more robust than
parsing source).

## Net change in this phase

**None.** No file was modified. Both reviews' recommendations were to record rather than change, and the two claims
that were too broad — AC6's cross-tab framing and any suggestion that T10 constrains the real worker — are narrowed
in phase 12's matrix and phase 13's summary instead of being papered over.
