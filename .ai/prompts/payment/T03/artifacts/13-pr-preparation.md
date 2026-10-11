# payment · T03 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

**Note on process:** this task's own work was committed phase-by-phase to the long-lived
`spec/service-specs-and-ai-framework` branch, mirroring T01/T02's own and every sibling service's
own identical convention. `main` was never touched and stays deployable throughout.

## Commit title

`payment-service T03: config properties & resource server`

## Commit message

```
payment-service T03: config properties & resource server

Adds application.properties (port 8083, datasource as payment_app,
JPA, flyway disabled, Kafka, resource-server JWKS/issuer, actuator
exposure incl. show-details=never), five validated
@ConfigurationProperties records (CryptoClientProperties,
InvoiceProperties, ReceiptProperties - the two genuinely required
fields, LedgerAnchorProperties, InvoiceExpirySweepProperties),
PublicEndpoints (3 patterns), and ResourceServerConfig (JWT validation
against auth's JWKS, RFC 9457 401/403 bodies - the 403 proven by
direct handler invocation since no authority-restricted path exists
in this task's own scope).

One disclosed deviation from design.md's own verbatim config block:
receipt.bucket/receipt.kms-public-keys-url get a concrete, non-blank
local-only-fake default instead of an empty fallback, matching
crypto-service's own identical pattern for its analogous S3 field -
the literal empty fallback would fail validation in every profile
including local, contradicting this task's own AC5.

This task surfaced and fully disclosed a real process error of its
own: the test suite was written during Phase 6 (implementation)
instead of Phase 10, the exact rule correctly followed for T01 and
T02. Caught only while writing Phase 9's own resolution, after Phase
7/8 had already reviewed the merged diff - not hidden or quietly
smoothed over. Phase 11 was still run for real rather than waved off
as already covered, and found genuine value: three @NotBlank fields
and two numeric validation constraints across two of the five config
records had zero negative tests at all, now fixed and mutation-
verified.

Spec: spec/payment-service/tasks.md task 3. Requirement: R29. Locked
decisions: L10, L13 (with the same local-dev datasource carve-out
established in T01/T02).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/payment/src/main/resources/application.properties`
- `services/payment/src/main/java/com/themistra/payment/common/config/{CryptoClientProperties,
  InvoiceProperties,ReceiptProperties,LedgerAnchorProperties,InvoiceExpirySweepProperties}.java`
- `services/payment/src/main/java/com/themistra/payment/common/{PublicEndpoints,
  ResourceServerConfig}.java`
- `services/payment/src/test/java/com/themistra/payment/common/{ResourceServerTestController,
  ResourceServerConfigTest,AccessDeniedHandlerTest,PublicEndpointsTest,
  ApplicationPropertiesContentTest}.java`
- `services/payment/src/test/java/com/themistra/payment/common/config/ConfigPropertiesBindingTest.java`

**Modified**
- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java` —
  `@ConfigurationPropertiesScan`.
- `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java` —
  annotation-count assertion updated 1→2.

**Process artifacts (not application code)**
- `.ai/prompts/payment/T03/artifacts/00-*.md` through `12-*.md` (this file is `13-*.md`).

## Summary

This service's first `application.properties`, its first validated configuration surface, and
its first security filter chain — zero trust by construction before any business endpoint
exists. Pure foundational wiring, no entity/repository/controller code yet (those start at task
5). Two things worth a reviewer's attention beyond the usual: (1) one small, disclosed deviation
from `design.md`'s own verbatim config text, justified and precedented; (2) a real, fully-disclosed
process slip in how this task's own tests got written, which did not compromise the suite's own
correctness (independently confirmed by mutation testing throughout) but is worth reading
`09-review-resolution.md` for if auditing this task's history.

## Testing performed

- `mvn -pl services/payment compile` clean after each structural change.
- `ConfigPropertiesBindingTest`: 18/18, every field across all five records now has both a
  positive binding test and a negative validation test (Phase 11's own real finding, closed).
- `ResourceServerConfigTest` + `AccessDeniedHandlerTest` + `PublicEndpointsTest`: 401 and 403
  both proven RFC 9457-shaped, including the `type` field (Phase 8 finding 2); `PublicEndpoints
  .PATTERNS` directly asserted exact, plus a source-text sweep for stray `permitAll()` calls.
- `ApplicationPropertiesContentTest`: 7/7, covering AC2/AC6/AC7/AC8, the secret/YAML
  negative-proof, and the resource-server JWT property guard (Phase 8 finding 3).
- Full suite: **68/68 passing** (18 + 3 + 1 + 7 + 14 + 17 T02 + 8 T01).
- Mutation tests run across Phases 7, 9, and 11: `PublicEndpoints.PATTERNS` widening,
  `show-details` removal, `jwk-set-uri` removal, `@NotBlank`/`@PositiveOrZero` removal on four
  separate fields — every one confirmed to actually fail the relevant test, then reverted clean.

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 3 — "Config & resource server."
- **Requirement IDs:** R29 (RFC 9457 4xx shape).
- **LOCKED decisions:** L10 (zero trust), L13 (secrets discipline, with the established local-dev
  carve-out).

---

**Phase 13 complete — all 14 phases (0–13) closed for payment-service T03.**
