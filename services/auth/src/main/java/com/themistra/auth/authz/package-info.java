/**
 * Roles, role templates (bundles), and their assignment. Template expansion happens at token issuance.
 *
 * <p>Module boundaries are enforced by ArchUnit: modules expose services, never entities;
 * see services/auth/docs/architecture/target-design.md §2.</p>
 */
package com.themistra.auth.authz;
