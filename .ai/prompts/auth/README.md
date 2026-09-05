# AI prompt workflows — `auth-service`

One task folder per implementation task in [`spec/auth-service/tasks.md`](../../../spec/auth-service/tasks.md). Each folder holds the 14-phase prompt set and a README.

**Spec package:** [`spec/auth-service/`](../../../spec/auth-service/) — `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

**Tasks (40):**

| Task | Title | Section |
|---|---|---|
| [T01](T01/) | Schema V5 | Foundation |
| [T02](T02/) | Resolve Q1 (TOTP encryption) | Foundation |
| [T03](T03/) | Password policy domain + config | Foundation |
| [T04](T04/) | Breach-check audit event | Foundation |
| [T05](T05/) | Verification token service | Foundation |
| [T06](T06/) | Self-service verification endpoints | Account module extensions |
| [T07](T07/) | Password reset flow | Account module extensions |
| [T08](T08/) | Change own password | Account module extensions |
| [T09](T09/) | Password policy enforcement | Account module extensions |
| [T10](T10/) | Enumeration safety tests | Account module extensions |
| [T11](T11/) | Lockout state machine | Lockout and authentication |
| [T12](T12/) | Lockout service | Lockout and authentication |
| [T13](T13/) | Login failure/success tracking | Lockout and authentication |
| [T14](T14/) | Admin unlock endpoint | Lockout and authentication |
| [T15](T15/) | Indistinguishable login response test | Lockout and authentication |
| [T16](T16/) | TOTP seed handling | MFA (after Q1 is resolved) |
| [T17](T17/) | MfaEnrollment entity/repository | MFA (after Q1 is resolved) |
| [T18](T18/) | MFA service | MFA (after Q1 is resolved) |
| [T19](T19/) | MFA controller | MFA (after Q1 is resolved) |
| [T20](T20/) | SAS MFA step integration | MFA (after Q1 is resolved) |
| [T21](T21/) | Token claim updates | MFA (after Q1 is resolved) |
| [T22](T22/) | MFA integration tests | MFA (after Q1 is resolved) |
| [T23](T23/) | ApiKey entity/repository | API keys |
| [T24](T24/) | Key service | API keys |
| [T25](T25/) | API-key exchange endpoint | API keys |
| [T26](T26/) | API-key CRUD controller | API keys |
| [T27](T27/) | API-key integration tests | API keys |
| [T28](T28/) | Session listing/revocation | Sessions and cleanup |
| [T29](T29/) | SAS revoke integration | Sessions and cleanup |
| [T30](T30/) | Scheduled cleanup job | Sessions and cleanup |
| [T31](T31/) | Rate limiting | Rate limiting, contracts, and hardening |
| [T32](T32/) | Public endpoint sweep | Rate limiting, contracts, and hardening |
| [T33](T33/) | Contract files | Rate limiting, contracts, and hardening |
| [T34](T34/) | Token claims doc | Rate limiting, contracts, and hardening |
| [T35](T35/) | ArchUnit / module-boundary tests | Rate limiting, contracts, and hardening |
| [T36](T36/) | End-to-end integration test | Final verification |
| [T37](T37/) | Run full test suite | Final verification |
| [T38](T38/) | Review against gap analysis defect catalogue | Final verification |
| [T39](T39/) | Update `auth-decisions.md` | Final verification |
| [T40](T40/) | Bump spec status | Final verification |

To execute a task, open its folder and run `00-…` through `13-…` in order with the model named in each file's header. See [`../../WORKFLOW.md`](../../WORKFLOW.md).
