# payment · T10 · Phase 11 — Test Review Resolution

Consumes `artifacts/11-test-review.md` (Kimi 2.7). 3 of 5 findings fixed, 1 acknowledged as a
known, disproportionate-to-fix limitation, 1 acknowledged as already disclosed.

## Gap 1 — `getDetail()` unmodifiability not fully proven

**ACCEPTED and FIXED — with a real self-caught bug along the way.** Added
`getDetailShouldReturnAnUnmodifiableMap`. First version used `Map.of("reason", "stale")` as the
input — already an unmodifiable map on its own, so the test would have passed even with *zero*
protection from `VerificationRecord` itself (confirmed empirically: removing both of Finding 1's
own `copyOf` calls left this first version green, a false sense of coverage). Rewrote it to pass
a genuinely mutable `HashMap`, re-confirmed red with both `copyOf` calls removed, then green with
them restored.

## Gap 2 — Integration tests unverified locally

**ACKNOWLEDGED, no new action.** Already disclosed at Phase 4/10 as a carried-forward,
Docker-unavailable gap. Kimi's own two hypothesized failure modes (Flyway-as-`payment_app`
ordering, JSON-deserialization map-equality) are both already addressed by the existing design
(`@BeforeAll` migrates as the admin role before `payment_app`'s password is even set;
`containsExactlyInAnyOrderEntriesOf` compares by entries, not map-implementation identity) — no
code change needed, noted for whoever runs this test next with Docker available.

## Gap 3 — `repository.save` invocation count not asserted

**ACCEPTED and FIXED.** Added `verify(repository).save(any(VerificationRecord.class))` to
`shouldRecordAnOrdinaryForwardTransition` and `shouldIgnoreOutOfOrderOrStaleChainEvents`.
Mutation-verified: made `record` return the unsaved object directly (bypassing
`repository.save` entirely), confirmed both tests go red, reverted.

## Gap 4 — Source-text guard could miss indirect delete calls

**ACKNOWLEDGED as a known, low-risk limitation — not fixed.** Real gap (method references,
static imports), but no code anywhere in this codebase calls a repository method any way other
than `repository.<verb>(...)`, so the suggested ArchUnit structural rule would guard against a
calling convention that doesn't exist yet anywhere in this service. Disproportionate for a
Low-severity/Low-confidence finding against a hypothetical. Revisit if this codebase ever adopts
method-reference-style repository calls.

## Gap 5 — No test proves `record` itself accepts a null `fromState`

**ACCEPTED and FIXED.** Added `shouldRecordTransitionWithNullFromState`. Mutation-verified: added
`Objects.requireNonNull(fromState, ...)` to `record`, confirmed red (this one test plus several
others that call `recordIgnored`, which itself passes `fromState = null`), reverted.

## Verification performed this phase

- `mvn -pl services/payment -am test-compile` — clean.
- `mvn -pl services/payment -am test -Dtest='VerificationRecordServiceTest'` — 13/13 (up from
  11).
- Mutation-verified all 3 fixed gaps, each via `Edit`, confirmed red, reverted:

| Mutation | Test(s) that caught it |
|---|---|
| Both of `VerificationRecord`'s own `copyOf` calls removed (create + getter) | `getDetailShouldReturnAnUnmodifiableMap` (after the self-caught `Map.of` test-writing bug was fixed) |
| `record` returns the unsaved object, bypassing `repository.save` | `shouldRecordAnOrdinaryForwardTransition`, `shouldIgnoreOutOfOrderOrStaleChainEvents` |
| `record` added a null-check on `fromState` | `shouldRecordTransitionWithNullFromState` (and several others via `recordIgnored`) |

- Full non-Docker suite: **196/196 passing** (up from 194).

## Net result

3 real gaps fixed (one of which surfaced a genuine bug in my own first attempt at the fix,
caught before it could ship), 1 acknowledged as a disproportionate-to-fix low-risk limitation, 1
re-acknowledged as already disclosed. Proceeding to Phase 12.
