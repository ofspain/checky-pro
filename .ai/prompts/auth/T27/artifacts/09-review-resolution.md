<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T27 · Phase 9 — Review Resolution

**Human Approval gate. Decided by femi, 2026-08-16.** Consumes the self-review (`artifacts/07-self-review.md`, no findings) and independent review (`artifacts/08-independent-review.md`, 3 findings).

---

## Resolution Log

### 1. `GET /api-keys` responses parsed without asserting 200 first
*(Phase 8 Finding 1, new, High confidence)*

**ACCEPTED.** All three `GET /api-keys` calls in the sequence (steps 2, 4, 6) now capture the `ResponseEntity` and assert `getStatusCode() == HttpStatus.OK` before parsing the body as JSON — a regression on the list endpoint now fails with a clear status-code mismatch instead of a confusing `IllegalStateException` from `readJson`.

### 2. Byte-for-byte 401 comparison is brittle to a hypothetical future serialization change
*(Phase 8 Finding 2, new, Medium confidence)*

**REJECTED — keep the raw byte-for-byte check only.** AC3 is explicitly worded "byte-for-byte identical"; that is the actual acceptance criterion, not a stand-in for structural equality. A redundant parsed-map comparison would never catch anything the raw check doesn't already catch (if the raw strings match, the parsed maps trivially match too) — it would only add insurance against a hypothetical future Spring/Jackson serialization change, which would be visible and easy to diagnose on its own if it ever happened. No code change.

### 3. The two 401 responses never assert Content-Type or absence of `detail`
*(Phase 8 Finding 3, new, High confidence)*

**ACCEPTED.** Both the post-revocation exchange (step 7) and the malformed-key exchange (step 8) now assert `Content-Type` contains `application/problem+json` and the raw body does not contain a `"detail"` key, matching R46 and `ApiKeyCrudIntegrationTest`'s established pattern for this exact check.

---

## Build Verification (post-resolution)

`mvn -q -pl services/auth -am test-compile` — clean, exit 0. The file remains Testcontainers-backed and cannot be executed this session (Docker unavailable); the added assertions are logically sound against the known response shapes already verified elsewhere in this module (`ApiKeyExceptionHandlerTest.onExchangeRejectedReturnsUniform401` confirms `ProblemDetail`'s `detail` is `null`, which Jackson omits from the serialized JSON entirely — hence `doesNotContain("\"detail\"")` is the correct absence check, not a false negative risk).

---

## Files Touched This Phase

- `apikey/ApiKeyLifecycleIntegrationTest.java` — added 3 status assertions (GET calls) and 4 new assertions (Content-Type + no-detail on both 401 responses).

No other file changed. No file under `spec/` touched. No public API, class name, or method signature changed.

---

**Phase 9 complete — human sign-off given, accepted comments applied.** Proceed to Phase 10 (Test Generation).
