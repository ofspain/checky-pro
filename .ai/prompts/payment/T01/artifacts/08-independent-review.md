# payment · T01 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T01/artifacts/08-independent-review.md`  
Consumed: Phase 6 implementation, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `spec/payment-service/agents.md`  
Scope: T01 only. Findings only — no rewrite.

---

### 1. Required `T01SkeletonRegressionTest` is missing

- **Issue:** The frozen brief lists `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java` as a file to create and details its required assertions. Phase 6 deferred all tests to Phase 10, so the mandated regression guard does not exist. This leaves AC1–AC5 and the version-alignment/S3-client checks without permanent, CI-enforced protection against silent regressions.
- **Evidence:**
  - `artifacts/04-frozen-task-brief.md:106-108` (Files to Create)
  - `artifacts/04-frozen-task-brief.md:149-163` (Required Tests)
  - `services/payment/src/test/java/com/themistra/payment/` does not exist (verified directly)
  - `artifacts/06-implementation-notes.md:31` explicitly defers the test class
- **Recommendation:** Create `T01SkeletonRegressionTest.java` before T01 is considered complete. It must: assert AC1–AC5 by direct file read of the two poms; check the Application class structure for AC4 (no Maven shell-out); align `testcontainers.version`, `archunit-junit5`, `awaitility`, AWS SDK BOM, and `flyway-maven-plugin` versions against `services/auth/pom.xml`; construct an `S3Client` with dummy credentials; and scan both poms for credential strings outside the documented L13 carve-out.
- **Confidence:** High.

---

### 2. No empirical proof that the S3 SDK resolves a constructible client

- **Issue:** The frozen brief requires a real `S3Client` construction test (dummy credentials, no network call) to prove the AWS SDK BOM + `s3` dependency actually resolves a working client now rather than deferring the discovery to task 18. Neither Phase 6 nor Phase 7 created or ran this check. Dependency-tree resolution alone does not prove class loading will succeed.
- **Evidence:**
  - `artifacts/04-frozen-task-brief.md:158-161`
  - No test class exists to perform the construction check
- **Recommendation:** Include the `S3Client` construction test inside the missing `T01SkeletonRegressionTest` (finding 1). It should mirror `services/notification`'s `sesV2ClientCanActuallyBeConstructed()` precedent.
- **Confidence:** Medium — the dependency set looks correct, but the absence of the construction test is the gap the frozen brief explicitly closes.

---

### 3. Self-review declared "no findings" without checking the frozen brief's full deliverables list

- **Issue:** Phase 7 concluded there was nothing to report, but it only inspected the root pom, the payment pom, and `PaymentServiceApplication.java`. It did not verify that every item in the frozen brief's Files to Create / Required Tests exists, which is how the missing regression test escaped notice.
- **Evidence:**
  - `artifacts/07-self-review.md:39` ("No findings")
  - `artifacts/04-frozen-task-brief.md:104-108` and `:147-163` list the test file and its assertions
  - The test source directory is empty
- **Recommendation:** In future Phase 7 work, cross-check the filesystem against every line of the frozen brief's Files to Create and Required Tests, not only the production files. Record any intentionally deferred items as explicit deviations rather than "none."
- **Confidence:** High.

---

### 4. Password carve-out is correctly implemented per the frozen brief

- **Issue:** Not a finding — positive confirmation for the record. `services/payment/pom.xml` contains the same local-dev-only Flyway plugin password placeholder as the sibling services, and `artifacts/04-frozen-task-brief.md:77-82` explicitly documents this as the one L13 carve-out. No other secret or credential string appears in the modified files.
- **Evidence:** `services/payment/pom.xml:111-113`; `artifacts/04-frozen-task-brief.md:77-82`; `spec/payment-service/agents.md` Security section.
- **Recommendation:** None.
- **Confidence:** High.

---

### 5. Dependency set and build scaffolding match the frozen brief

- **Issue:** Not a finding — positive confirmation. The payment pom declares exactly the dependencies, scopes, version pins, exclusions, plugins, and `finalName` required by AC2/AC3/AC5, and registering the module did not disturb the existing services (`mvn validate` from the repo root succeeded for all five modules in this review).
- **Evidence:** `services/payment/pom.xml` lines 7-117; `pom.xml` lines 21-28; build output from this review (`mvn validate`, all 5 modules `SUCCESS`).
- **Recommendation:** None.
- **Confidence:** High.
