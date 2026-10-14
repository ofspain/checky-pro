# payment · T07 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

**Note on process:** this task's own work was committed phase-by-phase to the long-lived
`spec/service-specs-and-ai-framework` branch, mirroring every prior task's own identical
convention. This artifact is the consolidated summary of that already-committed history, not a
pending commit — `main` was never touched and stays deployable throughout.

## Commit title

`payment-service T07: merchant-scoped invoice reads (GET /api/v1/invoices)`

## Commit message

```
payment-service T07: merchant-scoped invoice reads (GET /api/v1/invoices)

Adds GET /api/v1/invoices and GET /api/v1/invoices/{invoiceUuid}, both
ROLE_MERCHANT-only. InvoiceNotFoundException (package-private, mirrors
auth-service's own SessionNotFoundException exactly) is deliberately the
same exception for "doesn't exist" and "exists but isn't the caller's" -
no enumeration oracle (R28/L10), verified at both the service layer (same
exception type for both causes) and the controller/handler layer
(byte-for-byte identical response body for both causes).

InvoiceResponse extended to the full merchant-facing view: amount as a
decimal string (agents.md's own standing wire-format rule, caught at
Phase 3 - the Phase 2 TIB had missed it), watchId deliberately excluded
(Crypto Service's own internal identifier, no merchant-facing meaning).
listForMerchant/getForMerchant are @Transactional(readOnly = true).

The simplest task in this pipeline since T05: Phase 7's self-review found
nothing; Phase 8's Kimi review found zero code defects, only missing test
coverage (expected pre-Phase-10); Phase 11's Kimi review found 8 real
coverage gaps (ordering, full-field-set assertions, RFC 9457 body shapes,
malformed path variables, unauthenticated reads, invocation-argument
verification, null-field serialization), all fixed. Kimi's own Phase 8 run,
in a Docker-enabled environment, also empirically confirmed T06's own V3
migration-test fix (18/18 passing) that could not be re-run in that
session.

Spec: spec/payment-service/tasks.md task 7. Requirements: R28. Locked
decisions: L10.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceNotFoundException.java`
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceReadTest.java`

**Modified**
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceController.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExceptionHandler.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/dto/InvoiceResponse.java`
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceControllerTest.java`

**Process artifacts (not application code)**
- `.ai/prompts/payment/T07/artifacts/00-*.md` through `12-*.md`.

## Summary

Merchants can now read back their own invoices — a list and a single-resource lookup — with no
way to distinguish a nonexistent invoice from one owned by another merchant. No receipt
resource exists yet (R28's own receipt half is a later task).

## Testing performed

- `mvn -pl services/payment -am compile` — clean throughout.
- 18 new tests added this task (10 `InvoiceServiceReadTest`, 8 net new in
  `InvoiceControllerTest` after Phase 10+11 combined).
- **141/141 non-Docker-dependent tests passing**, up from 123 before this task.
- Mutation tests across Phases 10/11 on: `InvoiceResponse`'s `watchId` exclusion, the
  `InvoiceExceptionHandler`'s identical-404-body guarantee, and `listForMerchant`'s ordering
  pass-through — each caught its introduced defect, each reverted clean (via `Edit`, not
  `git checkout`, for anything not yet committed).

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 7 — "Merchant-scoped reads."
- **Requirement IDs:** R28.
- **LOCKED decisions:** L10.

---

**Phase 13 complete — all 14 phases (0–13) closed for payment-service T07.**
