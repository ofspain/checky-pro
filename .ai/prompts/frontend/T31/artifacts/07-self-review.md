# frontend · T31 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` + `artifacts/07-mutation-harness.py` |

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0, no output |
| T31's own cases | **50 / 50**, exit 0 (`-t "T31:"` over the shared file) |
| The shared file | **103 / 103** — T30's 53 untouched, T31's 50 added |
| Whole suite | **1502 passed / 1 failed (1503)** — the known environmental `lintRejectsHandWrittenBackendFetch` |
| Mutation | **25 / 25 killed, no survivors, 0 BROKEN**, every one by T31's own cases |

The mutation run is recorded in full rather than summarised, because the first attempt at recording it was
piped through `tail -8` and lost the baseline and the `BROKEN` count. Exit 0 proves the baseline guard passed
and nothing survived, but **a `BROKEN` line does not change the exit code** — which is precisely the gap that
let T30 publish a 26-killed run as 28 of 28. Re-run with the whole output kept:

```
baseline whole suite: 1 failed of 1503, exit 1
baseline T31 cases:   0 failed of 103, exit 0
KILLED lines: 25        BROKEN lines: 0        survivors: none
```

No `INHERITED-ONLY` line appears, so all 25 were killed by T31's own cases and not by T30's 53 sharing the
file.

---

## Findings

### S1 — MQ06 escaped the first run, and the criterion it broke was one I had written

· **Issue:** `readAccount.ts` named `/accounts/me` as a quoted literal, but nothing asserted it. The mutant
replaced it with `` api.GET(`/accounts/${'me'}` as '/accounts/me') `` and the entire suite stayed green.
· **Severity:** **Major** (now closed)
· **Evidence:** `frontend/src/profile/readAccount.ts:169`. The frozen brief's AC1 asks for "the path a quoted
literal argument and never assembled" **in those words**, and no case existed behind it.
· **Why it is an ESCAPE and not an inert mutant** — the judgement this package has now had to make five times
(T28's MB27, T30's MP09 and MP26, here): the assembled form yields the **identical runtime string**, so no
runtime case can ever distinguish the two. That makes it unkillable by behaviour and killable only by a source
scan — the same position AC5 is in. An inert mutant is one that cannot do harm; this one does harm that is
invisible at runtime, which is the opposite.
· **Why the property is worth holding:** an assembled path needs a cast to pass the type checker — the
mutant's own `as '/accounts/me'` is the tell — and a cast is how a request reaches an endpoint the contract
does not declare while still looking like a generated-client call (**L6**).
· **Recommendation:** none outstanding. Closed by a source-shape case mirroring T30's, and MQ06 is now killed.

### S2 — `isUsableAccount` is the only null-safety in the module, and that is deliberate but load-bearing

· **Issue:** every shape claim funnels through one function. If a future edit moves the `!response.ok` check
after it, or inlines part of it, the JSON-`null` arm stops being first.
· **Severity:** **Minor**
· **Evidence:** `readAccount.ts:127` and `:189`. The `null` arm is the first line of the guard, and `:189`
calls it before anything touches a property.
· **Assessment:** correct as written, and three mutants hold it — MQ11 removes the `null` arm, MQ14 drops a
field check, MQ15 drops the whole call. All three die. The concentration is a feature: one place to read, one
place to mutate.
· **Recommendation:** none. Noted so a reviewer knows the single point of failure is intentional and
instrumented.

### S3 — the runtime status list is a restatement of the schema, and no type can fully close that

· **Issue:** `ACCOUNT_STATUSES` duplicates the contract's enum. A union is compile-time only, so some runtime
copy is unavoidable — but a copy is a copy.
· **Severity:** **Minor**
· **Evidence:** `readAccount.ts:53-59`, with `satisfies` and `NoUnlistedStatus` either side of it.
· **Assessment:** held in three independent ways, and the two compile-time pins were **run before** the case
that depends on them was written: dropping `'DELETED'` gives `TS2344`, adding `'ARCHIVED'` gives `TS2322`. The
third leg compares the list to the YAML, which no type can reach, and **MQ25 proves that leg is what catches a
drift** — under vitest the file transpiles without typechecking, so a list that loses a value runs happily and
only the YAML comparison fails it.
· **Recommendation:** none. This is the narrowest form of the restatement that makes AC8 checkable at all.

### S4 — T31 corrected three instruments it does not own, and that is the largest risk in the diff

· **Issue:** T31's production file tripped T29's state scan, T27's second-factor scan and the token-storage
pin. All three were corrected in **other tasks' files** (T31-D2, D3, D4).
· **Severity:** **Major** — not because any change is wrong, but because editing another task's instrument is
the highest-leverage way to weaken this suite, and a reviewer should look hardest here.
· **Evidence:**
  - `identityErrorsIndistinguishableAcrossAccountStates.test.tsx` — `PERMITTED_STATE_SOURCE` added, premise
    comment corrected, one control added.
  - `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx:173` — bare `tfa` left-bounded to `\btfa`.
  - `tokensAreNeverWrittenToAnyStorage.test.tsx:963` — `this.name =` count 4 → 5.
