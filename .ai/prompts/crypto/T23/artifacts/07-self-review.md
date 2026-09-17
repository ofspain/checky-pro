# crypto · T23 · Phase 7 — Self Review

Self-review of the Phase 6 diff against the frozen brief (`artifacts/04-frozen-task-brief.md`) and
`agents.md`. Findings only — no fixes applied here (Phase 9), per this phase's own rule. No critical or
functional defects were found — both findings below are cosmetic/documentation-only.

## 1. `CryptoInternalOpenApiContractTest` imports `PathVariable` but never uses it

- **Issue:** `import org.springframework.web.bind.annotation.PathVariable;` (line 18) is dead — no
  reference to `PathVariable` anywhere in the file. Likely a leftover from an earlier draft that
  considered inspecting `@PathVariable`-annotated method parameters directly (rather than the
  YAML-side `parameters` array check that `watchIdPathParameterIsDeclaredOnTheDeleteOperation`
  ultimately implements) and was never cleaned up.
- **Severity:** Low
- **Evidence:** `common/CryptoInternalOpenApiContractTest.java:18`.
- **Recommendation:** Remove the unused import.

## 2. `controllerRoutes()`'s inherited bare-`@RequestMapping` limitation isn't disclosed here, unlike
   in the file it was copied from

- **Issue:** `AuthOpenApiContractTest.controllerRoutes()` carries an explicit Javadoc comment
  disclosing a known, currently-dormant limitation: a handler using a bare `@RequestMapping` with no
  explicit HTTP method yields an empty `mapping.method()` array, silently contributing zero routes.
  `CryptoInternalOpenApiContractTest.controllerRoutes()` copies the identical logic verbatim but drops
  that disclosure comment. The limitation is equally dormant here (all three real handlers —
  `WatchController.register`/`unregister`, `AttestController.attest`,
  `VerificationKeysController.verificationKeys` — use a method-fixing shorthand annotation
  `@PostMapping`/`@DeleteMapping`/`@GetMapping`, none a bare `@RequestMapping`), so this is not a live
  defect, but the silent inheritance of an undocumented limitation is worth surfacing rather than
  losing the context that motivated the original comment.
- **Severity:** Low
- **Evidence:** `common/CryptoInternalOpenApiContractTest.java:313-331`
  (`controllerRoutes()`); contrast with `services/auth/.../AuthOpenApiContractTest.java`'s own
  Javadoc on its identical method.
- **Recommendation:** Copy the disclosure comment forward, adapted to name this task's own three
  controllers.

---

No thread-safety, module-boundary, or fidelity defects were found. All 6 contract files were re-verified
against their real source-of-truth records during this review (spot-checked `provider-degraded.v1.schema.json`'s
`chain`/`reason` enums against `DegradationReason.values()` and `ProviderDegradedPublisher.Payload`'s
actual field list once more) — no drift found beyond what Phase 6's own tests already prove.
