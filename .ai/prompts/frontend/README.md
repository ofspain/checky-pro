# AI prompt workflows — `frontend`

One task folder per implementation task in [`spec/frontend/tasks.md`](../../../spec/frontend/tasks.md). Each folder holds the 14-phase prompt set and a README.

**Spec package:** [`spec/frontend/`](../../../spec/frontend/) — `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

**Tasks (172):**

| Task | Title | Section |
|---|---|---|
| [T01](T01/) | Shell renders | Phase 1 — Foundation |
| [T02](T02/) | /app prefix | Phase 1 — Foundation |
| [T03](T03/) | Generated client | Phase 1 — Foundation |
| [T04](T04/) | No hand-written backend fetch | Phase 1 — Foundation |
| [T05](T05/) | Single-flight 401 | Phase 1 — Foundation |
| [T06](T06/) | Shared renewal across tabs | Phase 1 — Foundation |
| [T07](T07/) | 429 generic copy | Phase 1 — Foundation |
| [T08](T08/) | No POST replay | Phase 1 — Foundation |
| [T09](T09/) | Denylisted navigations bypass the worker | Phase 1 — Foundation |
| [T10](T10/) | End-session navigation bypasses the worker | Phase 1 — Foundation |
| [T11](T11/) | API routes are NetworkOnly | Phase 1 — Foundation |
| [T12](T12/) | Prompt-to-reload update policy | Phase 1 — Foundation |
| [T13](T13/) | No secret in the bundle | Phase 1 — Foundation |
| [T14](T14/) | PKCE verifier | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T15](T15/) | Authorize request shape | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T16](T16/) | No password field | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T17](T17/) | Missing state discarded | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T18](T18/) | Mismatched state discarded | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T19](T19/) | Nonce validated | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T20](T20/) | Code exchange sends the verifier | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T21](T21/) | Access token in memory only | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T22](T22/) | No token in any storage | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T23](T23/) | PKCE state single-use | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T24](T24/) | Return target inside allowlist | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T25](T25/) | Return target outside allowlist | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T26](T26/) | Session storage holds only L16 items | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T27](T27/) | SAS MFA challenge resumes at callback | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T28](T28/) | Unenrolled privileged refusal copy | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T29](T29/) | Uniform sign-in failure | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T30](T30/) | Profile from userinfo | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T31](T31/) | Account status from accounts/me | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T32](T32/) | email_verified is not account state | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T33](T33/) | Clean re-auth on failed renewal | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T34](T34/) | Reuse detection path | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T35](T35/) | Sign-out revokes and clears | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T36](T36/) | Sign-out ends the SAS session | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T37](T37/) | Signed-out landing | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T38](T38/) | Sign-out then sign-in requires credentials | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T39](T39/) | Sign-out propagates to every tab | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| [T40](T40/) | Public route set is exact | Phase 1 — Auth & account: routes |
| [T41](T41/) | Unauthenticated redirect | Phase 1 — Auth & account: routes |
| [T42](T42/) | Authenticated shell | Phase 1 — Auth & account: routes |
| [T43](T43/) | Registration accepts 12 characters | Phase 1 — Auth & account: registration and reset |
| [T44](T44/) | Registration accepts 128 characters | Phase 1 — Auth & account: registration and reset |
| [T45](T45/) | Registration rejects out-of-range passwords | Phase 1 — Auth & account: registration and reset |
| [T46](T46/) | Code points, not UTF-16 | Phase 1 — Auth & account: registration and reset |
| [T47](T47/) | Registration acknowledgement is identical | Phase 1 — Auth & account: registration and reset |
| [T48](T48/) | Verify success state | Phase 1 — Auth & account: registration and reset |
| [T49](T49/) | Verify failure is uniform | Phase 1 — Auth & account: registration and reset |
| [T50](T50/) | Resend is public | Phase 1 — Auth & account: registration and reset |
| [T51](T51/) | Resend acknowledgement is uniform | Phase 1 — Auth & account: registration and reset |
| [T52](T52/) | Reset request is uniform | Phase 1 — Auth & account: registration and reset |
| [T53](T53/) | Reset success is same-device | Phase 1 — Auth & account: registration and reset |
| [T54](T54/) | Reset failure is uniform | Phase 1 — Auth & account: registration and reset |
| [T55](T55/) | Change password needs current | Phase 1 — Auth & account: registration and reset |
| [T56](T56/) | Breach rejection shows server copy | Phase 1 — Auth & account: registration and reset |
| [T57](T57/) | No client breach lookup | Phase 1 — Auth & account: registration and reset |
| [T58](T58/) | Identical across account states | Phase 1 — Auth & account: enumeration safety |
| [T59](T59/) | Identical across token states | Phase 1 — Auth & account: enumeration safety |
| [T60](T60/) | Device label shown | Phase 1 — Auth & account: sessions |
| [T61](T61/) | Fallback label when null | Phase 1 — Auth & account: sessions |
| [T62](T62/) | Rotation time shown | Phase 1 — Auth & account: sessions |
| [T63](T63/) | Revoke one | Phase 1 — Auth & account: sessions |
| [T64](T64/) | Revoke all | Phase 1 — Auth & account: sessions |
| [T65](T65/) | Silent re-auth after revoke-all fails | Phase 1 — Auth & account: sessions |
| [T66](T66/) | Enrollment shows URI and QR | Phase 1 — Auth & account: MFA (voluntary) |
| [T67](T67/) | Confirm needs a live code | Phase 1 — Auth & account: MFA (voluntary) |
| [T68](T68/) | Recovery codes shown once | Phase 1 — Auth & account: MFA (voluntary) |
| [T69](T69/) | Recovery codes not persisted | Phase 1 — Auth & account: MFA (voluntary) |
| [T70](T70/) | Acknowledgement required | Phase 1 — Auth & account: MFA (voluntary) |
| [T71](T71/) | Disable needs password and code | Phase 1 — Auth & account: MFA (voluntary) |
| [T72](T72/) | Regenerate shows once | Phase 1 — Auth & account: MFA (voluntary) |
| [T73](T73/) | Privileged bootstrap path | Phase 1 — Auth & account: MFA (voluntary) |
| [T74](T74/) | Create blocked without MFA | Phase 1 — Auth & account: API keys |
| [T75](T75/) | Plaintext shown once | Phase 1 — Auth & account: API keys |
| [T76](T76/) | Key not redisplayed | Phase 1 — Auth & account: API keys |
| [T77](T77/) | List shows contract fields | Phase 1 — Auth & account: API keys |
| [T78](T78/) | List never shows a secret | Phase 1 — Auth & account: API keys |
| [T79](T79/) | Revoke needs confirmation | Phase 1 — Auth & account: API keys |
| [T80](T80/) | Revoke calls DELETE | Phase 1 — Auth & account: API keys |
| [T81](T81/) | Admin area hidden from other roles | Phase 1 — Auth & account: admin (role matrix) |
| [T82](T82/) | adminGetAccount | Phase 1 — Auth & account: admin (role matrix) |
| [T83](T83/) | adminDeleteAccount | Phase 1 — Auth & account: admin (role matrix) |
| [T84](T84/) | adminActivateAccount | Phase 1 — Auth & account: admin (role matrix) |
| [T85](T85/) | adminSuspendAccount | Phase 1 — Auth & account: admin (role matrix) |
| [T86](T86/) | adminReinstateAccount | Phase 1 — Auth & account: admin (role matrix) |
| [T87](T87/) | adminUnlockAccount | Phase 1 — Auth & account: admin (role matrix) |
| [T88](T88/) | getEffectiveRoles | Phase 1 — Auth & account: admin (role matrix) |
| [T89](T89/) | assignRole | Phase 1 — Auth & account: admin (role matrix) |
| [T90](T90/) | removeRole | Phase 1 — Auth & account: admin (role matrix) |
| [T91](T91/) | assignRoleTemplate | Phase 1 — Auth & account: admin (role matrix) |
| [T92](T92/) | removeRoleTemplate | Phase 1 — Auth & account: admin (role matrix) |
| [T93](T93/) | createRole | Phase 1 — Auth & account: admin (role matrix) |
| [T94](T94/) | listRoles | Phase 1 — Auth & account: admin (role matrix) |
| [T95](T95/) | createRoleTemplate | Phase 1 — Auth & account: admin (role matrix) |
| [T96](T96/) | listRoleTemplates | Phase 1 — Auth & account: admin (role matrix) |
| [T97](T97/) | listAuditEvents | Phase 1 — Auth & account: admin (role matrix) |
| [T98](T98/) | Invoice list states | Phase 1 — Payment verification & invoicing: invoices |
| [T99](T99/) | Invoice amount is a decimal string | Phase 1 — Payment verification & invoicing: invoices |
| [T100](T100/) | Underpayment discrepancy | Phase 1 — Payment verification & invoicing: invoices |
| [T101](T101/) | Overpayment discrepancy | Phase 1 — Payment verification & invoicing: invoices |
| [T102](T102/) | Expired invoice | Phase 1 — Payment verification & invoicing: invoices |
| [T103](T103/) | Invoice detail from list | Phase 1 — Payment verification & invoicing: invoices |
| [T104](T104/) | Invoice create via client | Phase 1 — Payment verification & invoicing: invoices |
| [T105](T105/) | Forward path order | Phase 1 — Payment verification & invoicing: payment state |
| [T106](T106/) | Reorg CONFIRMING to SEEN | Phase 1 — Payment verification & invoicing: payment state |
| [T107](T107/) | Reorg SEEN to WATCHING | Phase 1 — Payment verification & invoicing: payment state |
| [T108](T108/) | HELD from any state | Phase 1 — Payment verification & invoicing: payment state |
| [T109](T109/) | HELD never called pending | Phase 1 — Payment verification & invoicing: payment state |
| [T110](T110/) | Live state update | Phase 1 — Payment verification & invoicing: payment state |
| [T111](T111/) | HELD before attestation is neutral | Phase 1 — Payment verification & invoicing: payment state |
| [T112](T112/) | No receipt before attestation | Phase 1 — Payment verification & invoicing: receipts |
| [T113](T113/) | Finalized copy | Phase 1 — Payment verification & invoicing: receipts |
| [T114](T114/) | Receipt at attestation | Phase 1 — Payment verification & invoicing: receipts |
| [T115](T115/) | Hold after attested reorg | Phase 1 — Payment verification & invoicing: receipts |
| [T116](T116/) | Receipt not cached | Phase 1 — Payment verification & invoicing: receipts |
| [T117](T117/) | Wallet monitoring setup | Phase 1 — Payment verification & invoicing: wallets and address safety |
| [T118](T118/) | Monitoring state shown | Phase 1 — Payment verification & invoicing: wallets and address safety |
| [T119](T119/) | Unknown token condition | Phase 1 — Payment verification & invoicing: wallets and address safety |
| [T120](T120/) | Confirmation count | Phase 1 — Payment verification & invoicing: wallets and address safety |
| [T121](T121/) | Address-poisoning warning on payment | Phase 1 — Payment verification & invoicing: wallets and address safety |
| [T122](T122/) | Address-poisoning warning on receipt | Phase 1 — Payment verification & invoicing: wallets and address safety |
| [T123](T123/) | Notification arrives in list | Phase 1 — Payment verification & invoicing: notifications |
| [T124](T124/) | Reconnecting state | Phase 1 — Payment verification & invoicing: notifications |
| [T125](T125/) | No duplicates on resume | Phase 1 — Payment verification & invoicing: notifications |
| [T126](T126/) | Stream auth without token in URL | Phase 1 — Payment verification & invoicing: notifications |
| [T127](T127/) | Stream reopens after renewal | Phase 1 — Payment verification & invoicing: notifications |
| [T128](T128/) | History is merchant-scoped | Phase 1 — Payment verification & invoicing: history, exports, and amounts |
| [T129](T129/) | Export is merchant-scoped | Phase 1 — Payment verification & invoicing: history, exports, and amounts |
| [T130](T130/) | History amounts are strings | Phase 1 — Payment verification & invoicing: history, exports, and amounts |
| [T131](T131/) | Base-unit scaling | Phase 1 — Payment verification & invoicing: history, exports, and amounts |
| [T132](T132/) | Scaling uses no Number | Phase 1 — Payment verification & invoicing: history, exports, and amounts |
| [T133](T133/) | Zero-decimal token | Phase 1 — Payment verification & invoicing: history, exports, and amounts |
| [T134](T134/) | OIDC round trip | Phase 1 — Release gate |
| [T135](T135/) | Accessibility gate | Phase 1 — Release gate |
| [T136](T136/) | Enumeration gate | Phase 1 — Release gate |
| [T137](T137/) | Bundle gate | Phase 1 — Release gate |
| [T138](T138/) | Upload progress | Phase 2 — Intelligence engine (DRAFT, blocked on Q4) |
| [T139](T139/) | Upload failure reason | Phase 2 — Intelligence engine (DRAFT, blocked on Q4) |
| [T140](T140/) | Upload failure hides internals | Phase 2 — Intelligence engine (DRAFT, blocked on Q4) |
| [T141](T141/) | Fields beside integrity | Phase 2 — Intelligence engine (DRAFT, blocked on Q4) |
| [T142](T142/) | Low confidence labelled | Phase 2 — Intelligence engine (DRAFT, blocked on Q4) |
| [T143](T143/) | Transaction-hash collection | Phase 2 — Intelligence engine (DRAFT, blocked on Q4) |
| [T144](T144/) | Collected evidence labelled | Phase 2 — Intelligence engine (DRAFT, blocked on Q4) |
| [T145](T145/) | Still-needed evidence | Phase 2 — Intelligence engine (DRAFT, blocked on Q4) |
| [T146](T146/) | Graph nodes and edges | Phase 2 — Intelligence engine (DRAFT, blocked on Q4) |
| [T147](T147/) | Partial analysis marked | Phase 2 — Intelligence engine (DRAFT, blocked on Q4) |
| [T148](T148/) | Narrative beside graph | Phase 2 — Intelligence engine (DRAFT, blocked on Q4) |
| [T149](T149/) | Claim status | Phase 3 — Dispute resolution (DRAFT, blocked on Q4) |
| [T150](T150/) | Claim evidence | Phase 3 — Dispute resolution (DRAFT, blocked on Q4) |
| [T151](T151/) | Statement apart from facts | Phase 3 — Dispute resolution (DRAFT, blocked on Q4) |
| [T152](T152/) | Narrative sections | Phase 3 — Dispute resolution (DRAFT, blocked on Q4) |
| [T153](T153/) | AI text distinct | Phase 3 — Dispute resolution (DRAFT, blocked on Q4) |
| [T154](T154/) | Confidence and recommendation | Phase 3 — Dispute resolution (DRAFT, blocked on Q4) |
| [T155](T155/) | Timeline order | Phase 3 — Dispute resolution (DRAFT, blocked on Q4) |
| [T156](T156/) | Missing event not inferred | Phase 3 — Dispute resolution (DRAFT, blocked on Q4) |
| [T157](T157/) | Smart-contract failure observed | Phase 3 — Dispute resolution (DRAFT, blocked on Q4) |
| [T158](T158/) | Signals not only a score | Phase 4 — Reputation & trust (DRAFT, blocked on Q4) |
| [T159](T159/) | Signal source | Phase 4 — Reputation & trust (DRAFT, blocked on Q4) |
| [T160](T160/) | Passport metric window | Phase 4 — Reputation & trust (DRAFT, blocked on Q4) |
| [T161](T161/) | Metric value with window | Phase 4 — Reputation & trust (DRAFT, blocked on Q4) |
| [T162](T162/) | Counterparty relationships | Phase 4 — Reputation & trust (DRAFT, blocked on Q4) |
| [T163](T163/) | Cluster evidence | Phase 4 — Reputation & trust (DRAFT, blocked on Q4) |
| [T164](T164/) | Alert classification | Phase 5 — Fraud intelligence & institutional API (DRAFT, blocked on Q4) |
| [T165](T165/) | Alert evidence | Phase 5 — Fraud intelligence & institutional API (DRAFT, blocked on Q4) |
| [T166](T166/) | Confirmed and suspected distinct | Phase 5 — Fraud intelligence & institutional API (DRAFT, blocked on Q4) |
| [T167](T167/) | Cross-chain chains | Phase 5 — Fraud intelligence & institutional API (DRAFT, blocked on Q4) |
| [T168](T168/) | Portal key shown once | Phase 5 — Fraud intelligence & institutional API (DRAFT, blocked on Q4) |
| [T169](T169/) | Portal plaintext-once rule | Phase 5 — Fraud intelligence & institutional API (DRAFT, blocked on Q4) |
| [T170](T170/) | Unguessable identifier | Cross-phase — Capability links (DRAFT, blocked on Q11) |
| [T171](T171/) | No PII on link | Cross-phase — Capability links (DRAFT, blocked on Q11) |
| [T172](T172/) | Link not cached | Cross-phase — Capability links (DRAFT, blocked on Q11) |

To execute a task, open its folder and run `00-…` through `13-…` in order with the model named in each file's header. See [`../../WORKFLOW.md`](../../WORKFLOW.md).
