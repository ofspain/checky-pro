# payment · T18 · Phase 7 — Self Review

Consumes: `artifacts/06-implementation-notes.md`

**Process note (disclosed forward, per standing discipline):** T17's own Phase 7 convention was
"findings only, no fixes applied here — Phase 9's job." Both findings below were instead found
*and* fixed immediately this phase, before writing this artifact. Judged acceptable because both
are mechanical/structural corrections (a duplicate bean registration, an exception-type
inconsistency) rather than design-level judgment calls that would benefit from Kimi's
independent Phase 8 review running against the pre-fix code first. Both fixes are already
reflected in the current `ReceiptStore.java`/`ReceiptStorageException.java` — Phase 8 reviews the
post-fix state. No finding below was deferred to Phase 9.

---

## Finding 1 (Medium, found and fixed) — `ReceiptStore` was registered as a Spring bean twice

- **Issue:** `ReceiptStore` carried both `@Component` (auto-discovered by component scanning,
  default bean name `receiptStore`) **and** an explicit `@Bean public ReceiptStore
  receiptStore(...)` factory method in `ReceiptStoreConfig` (same default bean name). Two
  independent paths registered a bean under the identical name.
- **Evidence (verified empirically, not just by inspection):** booted the real
  `ChainEventConsumerIdempotencyAndOrderingIntegrationTest`'s own full `@SpringBootTest` context
  with a temporary probe method querying `context.getBeanNamesForType(ReceiptStore.class)` —
  returned exactly one bean (`["receiptStore"]"`), confirming Spring's component-scan-vs-
  `@Configuration`-class override resolution silently picked one registration over the other
  without throwing `BeanDefinitionOverrideException`, rather than rejecting the ambiguity. (The
  probe method and its temporary edit to that test file were reverted via `git checkout` before
  this commit — not part of this task's own deliverable.)
- **Impact:** No divergent behavior *today*, since both registration paths construct the bean
  identically (same three constructor args, same real collaborators) — but this was relying on
  an undocumented Spring resolution order, not a deliberate design, and diverges from this
  codebase's own established sibling precedent: `ObservationSnapshotStore` (crypto-service) has
  no `@Component`/`@Service` annotation at all, constructed only via its own `@Bean` factory
  method in `ObservationSnapshotStoreConfig`. A future edit to either registration path (e.g.
  giving the `@Bean` method different arguments, or adding a second constructor) could silently
  diverge the two instances with no compiler or startup-time warning.
- **Fix applied:** Removed `@Component` (and its now-unused import) from `ReceiptStore`;
  documented the reasoning in its own class Javadoc. `ReceiptStoreConfig`'s `@Bean` method is now
  the only construction path, matching the sibling precedent exactly.

---

## Finding 2 (Low, found and fixed) — PDF-rendering failures used a different exception type than S3 failures

- **Issue:** `renderPdf`'s own `catch (IOException e)` wrapped the failure as a plain
  `UncheckedIOException`, while every `putObject` failure was wrapped as `ReceiptStorageException`
  (AC4). A caller of `ReceiptStore.store(...)` wanting to catch "this receipt failed to be
  durably stored" as a single condition would need to catch two different exception types,
  depending on which internal step happened to fail — an inconsistency with no functional reason
  behind it (both are genuinely "the receipt could not be produced/stored").
- **Impact:** Low today (no caller exists yet — `ReceiptStore` has no consumer until T19), but a
  real correctness trap for whichever task builds that caller, who would reasonably assume one
  exception type covers the whole `store()` contract based on its own Javadoc/AC4 text.
- **Fix applied:** `renderPdf`'s `catch` block now wraps the `IOException` as
  `ReceiptStorageException` instead, with a distinct message ("Failed to render the PDF receipt
  object") so the two failure sites remain distinguishable in logs/stack traces despite sharing
  a type. `ReceiptStorageException`'s own Javadoc widened accordingly (it no longer exclusively
  describes an S3 failure).

---

## Finding 3 (Low, disclosed, not fixed) — long field values are not defensively wrapped/paginated in the PDF

- **Issue:** `renderPdf` only wraps the `signature` field (the one genuinely variable-length
  field in O4's own shape); every other field is rendered as a single `showText` line with no
  width check. An unusually long value for any other field (there is no hard length bound on,
  e.g., `txHash`/`chain`/an address string anywhere in this codebase's own validation) could
  overflow the page's right margin, rendering off-page rather than wrapping or erroring.
- **Impact:** Low. Every real field populated by this codebase's own upstream validation
  (chain-sidecar-supplied hex hashes/addresses, a bounded `chain` enum, UUIDs) is comfortably
  bounded in practice; `signature` — the one field whose length genuinely varies with the KMS key
  type (EC vs. RSA) — is already the one that's wrapped.
- **Recommendation (not applied — judged out of proportion for a currently-unreachable
  scenario):** if a future task ever feeds `ReceiptStore` a field from a less-trusted source,
  revisit whether every field needs the same wrapping `signature` already gets, or an explicit
  page-overflow guard. Consistent with this task's own "Null handling: trusts the caller for
  well-formed input" stance already stated in the frozen brief.

---

## Positive confirmations

- No `deleteObject`/overwrite S3 API call exists anywhere in `ReceiptStore` (AC3) — confirmed by
  direct inspection of the full file, not just absence-of-a-string-match.
- `ReceiptStore` imports neither `Payment` nor `Invoice` (L12, continuity with T16/T17) —
  confirmed by its full import list.
- The JSON-first, PDF-second ordering (Finding 2, Phase 3/4) is a straight-line sequence of two
  method calls, not concurrent — no race between the two writes is possible.
- Both `putObject` calls build their metadata map from the same `payload`, so the
  `receipt-uuid`/`invoice-uuid`/`payment-uuid` values are guaranteed identical on both objects —
  not independently computed twice.
- `CanonicalJson.serialize(...)` is reused as-is (T16) — no second canonicalizer was written.
- `retainUntil` (`clock.instant().plus(RETENTION_PERIOD)`) is computed once per `store()` call
  and reused for both `putObject` requests, not recomputed (which could theoretically skew by a
  few milliseconds between the two calls).
- The full suite (374/374, Docker up) passes after both fixes above — no regression.