· **Assessment, per change:**
  - **T29's** was a **false premise, not a false positive.** Its own comment claimed no later task asks for
    `locked`/`suspended`/`deleted`; task 31 was already in `tasks.md` and the contract enum names all three.
    The fix is an allowlist with a reason **and a bound** — the file calls no translator and is a `.ts` file,
    which cannot contain JSX — plus a control proving the row is load-bearing, not stale, and bounded. The
    global pattern is unchanged and still global over **copy**, which is what R30 and L4 actually protect.
  - **T27's** was a genuine **false positive with a future victim**: `tfa` matched inside
    `ReadAccountFailure`, and matches `PaymentFailure` too, which the payment tasks are specified to write.
    Left-bounding removes the false positive and keeps `tfa`, `tfaCode`, `tfa_method`.
  - **The count** is a pin working as designed.
· **Residual risk, stated plainly:** `PERMITTED_STATE_SOURCE` is a new exception in a security-relevant scan.
It has one row, a bound and a dirty-proof — but **no mutant of mine attacks it**, because the harness mutates
production source and this instrument lives in a test file. Its control is the only thing holding it.
· **Recommendation:** this is the first thing Kimi should attack in phase 8. Specifically: whether the bound
(no translator, `.ts` only) is the right bound, and whether an allowlist was the right mechanism versus moving
the three words into T29's sign-in-scoped tier.

### S5 — no `redirect: 'manual'` and no 3xx handling, by measurement rather than by omission

· **Issue:** `readProfile.ts` has three redirect arms; `readAccount.ts` has none. A reviewer comparing the two
siblings will notice and should be able to find the reason without asking.
· **Severity:** **Minor**
· **Evidence:** `readAccount.ts:169` passes no options at all; the module header and the T31 block header both
record why.
· **Assessment:** measured, not assumed — `/accounts/me` answers a real `401`, where a bearer-less `/userinfo`
answers `302` into a followed `200 text/html`. Copying the arms across would have produced code no case could
reach, and T30's phase 11 is the evidence for what that costs: its `opaqueredirect` arm was correct and wholly
unverified for three phases.
· **Recommendation:** none. Recorded in both headers.

### S6 — the no-token short-circuit survives for a different reason than T30's, and the reason is weaker

· **Issue:** T30's short-circuit prevents a redirect being parsed as a profile. T31's prevents only a
`Set-Cookie` side effect and a doomed renewal, because the 401 here is well behaved.
· **Severity:** **Minor**
· **Evidence:** `readAccount.ts:157-158`.
· **Assessment:** still worth having — an unauthenticated read starting a server-side session is a real
side effect — but it is an **optimisation plus a side-effect avoidance**, not a correctness fix, and the module
comment says exactly that rather than overclaiming. `''` is guarded alongside `null` because
`sessionBoundary.ts:89` attaches with `if (token)`, so an empty string sends no header at all. MQ22 and MQ23
hold both arms.
· **Recommendation:** none.

### S7 — `emailVerified` is constrained only as "a boolean, faithfully read", on purpose

· **Issue:** T31 asserts nothing about `emailVerified` driving UI, which could read as an omission.
· **Severity:** **Minor**
· **Evidence:** `readAccount.ts:134`; MQ12 kills a truthiness check.
· **Assessment:** deliberate. `emailVerifiedClaimIsNotAccountState` is **T32's** named test, and constraining
the field further here would make T32's claim unprovable or pre-empted. T31 proves `false` is accepted and
returned as `false`, which is what the contract requires and what a truthiness bug would break.
· **Recommendation:** none. T32 must not be allowed to inherit this as already-done.

### S8 — enumeration safety, secrets, money, transactions, idempotency, thread-safety

· **Severity:** **Informational**
· **Enumeration safety (L4):** no surface here. The backend derives the account from the principal's `sub` and
exposes no path parameter, so there is no identifier for a caller to substitute. Stated because the absence is
the point.
· **Secrets (L3, L10):** no token, no account field, and no header detail reaches storage, a log, the URL or
the DOM. The measured `WWW-Authenticate` string is asserted absent from all seven surfaces and six console
channels; MQ18, MQ19 and MQ20 hold it. No URL is written in any comment, because
`shellBuildsAndMounts` scans app source for `https?://` without stripping comments — twice a hazard in T30.
· **Money (L7):** not applicable; no monetary value in `AccountResponse`.
· **Transactions / thread-safety:** not applicable. One async call, no shared mutable state of its own; the
single-flight renewal is the boundary's and is consumed, not reimplemented.
· **Idempotency:** `GET` on a read-only endpoint. R23's no-replay clause covers non-idempotent POSTs and does
not bind here; only its copy half does, held by MQ24.
· **Module boundaries:** the reader owns neither the token's lifetime nor renewal; both arrive as parameters,
so the composition root stays as unwired as it already was.
· **Readability / complexity:** one exported function, one error class, one derived type, two private guards.
The module is comment-heavy in this package's style, and `isUsableAccount` takes `unknown` deliberately so its
checks do not read as redundant to the next person.

---

## What I would attack first if I were reviewing this

1. **`PERMITTED_STATE_SOURCE`** (S4). A new exception in a security scan, in a file T31 does not own, with no
   mutant able to reach it.
2. **The `\btfa` narrowing** (S4). Verify it still catches every second-factor spelling T27 cares about; I
   checked `tfa`, `tfaCode`, `tfa_method` and T27's own controls pass, but the pattern is someone else's.
3. **Whether failing closed on an unrecognised status is right** (G2). It is a product decision dressed as a
   type decision: a sixth server-side status becomes a hard failure in the SPA until the contract is
   regenerated. I argue that is correct under L6 and safe on a field that gates access — but it is the
   judgement in this task most worth a second opinion.

---

**Phase 7 complete.** 50 cases, 25/25 mutants, one real escape found and closed. Phase 8 is Kimi 2.7's.
