STATUS: FROZEN

# crypto · T29 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 8 findings independently verified against real source. One further, more serious finding was made
independently while verifying Kimi's own Finding #2 (attempting the real `docker build`, now that
Docker is genuinely available for the first time this session).

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | §9 item 13 (`mvn verify`) can't be honestly ticked unconditionally | **ACCEPTED** | Split: unit + ArchUnit portion — verified, `[x]`. Integration (Testcontainers/Docker) portion — `[ ]`, citing T28 Phase 12's disclosed pre-existing failures (unrelated to this spec's own §3/§8 scope) plus this phase's own Docker-build finding below. |
| 2 | §9 item 14 (Docker image builds) is unverified | **ACCEPTED, and the real answer is worse than "unverified"** | Actually attempted (Docker is now available) — genuinely **fails**: `[ ]`, citing the real Maven reactor error below. |
| 3 | Q7's "deployment-swappable" claim is contradicted by a hardcoded constant | **ACCEPTED** | `KmsSigner.SIGNING_ALGORITHM` is a `private static final` field — a single-point-of-code-change, not a runtime/config-swappable value. Q7's resolution text corrected to Kimi's own suggested wording: "engineering answer: SHA-256 digest + ECDSA P-256 signing via KMS, verified end-to-end against a compatible key. **Open follow-up:** make `signingAlgorithm` configurable via `KmsProperties` if the platform later provisions a different key spec." |
| 4 | Q1's "commercially independent providers" can't be code-enforced | **ACCEPTED** | Added Kimi's own suggested sentence verbatim to Q1's resolution: commercial independence is an operational/procurement guardrail; code enforces only `providers.size() >= quorumThreshold` and cross-provider agreement. |
| 5 | Several §9 items depend on CI/IAM/process, not just code | **ACCEPTED** | Each `[x]` in §9 will name its evidence type explicitly (unit test / ArchUnit / integration test / CI process / gitleaks), not a bare checkmark. |
| 6 | The "tests pass" working decision vs. item 13's literal wording | **ACCEPTED, resolved by Finding #1's split, not by editing §3/§8** | Item 13's own checklist text is left unchanged (per T29's narrow scope, mirroring the earlier decision not to touch §8's named-test prose); the split disposition (Finding #1) is what actually reconciles the tension, not a rewording. |
| 7 | Fail-closed screening means attest always refuses without a real vendor | **ACCEPTED, with a correction to the recommendation** | Verified directly: `AttestationServiceTest` mocks `ScreeningClient` (Mockito, not Spring), and `EndToEndIntegrationTest` `@MockBean`s it too (T26) — no test double gap exists, contrary to Kimi's suggested "confirm such a double exists or is planned." The real, valid half of this finding is the operational implication for actual deployments: added to Q2's resolution text as an explicit disclosure, not a new test requirement. |
| 8 | No regression test guards `package.md`'s own header/checklist | **ACCEPTED** | A new test method will be added (mirroring `T01SkeletonRegressionTest`'s `SECURITY-THREAT-MODEL.md` pattern) asserting `Version: 0.2` / `Status: READY FOR IMPL`, and that every §9 item this task marks `[x]` still is — designed to tolerate items 13/14's own genuine, disclosed partial/unchecked state, not assert every item is `[x]`. |

## A finding beyond Kimi's own review — presented to the user, resolved

While verifying Finding #2, `docker build -f services/crypto/Dockerfile -t crypto-service .` was
actually attempted for the first time this session (Docker only became available during T28). It
genuinely fails:

```
[ERROR] Child module /workspace/services/auth of /workspace/pom.xml does not exist
```

The root `pom.xml`'s reactor declares both `services/auth` and `services/crypto` as modules; neither
service's own Dockerfile copies the sibling's `pom.xml` into its build context, so Maven's reactor
validation fails before any real compilation starts. `services/auth/Dockerfile` has the byte-for-byte
identical structural gap (confirmed by direct comparison) — this is not a crypto-specific defect, it
predates crypto-service's own module even being added to the reactor, and fixing it would touch
`services/auth/Dockerfile` too, squarely outside T29's own package.md-only scope.

**Presented to the user directly** (not a routine gate, since it determines whether the header bump can
honestly proceed at all). Decision: disclose honestly in §9 items 13/14, do not fix here, flag as a new
follow-up task candidate — the header still bumps to `READY FOR IMPL` if §11 and the rest of §9
genuinely hold, since this is a cross-service packaging/CI gap unrelated to crypto-service's own spec
content, not a gap in the spec's own requirements or their implementation.

## Task

Unchanged from Phase 2, with all dispositions above folded in: verify §11 Q1/Q2/Q3/Q7 and §9's 14 items
against real evidence, address each explicitly, bump the header only if the spec-content-completeness
gate (as scoped above) genuinely holds.

## Acceptance Criteria

1. **AC1.** Q1, Q2, Q3, Q7 each carry an explicit, evidence-cited resolution, including the corrections
   from Findings #3/#4/#7 above.
2. **AC2.** §9's 14 items are each checked with a typed evidence citation; items 13 and 14 are correctly
   left partially/fully unchecked with the real, disclosed reasons (Docker-blocked pre-existing failures;
   a genuine, newly-discovered cross-service Docker-build defect), not silently ticked.
3. **AC3.** A new regression test guards `package.md`'s own header values and §9's `[x]` claims.
4. **AC4.** The header bumps to `0.2`/`READY FOR IMPL`, per the human decision above, with the Docker
   build gap and pre-existing integration failures explicitly carried forward as disclosed, tracked
   follow-up items — not silently dropped once the header changes.

## Open Questions

No blockers — the one genuine judgment call (how to treat the newly-discovered Docker build failure)
was presented to and resolved by the user above.
