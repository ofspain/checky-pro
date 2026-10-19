# payment · T23 · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 6 implementation and Phase 7 self-review against the frozen brief (`artifacts/04-frozen-task-brief.md`), `spec/payment-service/agents.md`, and `spec/payment-service/requirements.md` (R29).

---

## Finding 1 — Two historical Javadoc references to `"about:blank"` remain in `main/java`

- **Issue:** AC2 says zero literal `"about:blank"` strings anywhere in `services/payment/src/main/java`. The self-review correctly notes that only two Javadoc mentions remain and zero are executable, but the acceptance criterion as written does not distinguish code from comments. A strict reading of AC2 would fail.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java` line 29; `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExceptionHandler.java` line 17.
- **Recommendation:** Either rephrase those Javadoc lines to avoid the literal string (e.g., "originally a uniform placeholder URI from Phase 9") or update AC2 to explicitly exempt historical Javadoc references.
- **Confidence:** Low — no runtime effect, but it makes the acceptance criterion ambiguous.

---

## Finding 2 — No direct unit test for `ProblemTypes` itself

- **Issue:** The brief requires a test that every constant is a distinct, well-formed `URI` except the two intentionally-shared pairs, and that the class cannot be instantiated. No such test exists in the repository.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/common/ProblemTypesTest.java` does not exist; glob for `ProblemTypesTest` returns nothing.
- **Recommendation:** Add `ProblemTypesTest` in Phase 10 with assertions for: every constant is a non-null absolute URI; `VALIDATION_ERROR` and `FORBIDDEN` are each referenced by exactly two call sites; no two constants share the same URI string except those two; and `ProblemTypes` has a private constructor (cannot be instantiated via reflection).
- **Confidence:** High — this is an explicit acceptance-criterion test from the brief.

---

## Finding 3 — `CryptoServiceUnavailableException` is handled only by `InvoiceExceptionHandler`

- **Issue:** `CryptoServiceUnavailableException` lives in `common`, but only `InvoiceExceptionHandler` maps it to `ProblemTypes.CRYPTO_SERVICE_UNAVAILABLE` (503). Any other REST endpoint that calls `CryptoClient` and fails would fall through to `ApiExceptionHandler`'s catch-all and become `INTERNAL_ERROR` (500) with a generic title, losing the 503 semantics and the stable catalogue type.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExceptionHandler.java` lines 36-43; `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java` lines 122-133. Today no other controller calls `CryptoClient`, so this is latent.
- **Recommendation:** Move the `CryptoServiceUnavailableException` handler to `ApiExceptionHandler` and make the `detail` message generic ("An upstream dependency is unavailable."), or keep it in `InvoiceExceptionHandler` and document the latent risk for future non-invoice callers.
- **Confidence:** Medium — correctness is preserved today, but the handler placement contradicts the exception's `common` package location.

---

## Finding 4 — Existing tests still assert the old `"about:blank"` type

- **Issue:** Seven existing assertions in `InvoiceControllerTest`, `ResourceServerConfigTest`, and `AccessDeniedHandlerTest` still expect `"about:blank"`. The self-review confirms exactly these seven are failing. This is expected given the task's design, but it means the codebase is not green until Phase 10 updates them.
- **Evidence:** `grep "about:blank" services/payment/src/test/java` returns 8 matches across 3 files.
- **Recommendation:** Phase 10 must update each assertion to the matching `ProblemTypes` URI string (e.g., `https://checky.pro/problems/invalid-invoice-request`, `.../unauthorized`, `.../forbidden`) and add `$.type` assertions to `ExportControllerTest`'s 4xx tests as the brief requires.
- **Confidence:** High — this is the declared, mechanical follow-up work.

---

## Finding 5 — No automated guard against new inline URI literals

- **Issue:** AC5 requires that no call site outside `ProblemTypes.java` contains `URI.create(`. The self-review used a manual `grep`, which is effective once but not enforced on every commit. A future task could accidentally inline a new URI.
- **Evidence:** TIB AC2/AC5; self-review "grep" note.
- **Recommendation:** Add a lightweight CI check (e.g., a shell script in `.github/scripts` or a Maven-enforcer-style test) that fails the build if `URI.create(` appears outside `ProblemTypes.java` in `services/payment/src/main/java`, or add an ArchUnit rule that forbids `URI.create` calls outside the `ProblemTypes` class.
- **Confidence:** Low — process hygiene, not a current bug.

---

## Finding 6 — The named test `shouldReturnProblemJsonWithNoInternalDetailOn4xx` does not exist yet

- **Issue:** The brief's named test asserts a representative 4xx body's full RFC 9457 shape, including a non-`about:blank` catalogue type. No test with that name exists in the repository.
- **Evidence:** `grep -R "shouldReturnProblemJsonWithNoInternalDetailOn4xx" services/payment/src/test` returns nothing.
- **Recommendation:** Add the named test in Phase 10, e.g., as a `MockMvc` test that triggers `InvalidInvoiceRequestException` and asserts `type`, `title`, `status`, and `detail`, plus the absence of any stack-trace or exception-class fields.
- **Confidence:** High — this is the named test from the brief.

---

## Summary

The T23 implementation is a faithful, mechanical replacement of placeholder types with the stable catalogue. The only real risks are (1) the ambiguous AC2 wording around Javadoc literals, and (2) the missing tests (`ProblemTypesTest`, the named R29 test, and the test assertion updates). The `CryptoServiceUnavailableException` handler placement is a latent maintainability issue worth documenting. No production code changes are needed before Phase 9 unless the Phase 4 gate wants the Javadoc literals cleaned up.
