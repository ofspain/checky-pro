/**
 * Operator endpoints (unlock, suspend, key revoke) — ADMIN-scoped, every action audited.
 *
 * <p>Module boundaries are enforced by ArchUnit: modules expose services, never entities;
 * see services/auth/docs/architecture/target-design.md §2.</p>
 */
package com.themistra.auth.admin;
