# auth · T03 — Phase 9: Review Resolution

**Human Approval gate.** Decisions below made by femi, applied by the model. Findings are
deduplicated across `07-self-review.md` (self-review) and `08-independent-review.md` (Kimi
independent review) where they overlap.

---

## Accepted

### C — `PasswordPolicyProperties` didn't enforce L2's 12/128 bounds; no cross-field check
(Self-review Finding 2 + Kimi Findings 3)

**Reason accepted:** L2 is a LOCKED decision ("Minimum 12 characters, maximum 128 characters ...
implement exactly as written"). The config record should not be able to express a value that
violates it, and independently, `minLength > maxLength` would silently reject every password.

**Change made** (`services/auth/src/main/java/com/themistra/auth/account/PasswordPolicyProperties.java`):
- `minLength`/`maxLength` tightened from `@Min(1)` to `@Min(12) @Max(128)` each.
- Added a cross-field check: `@AssertTrue boolean isLengthRangeValid()` returning
  `minLength <= maxLength`, so even two individually-valid values (e.g. `minLength=100`,
  `maxLength=20`) fail startup validation.

### E — `BreachCheckClient.isBreached` had no null guard
(Self-review Finding 4 + Kimi Finding 5)

**Reason accepted:** cheap, targeted defensive fix on a public method; turns an NPE deep inside
`sha1UppercaseHex` into an intentional, descriptive failure.

**Change made** (`services/auth/src/main/java/com/themistra/auth/authn/BreachCheckClient.java`):
`Objects.requireNonNull(rawPassword, "rawPassword must not be null")` added as the first line of
`isBreached`.

### F — `breach-check.timeout-ms` could overflow `int` via `Math.toIntExact`
(Kimi Finding 6)

**Reason accepted:** trivial config bound; converts a confusing `ArithmeticException` at bean
construction into a normal Bean Validation failure with a clear message.

**Change made** (`PasswordPolicyProperties.java`): `BreachCheck.timeoutMs` changed from
`@Positive long timeoutMs` to `@Positive @Max(Integer.MAX_VALUE) long timeoutMs`.

### H — `urlPrefix` trailing-slash assumption was unvalidated
(Kimi Finding 8)

**Reason accepted:** one-line defensive normalization removes an entire misconfiguration class
(missing trailing slash → wrong URL → silent fail-open under R10) without adding a new validation
constraint or changing any public contract.

**Change made** (`BreachCheckClient.java`, `buildRestClient`): `urlPrefix` is normalized to end
with `/` before being passed to `.baseUrl(...)`:
```java
String urlPrefix = properties.breachCheck().urlPrefix();
String baseUrl = urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
```

### I — Readability: implicit non-2xx-throws behavior and `isBreached`'s exception contract undocumented
(Self-review Finding 5 + Kimi Finding 9)

**Reason accepted:** documentation-only, zero behavior change, directly addresses a legibility gap
both reviews independently raised.

**Change made** (`BreachCheckClient.java`):
- Javadoc on `isBreached` now states it's the only checked failure mode and that a `null` argument
  is a caller bug, not a fail-open condition.
- Inline comment added above the `retrieve()` call explaining that `RestClient` throws on non-2xx
  by default, so error responses are caught below, not passed to `responseContainsSuffix`.

---

## Rejected

### A — No unit tests exist yet (Kimi Finding 1)

**Reason rejected (for Phase 9):** by design, this pipeline splits implementation (Phase 6) from
test generation (Phase 10); Phase 6's own guardrail explicitly deferred tests. This is not a
Phase 9 code-defect fix — it's the next phase. Not rejected as a concern, just out of sequence.

### B — HIBP URI resolution unverified (Self-review Finding 1 + Kimi Finding 2)

**Reason rejected (for Phase 9):** both reviews agree the code is *likely* correct; what's missing
is a test proving it, not a code change. Phase 9's rule is "do not refactor, do not optimize" —
speculatively rewriting working URI-construction code with no test to confirm the rewrite is
actually better would be exactly that. Phase 10 must include the URI-assertion test both reviews
recommended (see `08-independent-review.md` Finding 2's recommendation) before this can be closed.

### D — `account`/`authn` two-way package dependency (Self-review Finding 3 + Kimi Finding 4)

**Reason rejected (for Phase 9):** both reviewers' own recommendation is "no T03 code change" —
the package placement was locked by the frozen brief (Phase 4), and unwinding it now would be a
scope-exceeding refactor of a decision already approved at the human gate. Logged as a future
cleanup item (hoist HIBP-specific config to an `authn`-owned record), not actioned here.

### G — `urlPrefix` required even when breach-check is disabled (Kimi Finding 7)

**Reason rejected:** conditional (enabled-only) validation is a meaningful complexity increase —
a class-level constraint keyed on another field's value — for a scenario the current verbatim
config block (locked by the frozen brief) already avoids by always supplying a non-blank default.
Not worth the added validation complexity Phase 9 discourages ("do not refactor, do not
optimize").

### J — Forward note for task 33 re: null account/actor context + `security.` prefix (Kimi Finding 10)

**Reason rejected:** Kimi's own recommendation is "No T03 code change" — this is already
documented in `04-frozen-task-brief.md`'s State Changes section (Finding 2 and Finding 4
dispositions from Phase 4). Nothing left to do here.

---

## Summary

5 accepted (C, E, F, H, I), applied to `PasswordPolicyProperties.java` and `BreachCheckClient.java`
— no public API changes, no renames, no refactoring beyond the targeted fixes above. 4 rejected
(A, B, D, G, J), each with a reason; A and B are carried forward as required work for Phase 10
rather than dropped. Both modified files recompiled successfully (verified via targeted `javac`
against the module's dependency classpath, bypassing the pre-existing unrelated `token`-package
build failure noted in `06-implementation-notes.md`).
