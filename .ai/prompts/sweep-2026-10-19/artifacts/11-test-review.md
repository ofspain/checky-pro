# open-issues sweep · Phase 11 — Test Review

Gaps found by reviewing the sweep's 21 fixes, their guarding tests, and the suite state against the cross-service risks they address. There is no Phase 10 manifest for the sweep, so this review focuses on what is not yet pinned by a committed test.

---

## Gap 1 — No cross-service integration test for the crypto unregister fix

- **Why it matters:** Fix 1 changes crypto-service watcher behaviour so that `SEEN` no longer stops `confirmed`/`finalized`/`reorged` tracking. The payment-service E2E uses a WireMock stand-in for crypto, so the real interaction is unproven.
- **Suggested test:** Add a cross-service integration test that boots crypto-service and payment-service together (or uses a contract-stub generated from `contracts/api/crypto-internal.yaml`) and drives `invoice.created → watch registered → seen → confirmed → finalized → receipt.issued` end-to-end.

---

## Gap 2 — No container startup smoke test

- **Why it matters:** Fix 2 revealed crypto could not start because `QuorumEvaluator` was not a bean. The only full-context starts are inside integration tests, which may hide wiring failures behind other setup.
- **Suggested test:** Add a lightweight `@SpringBootTest` in each service that asserts the context loads with no container dependencies. For crypto this would have caught the missing bean.

---

## Gap 3 — No E2E security test for merchant-scoped receipt read

- **Why it matters:** Fix 8 added the merchant receipt read endpoint. Unit and integration tests guard it, but there is no end-to-end test proving a merchant JWT can read only its own receipt.
- **Suggested test:** Add an integration test that creates a receipt for merchant A, authenticates as merchant B, asserts 403, then authenticates as merchant A and asserts 200.

---

## Gap 4 — No test verifies the new email link paths render end-to-end

- **Why it matters:** Fix 7 changed notification email links to `/app/verify?token=`, `/app/reset?token=`, `/app/invoices/<uuid>`, `/app/receipts/<uuid>`. The tests verify the `TemplateRenderer` output, but no test sends a real event through Kafka and inspects the captured email body for the new paths.
- **Suggested test:** Extend `TemplateRendererIntegrationTest` or add a Kafka-driven test that consumes an `auth.email.requested`/`payments.receipt.issued` event and asserts the captured email body contains the expected `/app/...` URL.

---

## Gap 5 — No test verifies the built Docker images actually start

- **Why it matters:** Fix 13 made the three sibling Dockerfiles build. A successful build does not prove the image runs, especially after adding missing module poms.
- **Suggested test:** Add a smoke run for auth/crypto/notification images (similar to payment T27 D3) that starts the image with `--read-only` and asserts `/actuator/health` returns `UP`.

---

## Gap 6 — No test verifies FU-1/FU-2 rules cover shared library code

- **Why it matters:** Fix 21 added `@PreAuthorize`/layering rules per service. Controllers or handlers in `libs/` would not be analysed by the per-service `ControllerRulesArchitectureTest`.
- **Suggested test:** Run a `grep` for `@RestController`/`@Controller` under `libs/` as part of the sweep's guard suite, or add a note that no shared controllers exist.

---

## Gap 7 — No test verifies the parked-event expiry metric is emitted

- **Why it matters:** Fix 20 expires parked events whose route never arrives. The only observability is a WARN log and a Micrometer counter. A missing metric would make the issue invisible in production.
- **Suggested test:** Add a test that parks an event, advances the clock past the expiry cutoff, runs the expiry job, and asserts the `themistra.notification.parked_events.expired` counter increments.

---

## Gap 8 — No test pins the G2 event-key spec note

- **Why it matters:** Fix 18 added a spec note that payment events are keyed by their aggregate (`paymentUuid`/`receiptUuid`), not by `invoiceUuid` as the original spec said. The note is manual; a future spec edit could revert it.
- **Suggested test:** Add a guard test in `T01SkeletonRegressionTest` that asserts `package.md` §9 or the relevant note contains the corrected key wording.

---

## Gap 9 — No test verifies the JWKS URI is env-driven in all services

- **Why it matters:** Fix 14 replaced hard-coded `localhost:8080` JWKS URIs with env-driven properties. A regression could reintroduce a hard-coded URI in a new property or profile.
- **Suggested test:** Add a properties guard test in each service that asserts `application.properties` contains no literal `localhost:8080` (except in local-only defaults that are themselves env-overridable).

---

## Gap 10 — No test verifies the notification test controller is not scanned in production

- **Why it matters:** The sweep left `ResourceServerTestController` ungated in notification. In production it would not be scanned because it is in `src/test/java`, but full-application integration tests may load it.
- **Suggested test:** Add an assertion in the notification architecture test that `ResourceServerTestController` is annotated with a test-only profile or resides under `src/test/java`.

---

## Gap 11 — No test stabilizes the flaky auth audit test

- **Why it matters:** `AuditTrailIntegrationTest` timed out once on a 15-second Kafka wait. A flaky test in the suite undermines confidence.
- **Suggested test:** Increase the Awaitility timeout, use a unique consumer group per test, or isolate the test's Kafka topic setup so earlier tests do not affect its offsets.

---

## Gap 12 — No test verifies `QuorumEvaluator` is a singleton bean

- **Why it matters:** Fix 2 made `QuorumEvaluator` a bean. A duplicate bean or a prototype scope could cause subtle quorum bugs.
- **Suggested test:** Add an assertion in a crypto integration test that the application context contains exactly one `QuorumEvaluator` bean and that it is the instance used by `Watcher`.

---

## Summary

The sweep's fixes are well-guarded by unit and integration tests, and the deliberate-break discipline is strong. The highest-value additions are Gap 1 (cross-service crypto/payment proof), Gap 2 (startup smoke tests), and Gap 5 (Docker image runtime smoke). Gaps 3–4, 6–12 are smaller completeness, observability, and regression-guard notes.
