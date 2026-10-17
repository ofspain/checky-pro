# payment · T22 · Phase 7 — Self Review

Consumes: `artifacts/06-implementation-notes.md`

## Checks performed

- **Binding mechanics verified empirically, not assumed.** `@RequestParam Instant from`/`to`
  relies on Spring's default `ConversionService` parsing an ISO-8601 string directly to
  `java.time.Instant` with no `@DateTimeFormat` annotation. Confirmed outside the full Spring
  context with a standalone `DefaultFormattingConversionService` probe:
  `convert("2026-10-15T00:00:00Z", Instant.class)` → `2026-10-15T00:00:00Z`. This is the exact
  converter Spring MVC's `@RequestParam` binding uses, so the controller's typed-`Instant`
  parameters work as designed without extra configuration.
- **Security wiring confirmed, not assumed.** `ResourceServerConfig`'s `auth.anyRequest()
  .authenticated()` default already covers `/api/v1/exports/transactions` — confirmed
  `PublicEndpoints.PATTERNS` does not list it. `@PreAuthorize("hasRole('MERCHANT')")` on top of
  that is consistent with every other merchant-facing endpoint.
- **Null-safety of CSV fields.** Every `TransactionExportRow` field reaching `csvField(...)` is
  non-null by construction: the query only returns `ATTESTED` rows, and `markAttestedIfFinalized`
  (T19) sets `attested_at` in the same transition that requires `finalized_at` to already be set;
  all other joined columns are `NOT NULL` in `V1__payments_baseline.sql`. No defensive null
  handling added for a case that cannot occur, consistent with this codebase's convention.
- **Transaction boundary.** `TransactionExportService.export`'s own `@Transactional(readOnly =
  true)` is the correct, and only, transactional boundary — the repository's bare
  `EntityManager.createNativeQuery(...).getResultList()` has no implicit transaction of its own
  (unlike a Spring Data `JpaRepository` method), so without this the call would fail outside any
  active transaction/persistence context.
- **No regression.** Full suite re-run after Phase 6: 420/420.

## Findings

None beyond what Phase 6 already disclosed (the `ApiExceptionHandler` addition for
`MissingServletRequestParameterException`, and the empirically-confirmed native-query column
types) — both already fixed and verified in that same phase, not deferred here.

No new Low/Medium/High finding identified in this self-review pass.
