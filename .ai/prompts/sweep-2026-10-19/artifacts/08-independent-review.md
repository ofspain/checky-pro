# open-issues sweep · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 7 self-review and the commit summary against `spec/*/agents.md`, the platform rules, and the cross-service impact of the 21 fixes.

---

## Finding 1 — No end-to-end proof that the crypto unregister fix composes with payment

- **Issue:** Fix 1 changes crypto-service watcher behaviour so that unregistering at `SEEN` no longer stops `confirmed`/`finalized`/`reorged` tracking. The payment-service end-to-end test (`PaymentEndToEndIntegrationTest`) uses a WireMock stand-in for Crypto Service, so it cannot prove the real crypto fix works with the real payment flow. A regression in the cross-service contract would only be caught by separate crypto tests.
- **Severity:** Medium — the fix is correct per the crypto tests, but the integrated behaviour remains unproven.
- **Evidence:** Self-review fix 1; payment `PaymentEndToEndIntegrationTest` uses `WireMock` for crypto.
- **Recommendation:** Add a cross-service integration test that boots payment-service against a real crypto-service instance (or a contract-stub generated from `contracts/api/crypto-internal.yaml`) and drives a payment through `SEEN → FINALIZED`. If that is too heavy, record the gap as a follow-up and rely on the crypto test suite plus the contract tests.

---

## Finding 2 — Public-handler detection relies on substring `permitAll`

- **Issue:** This aligns with self-review S1. `ControllerRulesArchitectureTest.permitAllHandlers` treats a handler as public only if its `@PreAuthorize` expression contains the substring `permitAll`. An expression such as `@PreAuthorize("true")` or `@PreAuthorize("permitAll() and hasAuthority('X')")` could evade the count while opening the endpoint. The URL rule and `PublicEndpointsTest` provide a backstop, but the architecture guard itself is fragile.
- **Severity:** Low — the layered defences make exploitation unlikely.
- **Evidence:** Self-review S1.
- **Recommendation:** Replace the substring check with a Spring Security `SecurityExpressionParser` evaluation or require the exact expression to be one of a small allowlist (e.g., `permitAll()`). At minimum, assert the expression equals `permitAll()` literally for public handlers.

---

## Finding 3 — Crypto's public-handler allowlist check assumes GET

- **Issue:** This aligns with self-review S3. `allowlistedPublicHandlers` maps each non-actuator `PATTERNS` entry to `GET <path>`. A future public POST/PUT endpoint would fail the check and require a test change, which is safe but awkward.
- **Severity:** Low — no public POST exists today.
- **Evidence:** Self-review S3.
- **Recommendation:** Derive the allowed methods from the controller mappings or from the `PublicEndpoints` definition, rather than hard-coding GET.

---

## Finding 4 — No dedicated container startup smoke test

- **Issue:** This aligns with self-review S11. Fix 2 revealed that crypto-service could not start because `QuorumEvaluator` was not a bean. The only full-context starts are inside integration tests. A dedicated startup test (e.g., `@SpringBootTest` with no container dependencies, or a Docker health check) would catch bean wiring errors earlier and more cheaply.
- **Severity:** Medium — bean wiring failures are a class of error that integration tests may mask or miss.
- **Evidence:** Self-review S11; fix 2.
- **Recommendation:** Add a lightweight `@SpringBootTest` startup smoke test in each service that simply asserts the context loads. For payment/crypto/notification this also validates `ResourceServerConfig` and method-security wiring.

---

## Finding 5 — Notification's `ResourceServerTestController` is not profile-gated

- **Issue:** This aligns with self-review S6. A test-only controller sits in `com.themistra.notification.common` and is scanned into full-application test contexts, exposing `GET /v1/notifications/unread`. Crypto's equivalent was gated in fix 2. An ungated test controller is a latent security/maintenance risk.
- **Severity:** Low — it is only reachable in test contexts, but it should not be present in production-oid integration tests.
- **Evidence:** Self-review S6.
- **Recommendation:** Gate it behind a test-only profile (e.g., `@Profile("test-controller")`) or move it to `src/test/java`, matching crypto's fix.

---

## Finding 6 — Auth `AuditTrailIntegrationTest` flakiness is unaddressed

- **Issue:** This aligns with self-review S8. The test timed out once on a 15-second Kafka wait in the full run and passed alone. Flaky tests erode trust in the suite and may hide real regressions.
- **Severity:** Low — the flake is pre-existing and unrelated to the sweep.
- **Evidence:** Self-review S8.
- **Recommendation:** Increase the Kafka wait timeout, add a retry at the Awaitility level, or isolate the test's consumer group so it is not affected by earlier tests' offsets.

---

## Finding 7 — Parked-event expiry silently discards events

- **Issue:** This aligns with self-review S7. Fix 12 parks payment events that arrive before their route; fix 20 expires them if the route never arrives. An expired event is gone for good, visible only as a WARN log and a metric. There is no dead-letter table or retry for human review.
- **Severity:** Low — the behaviour is disclosed and out of scope.
- **Evidence:** Self-review S7; fix 20.
- **Recommendation:** Consider adding a small `parked_events_dead` table or extending `delivery_log` with a `SUPPRESSED`/`EXPIRED` row so that "we never notified the merchant" is auditable without relying on logs/metrics. If that is out of scope, add the limitation to `package.md` §9 item 6.

---

## Finding 8 — FU-1 layering rule is a proxy, not the stated architecture

- **Issue:** This aligns with self-review S4. The rule enforces controller → service → persistence direction because no module has `api`/`application`/`domain` packages. The `agents.md` note acknowledges this, but a future refactor into layered packages would require the rule to be rewritten.
- **Severity:** Low — the intent is clear and the rule is documented.
- **Evidence:** Self-review S4; `agents.md` notes.
- **Recommendation:** Keep the current rule but add a code comment in each `ControllerRulesArchitectureTest` explaining why the rule uses package depth rather than layer names, so a future restructure does not silently weaken it.

---

## Finding 9 — Cross-service FU-1/FU-2 port may have missed shared library code

- **Issue:** Fix 21 added `@PreAuthorize` to controllers in auth, crypto, and notification, and tied public endpoints to `PublicEndpoints`. If any shared library under `libs/` contains controllers or handlers consumed by these services, they would not be covered by the per-service architecture tests.
- **Severity:** Low — no shared controllers are known.
- **Evidence:** Fix 21; self-review scope.
- **Recommendation:** Run a quick `grep` for `@RestController`, `@Controller`, or `@RequestMapping` under `libs/` and either cover them with the same rules or document that they are not controllers.

---

## Finding 10 — Receipt read endpoint security should be proven beyond unit tests

- **Issue:** Fix 8 added the merchant-scoped receipt read endpoint. The guarding tests are unit/integration tests. There is no end-to-end test proving that a merchant JWT can read only its own receipt and a different merchant gets 403.
- **Severity:** Low — the tests likely cover it, but an E2E security test would close the loop.
- **Evidence:** Fix 8; `ReceiptControllerTest`.
- **Recommendation:** Add a security-focused integration test that creates a receipt for merchant A, authenticates as merchant B, and asserts 403; then authenticates as merchant A and asserts 200.

---

## Summary

The sweep is a strong, well-tested set of fixes. The most important follow-ups are Finding 1 (cross-service proof of the crypto unregister fix) and Finding 4 (startup smoke tests), because they prevent classes of failures that the existing suites have already shown can slip through. Findings 2, 3, and 5–10 are smaller robustness, security, and clarity notes.
