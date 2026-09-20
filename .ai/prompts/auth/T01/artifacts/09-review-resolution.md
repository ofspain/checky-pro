STATUS: RESOLVED

# auth · T01 · Phase 9 — Review Resolution

Human Approval gate. Decided by: femi (this session). Consumes `artifacts/07-self-review.md` and
`artifacts/08-independent-review.md`. Only ACCEPTED comments below were applied; no refactor, no
optimization, no public API/class rename occurred (none were proposed).

## Resolution log

| # | Comment (source) | Decision | Reason | Change made |
|---|---|---|---|---|
| 1 | Self-review Finding 1: `flyway-maven-plugin` missing explicit `<version>`, flagged Medium as possible version skew. | **Superseded by independent review, then separately ACCEPTED on its own merits.** | Independent review (Finding 1) verified via `mvn help:effective-pom` that the version is already BOM-managed (`org.springframework.boot:spring-boot-dependencies:3.5.4` pins `flyway-maven-plugin` to `11.7.2`, matching `flyway-core`) — I independently confirmed this by finding the pin in the cached BOM POM. The original "coincidental skew" risk was overstated. Human (femi) chose to pin the version explicitly anyway, for defensiveness/clarity, even though it's redundant given the BOM. | Added `<version>11.7.2</version>` to the `flyway-maven-plugin` block in `services/auth/pom.xml`. |
| 2 | Independent review Finding 5 / Self-review Finding 3: plugin DB credentials hardcoded, no env-var override; both reviews called it acceptable but suggested an optional clarifying comment. | **ACCEPTED.** | Human requested it after confirming (via three checks: no `<executions>` binding, CI only runs `mvn verify`, production Dockerfile builds a distroless image with no Maven at all) that the plugin genuinely cannot run outside explicit local invocation — the comment documents that guarantee for future readers rather than leaving it implicit. | Added a two-line XML comment directly above the plugin block in `services/auth/pom.xml` stating it is local-dev only and explaining why (no `<executions>` binding). |
| 3 | Self-review Finding 2 / Independent review Finding 4: non-`CONCURRENTLY` `CREATE INDEX` locks `lockout_state` during migration; production rollout risk if the table has rows by deploy time. | **REJECTED as a code change; logged as a deferred note.** | Both reviews agree the SQL is a LOCKED verbatim artifact (`design.md` §4c, "copy exactly, do not paraphrase"; L1). No code change is possible within T01's scope. Independent review's alternate suggestion ("escalate to the spec author to amend `design.md` §4c") is also out of scope here — this phase may not modify `spec/`. | No code change. Recorded as an open, deferred item (see below) for whoever owns the production rollout runbook / a future `design.md` revision — not resolved in T01. |
| 4 | Independent review Finding 2: `mvn -pl services/auth verify` cannot be confirmed green due to pre-existing `token`-module compile errors, unrelated to T01. | **Confirmed, no action.** | Matches what Phase 6 already established (reproduced identically with T01's only change stashed out). Independent review corroborates independently on a different machine. Per prior instruction, left flagged and untouched — out of T01's scope. | No code change. |
| 5 | Independent review Finding 3: dynamic verification (Docker/`flyway:migrate`) could not be reproduced on the reviewer's machine (Docker Desktop unavailable there). | **Not applicable — already independently verified elsewhere.** | This is an environment limitation of the review machine, not a defect. AC4 was already run to completion and verified against a live Postgres in Phase 6 (this session, migration applied, `flyway_schema_history` confirmed). | No action needed. |
| 6 | Independent review Finding 6: V5 SQL matches `design.md` §4c verbatim, no deviation found. | **Confirmed, no action.** | Both reviews agree; no comment to resolve. | No code change. |

## Exact changes made this phase

`services/auth/pom.xml`:
```diff
+      <!-- Local-dev only: runs solely via explicit `mvn flyway:migrate`. No <executions> binding,
+           so it never fires during `package`/`verify`/CI or the production Docker build. -->
       <plugin>
         <groupId>org.flywaydb</groupId>
         <artifactId>flyway-maven-plugin</artifactId>
+        <version>11.7.2</version>
         <configuration>
           <url>jdbc:postgresql://localhost:5432/checky</url>
           <user>checky</user>
           <password>checky-local-only</password>
           <schemas>auth</schemas>
         </configuration>
       </plugin>
```
Re-validated: `mvn -pl services/auth validate` → `BUILD SUCCESS` after both edits.

## Open items (not resolved here, carried forward)

- **Production index-lock note (item 3 above).** Deferred — no owner assigned within this task;
  raise separately if/when a production rollout runbook for V5 is authored.
- **Pre-existing `token`-module compile break (item 4 above).** Deferred — flagged, not owned by
  T01, requires its own triage outside this task's scope.
- **ShedLock `TIMESTAMPTZ` compatibility** — already deferred to task #30 in the frozen brief
  (Phase 4); unchanged by this phase.

If nothing had been accepted, this section would say so explicitly — two comments were accepted and
applied as shown above.
