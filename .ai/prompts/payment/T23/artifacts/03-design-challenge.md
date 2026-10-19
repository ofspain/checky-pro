# payment · T23 · Phase 3 — Design Challenge

Findings from an adversarial review of `artifacts/02-task-implementation-brief.md` (TIB) against `spec/payment-service/agents.md`, `spec/payment-service/requirements.md` (R29), the current codebase, and `auth-service`'s existing `ProblemTypes` precedent.

---

## Finding 1 — The `ProblemTypes` base URI is not frozen

- **Issue:** The TIB says the new class should mirror `auth-service`'s `ProblemTypes` shape exactly, including a shared `BASE` prefix, but it never states what that prefix is. An implementer could pick a different base (e.g., `https://themistra.com/problems/`), producing payment-service problem types that are inconsistent with the auth-service catalogue and any future `contracts/api/payments.yaml`.
- **Severity:** Medium — wrong or inconsistent URIs are a backwards-compatibility hazard once published.
- **Evidence:** `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` line 11 uses `BASE = "https://checky.pro/problems/"`; TIB Locked Decisions only describes suffixes and kebab-case.
- **Recommended brief amendment:** Pin the base exactly: `private static final String BASE = "https://checky.pro/problems/";`.

---

## Finding 2 — `RESOURCE_NOT_FOUND` diverges from the auth precedent the brief claims to mirror

- **Issue:** The TIB explicitly says the 404 type should mirror auth-service's generic, reusable `NOT_FOUND` constant. It then names the constant `RESOURCE_NOT_FOUND` with URI suffix `resource-not-found`, which is neither the auth name nor the auth URI. This contradicts the stated intent and creates a platform inconsistency.
- **Severity:** Medium — a 404 is a common error shape; diverging URIs make client error-catalogue work harder.
- **Evidence:** `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` line 15 defines `NOT_FOUND` → `.../not-found`; TIB Locked Decisions defines `RESOURCE_NOT_FOUND` → `.../resource-not-found`.
- **Recommended brief amendment:** Rename to `NOT_FOUND` (`not-found`) to match auth-service, or explicitly document why payment-service needs a different URI.

---

## Finding 3 — AC2's "zero literal `"about:blank"`" rule is ambiguous about comments

- **Issue:** AC2 says there must be zero remaining literal `"about:blank"` strings anywhere in `services/payment/src/main/java`. Several Javadoc comments currently contain the literal string (e.g., `ApiExceptionHandler` line 30, `InvoiceExceptionHandler` line 17). A mechanical replacement of call sites would leave these comments in place, making AC2 either fail or require an exception.
- **Severity:** Low — no runtime impact, but it weakens the enforceability of the acceptance criterion.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java` line 30; `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExceptionHandler.java` line 17.
- **Recommended brief amendment:** Clarify AC2: either (a) update or remove every Javadoc reference to `"about:blank"`, or (b) restrict the hunt to executable call sites and document the exception.

---

## Finding 4 — No governance rule for adding future problem types

- **Issue:** The TIB states the catalogue is "stable" and that new types should be added to `ProblemTypes`, but it does not say when a new type is required, who owns the URI namespace, or how it gets into the API contract. Without this, a future task might inline a new URI or reuse an existing one inappropriately.
- **Severity:** Low — not a blocker for T23, but the whole point of this task is to prevent ad-hoc types.
- **Evidence:** TIB Constraints / Outputs.
- **Recommended brief amendment:** Add a constraint: "Every new problem type must be added to `ProblemTypes` and referenced from `contracts/api/payments.yaml` (task 24). Inlined URIs at call sites are forbidden. Changing a published URI requires introducing a new constant and deprecating the old one."

---

## Finding 5 — The eleven-constant catalogue may be incomplete for future 5xx surfaces

- **Issue:** The brief covers the existing handlers, including `CRYPTO_SERVICE_UNAVAILABLE` (503) and `INTERNAL_ERROR` (500). However, R29's "no internal detail" rule applies to 5xx as well as 4xx. If a future task adds another upstream-dependency failure (e.g., a notification or S3 failure handled in application code), it will need its own type or a generic upstream-error type. The brief does not reserve such a type.
- **Severity:** Low — speculative.
- **Evidence:** `agents.md` Security rule; R29.
- **Recommended brief amendment:** Either accept that future upstream failures will add types as needed, or add a generic `UPSTREAM_ERROR`/`DEPENDENCY_UNAVAILABLE` type now for use by any non-crypto dependency failure.

---

## Finding 6 — `ResourceServerConfig.writeProblemJson` helper signature is not frozen

- **Issue:** The TIB says the helper should "now take the `type` as a parameter rather than hardcoding it," but it does not specify the exact signature. A signature like `writeProblemJson(..., URI type)` vs. `writeProblemJson(..., String type)` affects whether callers must import `ProblemTypes` or can still pass arbitrary strings.
- **Severity:** Low — easy to fix, but worth freezing for consistency.
- **Evidence:** TIB Files to Modify; `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java` lines 97-108.
- **Recommended brief amendment:** Freeze the signature as `private static void writeProblemJson(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, URI type, String title, String detail)` so the helper enforces catalogue use via the `URI` type.

---

## Finding 7 — Test list assumes exact counts but does not identify the six `InvoiceControllerTest` sites

- **Issue:** The TIB lists "6 in `InvoiceControllerTest`" that need mechanical `$.type` updates, but it does not name the tests. Whoever does Phase 10 will have to hunt them down, risking missed assertions or inconsistent expected values.
- **Severity:** Low — purely mechanical.
- **Evidence:** TIB Required Tests / Files to Modify.
- **Recommended brief amendment:** Either enumerate the six test names after a quick scan, or add a Phase-10 note to grep for `jsonPath("$.type").value("about:blank")` in `InvoiceControllerTest`.

---

## Summary

The T23 brief is a low-risk, mechanical refactor with a clear scope. The most important amendments before freezing are (1) pinning the exact `BASE` URI and (2) aligning the 404 constant name/URI with auth-service. Gaps 3–7 are smaller process/clarity items that should be dispositioned at the Phase 4 gate.
