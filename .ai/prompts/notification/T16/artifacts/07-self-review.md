# notification · T16 · Phase 7 — Self Review

Self-review of the Phase 6 implementation against the frozen brief and `agents.md`. Findings
only — no fixes applied here (Phase 9's own job). The Surefire/`@ArchTest` discovery and its fix are
not re-litigated here (already fully disclosed in Phase 6's own implementation notes); this review
looks for what that disclosure didn't already cover.

## Finding 1 · A FROZEN (Phase 4, human-approved) file path was silently amended one phase later, without a renewed approval gate

**Severity:** Medium (process, not code)

**Evidence:**
- Phase 4's own frozen brief, **Files to Create**: `src/test/java/archtestfixtures/RogueHttpClientUser.java`.
- Phase 5's own implementation plan moved it to `src/test/java/com/themistra/notification/channel/RogueHttpClientUser.java`
  — a different package, under a heading calling it "a placement correction caught during this
  phase's own design pass, before any code was written."
- The actual Phase 6 file lives at the Phase-5-corrected path, not the Phase-4-frozen one.

**Issue:** The correction is right on the merits — `shouldMakeNoSynchronousCrossServiceCall`'s own
subject condition (`.that().resideInAPackage(ANALYZED_PACKAGE + "..")`) genuinely would not admit a
fixture at the frozen brief's literal `archtestfixtures` path as a valid subject at all, so keeping
the frozen path would have shipped a negative-proof test that silently proves nothing. But Phase 4's
own "FROZEN" status is this pipeline's human-approval gate; Phase 5 (a design/planning phase, no
human approval step of its own) overrode one of its concrete file paths without looping back through
that gate. Nothing downstream was hidden — Phase 5's own artifact names the correction and the
reasoning in full — but the FREEZE's own authority was bypassed procedurally, not just its literal
text.

**Recommendation:** No code change needed; the correction is already the right outcome and already
disclosed. Worth confirming with the user, as a process matter, that a Phase-5-stage correction to a
FROZEN file path is an acceptable exception to "Phase 4 is where paths get fixed," rather than
something that should have been kicked back for a fresh freeze. Not blocking.

## Finding 2 · The frozen brief's own Constraints text (Finding #6's resolution) now names an annotation this class no longer has

**Severity:** Low

**Evidence:**
- Frozen brief, **Constraints**: "`@AnalyzeClasses` and every canary's own `ClassFileImporter` must
  share one single, eagerly-initialized `JavaClasses` instance and identical `ImportOption`, so the
  two can never silently analyze a different class set than each other."
- Phase 6's own (Surefire-bug-forced) implementation removed `@AnalyzeClasses` from the class
  entirely — there is no longer an annotation-driven scan to drift from.

**Issue:** The *substance* of the constraint still holds — `analyzedClasses`/`analyzedClasses()` is
still one single, eagerly-initialized `JavaClasses` instance, with one `ImportOption`, shared by
every canary, so no two canaries can check a different class set than each other. But the
constraint's own literal text, read on its own, now describes a mechanism (`@AnalyzeClasses`
drifting from the canaries) that no longer exists in this file, since there's nothing left for it to
drift from. A future reader comparing the frozen brief line-by-line against the code could read this
as unmet, when it's actually satisfied by construction for a different, stronger reason (nothing to
drift from at all, not two things kept in sync).

**Recommendation:** No code change needed — already addressed by this class's own Javadoc, which
explains the full reasoning. Worth noting in Phase 12 (specification verification) that this
Constraints line should be read as superseded by the Phase 6 Javadoc, not as a literal unmet
requirement.

## Open Questions

Finding 1 is worth a brief confirmation from the user before Phase 9 (review resolution), since it's
a process question about this pipeline's own freeze discipline, not a code defect — everything else
found here is informational, not blocking.
