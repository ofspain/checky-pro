/**
 * Interactive authentication customization for Spring Authorization Server — password auth, MFA step (D-014), lockout, NIST password policy (D-006), password reset.
 *
 * <p>Module boundaries are enforced by ArchUnit: modules expose services, never entities;
 * see services/auth/docs/architecture/target-design.md §2.</p>
 */
package com.themistra.auth.authn;
