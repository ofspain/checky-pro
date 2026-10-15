# payment · T16 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

No code in this artifact — documentation only, per this phase's own instructions.

**Branching note:** same established practice as every prior task this session — executed
directly on the long-running `spec/service-specs-and-ai-framework` branch, not a fresh per-task
branch off `main`. `main` itself was never touched and stays deployable. The attribution trailer
below uses this session's own current identity (Claude Sonnet 5), not the generic placeholder
text baked into this prompt template's own header.

## Commit title

`payment T16: CanonicalJson + ReceiptDigest (R19 partial, L6/L7/L12)`

## Commit message

```
payment T16: CanonicalJson + ReceiptDigest (R19 partial, L6/L7/L12)

Adds CanonicalJson.serialize(Map<String,Object>) in common/ - a shared, deterministic
canonical-JSON serializer for both this task's own ReceiptDigest and task 19's LedgerService,
rather than two independently-written canonicalizers that could silently drift apart. Sorts
keys recursively at every nesting level, preserves list element order, and - the one real
design challenge this task surfaced (Kimi Phase 3) - validates every value against an explicit
allowed-type list (String/Boolean/null/Short/Integer/Long/Byte/Map/List) rather than trusting
callers, throwing IllegalArgumentException for anything else (UUID/Instant/BigDecimal/Float/
Double/BigInteger/arrays). Without this guard, a caller could silently depend on Jackson's own
version-sensitive default serialization for one of those types, undermining the entire point of
a reproducible digest.

Adds ReceiptPayload (new receipt package, a plain 12-field record over design.md O4's exact
shape, with compact-constructor null-checks on every field except fromAddress) and
ReceiptDigest.digest(...), which converts every field to a CanonicalJson-safe type before
building the map - except tokenDecimals, which O4's own pinned JSON example shows as a bare
number, unlike every other field shown quoted. ReceiptPayload deliberately imports neither
Invoice nor Payment (both @Entity classes in different feature modules) - the cross-module
entity-import ban (L1/L12) already enforced by ArchitectureTest. receiptUuid has no entity
source at all (no Receipt entity exists yet, task 17's own job) and is always caller-generated.

A real factual error in this task's own first-drafted Javadoc was caught and corrected during
independent review: the claim that Jackson's default ObjectMapper escapes non-ASCII characters
was backwards - verified directly against jackson-core's own JsonWriteFeature source
(ESCAPE_NON_ASCII disabled by default), so a default-configured mapper actually emits raw UTF-8
for non-ASCII content.

No Kimi Phase 11 artifact landed for this task; the test review was self-produced and held to
the same adversarial standard as a genuine pass (disclosed in Phase 12, not hidden), finding and
fixing 4 real gaps: Boolean (an explicitly supported type) and BigInteger/arrays (explicitly
rejected types) all had zero test coverage despite being named in the class's own contract, and
only 4 of ReceiptPayload's 12 fields had a dedicated digest-sensitivity test.

CanonicalJsonTest+ReceiptDigestTest: 29/29 passing. Full suite: 345/345 passing (316 pre-task
baseline + 29 new).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Production code:**
- `services/payment/src/main/java/com/themistra/payment/common/CanonicalJson.java` (new).
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptPayload.java` (new,
  first file in the new `receipt` package).
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptDigest.java` (new).

**Tests:**
- `services/payment/src/test/java/com/themistra/payment/common/CanonicalJsonTest.java` (new) —
  17 tests.
- `services/payment/src/test/java/com/themistra/payment/receipt/ReceiptDigestTest.java` (new) —
  12 tests.

No migration file — this task is wholly stateless, no entity, no persistence.

## Summary

T16 builds the one shared canonicalization primitive two later tasks (T17's receipt issuance,
T19's hash-chain ledger) both need, closing a real foot-gun along the way: a generic,
reusable `Map<String,Object>` serializer is only as trustworthy as its own type guard, and the
first draft relied entirely on caller discipline until Kimi's Phase 3 design challenge pointed
out the risk. The harder design question — how to keep a digest's own byte-for-byte
reproducibility independent of Jackson's version-specific defaults for `UUID`/`Instant`/
`BigDecimal` — was resolved by converting every such value to its canonical string/number form
at `ReceiptDigest`'s own boundary, so `CanonicalJson` itself never needs a date/UUID-aware
Jackson module at all.

## Testing performed

- `mvn -o -pl services/payment -am compile`, `mvn -o -pl services/payment -am test-compile`:
  clean throughout every phase (one self-caught Javadoc unicode-escape compile error at Phase 6,
  fixed immediately).
- `mvn -o -pl services/payment -am test -Dtest=CanonicalJsonTest,ReceiptDigestTest`: **29/29
  passing**.
- `mvn -o -pl services/payment -am test` (full suite, Docker up —
  `services/auth/compose.local.yaml`): **345/345 passing** (316 pre-task baseline + 29 new).
- Mutation-tested the core type guard twice: once at Phase 10 (removed the final `throw`,
  confirmed 5 rejection tests failed red, reverted) and the underlying logic re-exercised by
  Phase 11's own 3 new type-coverage tests (`Boolean` positive, `BigInteger`/array negative).

## Specification references

- **Task:** T16 — Receipt digest (`spec/payment-service/tasks.md`).
- **Requirement IDs:** R19 (partial — the digest-computation mechanism only; the full
  request/decide/record flow is T17's own scope).
- **LOCKED decisions:** L6 (money as decimal string on the wire), L7 (context — the shared
  canonicalizer T19 will also use), L12 (module boundaries — `common`/`receipt` placement, no
  cross-module entity import).
- **Design reference:** `design.md` O4 — the exact 12-field canonical receipt payload shape.
