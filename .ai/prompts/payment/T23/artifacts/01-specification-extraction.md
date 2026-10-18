# payment · T23 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

## Business Rules

- **R29.** Every `4xx` response SHALL be an RFC 9457 `application/problem+json` body with a
  stable `type`, no stack traces, and no internal details.

## Locked Decisions

- **L10 (continuity).** Zero trust at the edge — every non-public endpoint requires a valid JWT;
  `ResourceServerConfig`'s own manual 401/403 handlers are in scope for this task since they are
  two of the twelve `about:blank` sites identified at Phase 0, even though they live in
  `ResourceServerConfig`, not `ApiExceptionHandler` itself — R29 says "every 4xx response," not
  "every `ApiExceptionHandler` response."
- **L12 (continuity).** No feature module imports another feature module's entity class; `common`
  is shared plumbing. The new catalogue class belongs in `common` (per `design.md`'s own package
  map, `ApiExceptionHandler.java` already lives there) and must not import anything from
  `invoice`/`export`/any other feature module — it is a pure constants class with zero
  dependencies either way.
- No LOCKED decision requires per-type `detail` wording to change; R29's "no internal details"
  constraint was already independently confirmed, site by site, by every prior task that added a
  handler (T06, T07, T08, T12, T22) — re-confirmed at Phase 0, no new leak found.

## Files involved

**Existing, to extend (not replace):**
- `common/ApiExceptionHandler.java` — 7 `setType("about:blank")` sites.
- `invoice/InvoiceExceptionHandler.java` — 3 sites.
- `export/ExportExceptionHandler.java` — 1 site.
- `common/ResourceServerConfig.java` — 1 shared `writeProblemJson` helper, used by both the 401
  and 403 manually-constructed beans.

**New, per Phase 0's own finding (not named explicitly anywhere in `design.md`, but required to
satisfy "a stable `type` catalogue" as literally worded, and directly mirroring `auth-service`'s
own already-built `ProblemTypes` precedent):**
- `common/ProblemTypes.java`.

## Dependencies

- `java.net.URI` (already used at every existing call site).
- No new library, no new config key, no new contract file (`contracts/api/payments.yaml` does
  not exist yet — task 24's own job, logged as an open question below, not a blocker here).
- Existing exception classes (`InvalidInvoiceRequestException`, `CryptoServiceUnavailableException`,
  `InvoiceNotFoundException`, `InvalidExportRequestException`, plus the framework exceptions
  `ApiExceptionHandler` already maps) — none change shape; only their handler's `setType(...)`
  argument changes.

## Acceptance Criteria

- **AC1.** A single `ProblemTypes` class in `common` defines one named, stable `URI` constant per
  distinct logical error category currently emitted by this service (enumerated at Phase 2) —
  mirroring `auth-service`'s own `ProblemTypes` shape (base URL + kebab-case suffix, private
  constructor, never an inline literal at a call site).
- **AC2.** Every one of the twelve existing `setType("about:blank")` call sites (`ApiExceptionHandler`
  ×7, `InvoiceExceptionHandler` ×3, `ExportExceptionHandler` ×1, `ResourceServerConfig`'s shared
  helper ×1, covering both its 401 and 403 callers) is replaced with the matching
  `ProblemTypes` constant.
- **AC3.** No response body for any 4xx or 5xx path includes a stack trace, a raw exception
  message from an unexpected/internal failure, or any other internal implementation detail —
  re-confirmed, not merely assumed, for every existing handler (continuity of R29's existing,
  already-satisfied half; this task's own net-new work is entirely AC1/AC2).
- **AC4.** Two genuinely new type entries are needed beyond anything `auth-service`'s own
  catalogue already has an equivalent for: a missing-required-parameter case and a malformed-
  request-parameter case (both T22-era additions to `ApiExceptionHandler`, neither of which
  `auth-service` has ever needed).

## Tests required

- **Named test:** `shouldReturnProblemJsonWithNoInternalDetailOn4xx` (`package.md` §8) → R29 —
  a representative 4xx response (e.g. a validation failure) asserts the full RFC 9457 shape:
  `type` is one of the new named catalogue URIs (not `"about:blank"`), `title`/`status`/`detail`
  present, and no field anywhere in the body contains a stack trace or raw exception class name.
- **Boundary tests implied, not separately named:**
  - Every one of the eight existing test assertions Phase 0 identified as expecting
    `"about:blank"` literally must be updated to the correct new catalogue value, not merely
    deleted — a mechanical update across `InvoiceControllerTest` (6), `ResourceServerConfigTest`
    (1), `AccessDeniedHandlerTest` (1).
  - A new direct test (or tests) on `ProblemTypes` itself proving no two constants collide (same
    URI reused for two logically distinct categories) and that the class cannot be instantiated
    (mirrors `PublicEndpoints`'/every other pure-constants class's own established style in this
    codebase, if any such test exists — to confirm at Phase 2/10).
  - `ExportControllerTest`'s own 4xx paths currently assert only `$.title`, never `$.type` — this
    task should add a `$.type` assertion there too, since R29 applies to every 4xx response in
    this service, not only the ones a prior task happened to assert on.

## Open Questions

- **Q-A.** `contracts/api/payments.yaml` does not exist yet (task 24). This task's own `type`
  catalogue is not blocked by that — `ProblemTypes` is a pure Java artifact, not an OpenAPI one —
  but task 24, once authored, should reference these exact URIs rather than re-inventing its own.
  Logged for that later task's own grounding, not a blocker here.
- No other genuine blockers. `package.md` §11 was checked in full (all of Q1–Q8 already closed,
  none scoped to error handling/R29).
