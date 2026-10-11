# payment · T03 · Phase 11 — Test Review

Produced directly (genuine, fresh pass through the "do the tests actually verify the
specification" lens — missing cases, weak assertions, flakiness, duplicate tests, coverage gaps —
not a redundant repeat of Phase 8's own correctness-focused review, despite the process deviation
disclosed in `09-review-resolution.md`/`10-test-generation.md`).

---

### 1. Three `@NotBlank` fields on `CryptoClientProperties` have zero negative tests

- **Gap:** `baseUrl`, `attestPath`, and `watchPath` are all `@NotBlank`
  (`CryptoClientProperties.java:17-19`), but `ConfigPropertiesBindingTest` only has one negative
  test for this record (`connectTimeoutMs`'s `@Positive`). None of the three string fields' own
  validation rule is ever exercised failing.
- **Why it matters:** a typo'd or accidentally-removed `@NotBlank` annotation on any of these
  three fields would go completely undetected — the positive binding test only proves valid input
  binds, never that invalid input is rejected.
- **Suggested test:** one negative test per field (or a parameterized one), each asserting
  `rootCause().isInstanceOf(BindValidationException.class).hasMessageContaining(<fieldName>)`.

### 2. `readTimeoutMs`'s `@Positive` constraint is untested

- **Gap:** Same record, same class of gap: `connectTimeoutMs` has a negative test,
  `readTimeoutMs` does not, despite carrying the identical `@Positive` annotation.
- **Why it matters:** asymmetric coverage of two fields with the same validation rule invites the
  same regression on whichever one isn't tested.
- **Suggested test:** a `readTimeoutMs`-specific negative test, mirroring the existing
  `connectTimeoutMs` one.

### 3. `InvoiceProperties.amountToleranceBaseUnits`'s `@PositiveOrZero` is untested

- **Gap:** Only `defaultTtlMinutes`'s `@Positive` has a negative test; the sibling field's own
  `@PositiveOrZero` constraint (allowing 0, rejecting negative) is never exercised failing.
- **Why it matters:** same reasoning as findings 1-2 — an untested validation annotation is one
  silent regression away from doing nothing.
- **Suggested test:** a negative test with a negative value (e.g. `-1`), confirming rejection,
  plus (implicitly already covered by the existing positive test using `0`) that zero itself is
  accepted.

### 4. `receiptPropertiesBindFromValidConfiguration` only asserts one of the two bound fields

- **Gap:** The positive binding test for `ReceiptProperties` asserts `props.bucket()` but never
  `props.kmsPublicKeysUrl()`.
- **Why it matters:** low severity — the field's own *validation* is well-tested elsewhere (the
  blank/missing tests), but the *binding* of a valid value specifically for this field is never
  independently confirmed.
- **Suggested test:** add the missing assertion to the existing test, not a new one.

### 5. (Minor, no fix applied) Overlapping coverage between `ResourceServerConfigTest` and `PublicEndpointsTest`

- **Gap:** Both classes assert `/actuator/health` is reachable without a token —
  `ResourceServerConfigTest.publicActuatorHealthPathIsReachableWithoutAToken` and
  `PublicEndpointsTest.declaredPublicPathsAreNotBlockedBySecurity` (one of its 5 parameterized
  cases).
- **Why it matters:** harmless duplication, not a correctness risk — flagged for the record, not
  actioned. `PublicEndpointsTest`'s own version is the more complete one (covers 5 paths, added
  specifically to close Phase 7 finding 1); `ResourceServerConfigTest`'s narrower version predates
  it and could be removed for tidiness in a future pass, but removing test coverage is not this
  phase's own job to decide unilaterally.

---

## Resolution (applied directly, findings 1-4; finding 5 documented only)

1. **Accepted, fixed:** added `cryptoClientPropertiesFailWhenBaseUrlIsBlank`,
   `cryptoClientPropertiesFailWhenAttestPathIsBlank`,
   `cryptoClientPropertiesFailWhenWatchPathIsBlank`.
2. **Accepted, fixed:** added `cryptoClientPropertiesFailWhenReadTimeoutIsNotPositive`.
3. **Accepted, fixed:** added `invoicePropertiesFailWhenAmountToleranceIsNegative`.
4. **Accepted, fixed:** added the missing `kmsPublicKeysUrl()` assertion to the existing test.
5. **Documented, not fixed** — see reasoning above.

Each new negative test mutation-verified (remove the annotation it guards, confirm the test now
fails, restore, confirm clean) — ran this for the `baseUrl`/`@NotBlank` and
`amountToleranceBaseUnits`/`@PositiveOrZero` cases specifically, both confirmed real. Final:
`mvn -pl services/payment test` → **68/68 passing** (18 config + 3 resource-server + 1
access-denied + 7 properties-content + 14 public-endpoints + 17 T02 + 8 T01).
