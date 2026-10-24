# frontend · T08 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T08 — No POST replay |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/09-review-resolution.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/10-test-generation.md` |

**Phase split.** T08's deliverable *is* test code (phase 4, owner decision 2: no production change), so the cases
were written at phase 6 and strengthened at phase 9. This phase is the **mutation and coverage pass** over them, as
`05-implementation-plan.md` set out. No case was added here; the file is unchanged since `a19c3ff3`.

`frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` — **47 cases, all green**: T07's 29,
plus T08's 18 nested under `describe('no POST replay', …)` at `:705`, which leaves the named test string and T07's
cases untouched.

## Test manifest (T08's cases)

| # | Case | Line | Verifies |
|---|---|---|---|
| 1–5 | `it.each(PUBLIC_POSTS)` — the five `security: []` POSTs: one transport call, `POST`, the expected path, **no bearer**, the exact JSON body, the T07 copy shown, `failures` empty | `:808` | AC1, AC2, R23 |
| 6–9 | `it.each(PROTECTED_POSTS)` — the four protected shapes (JSON body, sensitive body, no body, path parameter): one call each, `Bearer token`, the exact body (`''` where auth.yaml declares none) | `:828` | AC1, AC2, R23 |
| 10 | all nine driven with **no notice** passed to `createAuthApi`: still one call each | `:851` | AC1, L12 |
| 11 | a **renewal-capable** session: a 429 on a protected POST neither calls `renew()` nor re-sends | `:861` | AC1, AC3, L18 |
| 12 | the 401 re-send boundary: exactly `['POST /api-keys Bearer old', 'POST /api-keys Bearer new']`, identical bodies, nothing afterwards, the copy shown. Carries the R23-scope rationale as a comment | `:882` | AC3, R23, R21/L18 |
| 13 | a POST whose `clone()` throws: the 429 comes back unchanged, one call, the notice still raised | `:920` | AC4 |
| 14 | `Object.keys(notice.middleware)` is exactly `['onResponse']`, and it returns `undefined` | `:939` | AC5, `agents.md` API access |
| 15 | unmounted: no `setTimeout`/`setInterval` while handling the 429; then an hour of faked time and a `dismiss()` add no transport call | `:957` | AC6, R23 ("resume only on user action"), L4 |
| 16 | the contract drift guard: every `post:` in `auth.yaml` is in `EXERCISED ∪ UNEXERCISED`, and the contract is asserted to have been found | `:982` | AC7 |
| 17 | a marker string in `currentPassword` reaches the transport and nothing else — no console level, no storage, no cookie, no URL change, not the rendered notice | `:1001` | AC8, §5, privacy rules |
| 18 | T05 parity: a 401 on a POST renews once, re-sends once to a 201, identical bodies, no notice | `:1031` | AC9, L12 |

Every acceptance criterion in the frozen brief has at least one case, and every case has at least one mutant that
breaks it.

## Mutation runs

Each mutant was applied to the real file, the suite run, then reverted with `git checkout --`; `git status` was
checked afterwards, and `contracts/api/auth.yaml` and `sessionBoundary.ts` are byte-identical. **19 of 19 caught** —
T07's 14 (re-run, since the file is shared) plus five for T08.

| Mutant | Caught by |
|---|---|
| M15 the boundary treats 429 like 401 (renews and re-sends) | 11 cases, including #11, the only one with a renewal that would succeed |
| M16 the 401 re-send is sent twice | 6 cases |
| M17 a timer-driven retry 1 s after a 429 | 2 cases (#15's spy and its hour of faked time) |
| M18 `auth.yaml` gains a POST | exactly 1 — #16, the drift guard, and nothing else |
| M19 the boundary rebuilds a protected request without its body | 5 cases (added at phase 9, the point of P8-1) |
| M1–M14 (T07's set) | unchanged, all still caught |

**Two notes on the limits of this set.** A body-dropping defect on a *public* POST is not expressible as a mutant of
our code — the boundary returns early for public operations, so only openapi-fetch handles that body. And M15 is
invisible unless a case uses a session whose `renew()` would succeed, which is why case #11 exists; the design of
that mutant is what found the hole, at phase 6.

## Verification

- The named test file: **47/47**.
- Whole frontend suite: **145 passed, 1 failed (146)** — the pre-existing environmental
  `lintRejectsHandWrittenBackendFetch` AC4 case (this host's `oxlint@1.87.0` prints no summary line). It fails
  identically with T08 stashed; baselined both ways at T07 phase 9.
- `tsc --noEmit` exit 0. `oxlint --no-ignore -c .oxlintrc.json src` exit 0. `vite build` exit 0.
- `git status`: the T08 test file only; no production, spec, contract or earlier-task file changed.

## Open Questions

None.
