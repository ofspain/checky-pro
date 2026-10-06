<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review). -->

# notification · T18 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T18 — Dockerfile + build/image checkpoint |
| **Spec section** | Deployment rule in `agents.md` |
| **Model** | Kimi 2.7 |
| **Consumes** | Implementation + `artifacts/07-self-review.md` + frozen brief |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the T18 Dockerfile and self-review.

---

## Finding 1 (concur with self-review Finding 1) · Sibling-pom list is hand-maintained

**Severity:** Low

**Evidence:**
- `services/notification/Dockerfile` lines 7–8 explicitly copy `services/auth/pom.xml` and `services/crypto/pom.xml`.
- Root `pom.xml` `<modules>` is the real source of truth.

**Assessment:** Valid deferred maintenance hazard. Adding a fourth module to the root POM without updating this Dockerfile will reintroduce the same reactor-validation failure. A generalized copy mechanism would avoid this, but it is out of this task's explicitly scoped fix.

**Recommendation:** Track as a follow-up alongside the already-known auth/crypto Dockerfile fixes. No code change for this task.

---

## Finding 2 (concur with self-review Finding 2) · Only `latest` tag is produced

**Severity:** Informational

**Evidence:**
- Build command in the Dockerfile comment: `docker build -f services/notification/Dockerfile -t notification-service .`
- Sibling Dockerfiles use the same pattern.

**Assessment:** Acceptable. Version/SHA tagging belongs to CI/CD, which is out of scope.

**Recommendation:** No change.

---

## Finding 3 · `mvn verify` and `docker build` claims cannot be confirmed in this environment

**Severity:** Medium (verification gap)

**Evidence:**
- The brief requires a fresh `mvn -pl services/notification clean verify` and a real `docker build`.
- This workspace has neither `mvn` nor Docker available.

**Assessment:** The Dockerfile is structurally correct and follows the brief's design. The self-review references a `docker inspect` confirmation of `USER nonroot`, but the independent reviewer cannot reproduce any build command here.

**Recommendation:** Ensure both commands are executed and pass in an environment with Maven and Docker before the task is closed. Capture the command output in Phase 12 if possible.

---

## Finding 4 · Wildcard copy of sibling poms is not a trivial drop-in replacement

**Severity:** Very low / informational

**Evidence:**
- A naive `COPY services/*/pom.xml services/` would flatten all module `pom.xml` files into `services/`, breaking Maven's relative path expectations.

**Assessment:** The current explicit copy list is the simplest correct approach for a multi-module Maven reactor. A future generalization would need to preserve directory structure (e.g., a small `RUN find`/`cp` step or BuildKit). This is not a defect in the current file.

**Recommendation:** If addressing Finding 1 later, use a structure-preserving copy, not a flat wildcard.

---

## Cross-check against acceptance criteria

| Criterion | Status | Notes |
|---|---|---|
| AC1 — `mvn -pl services/notification clean verify` passes | ⚠️ | Cannot verify locally; structurally expected to pass. |
| AC2 — Docker image builds from repo root | ⚠️ | Dockerfile is correct; actual `docker build` cannot be run in this environment. |
| Deployment rule — multi-stage, distroless JRE 21, non-root | ✅ | `maven:3.9-eclipse-temurin-21` build stage, `gcr.io/distroless/java21-debian12:nonroot` runtime, `USER nonroot`. |
| Sibling pom fix | ✅ | Copies `services/auth/pom.xml` and `services/crypto/pom.xml` before Maven runs. |
| No sibling Dockerfile changes | ✅ | `services/auth/Dockerfile` and `services/crypto/Dockerfile` are untouched. |

---

## Verdict

The Dockerfile satisfies the design and structural acceptance criteria. The only material uncertainty is the unverified `mvn verify` / `docker build` execution (Finding 3), which is an environmental limitation of this workspace, not a code defect. The hand-maintained sibling-pom list (Finding 1) should be tracked as follow-up maintenance.
