/**
 * Cross-cutting plumbing only — RFC 9457 error handling, validated configuration records, web config. No domain logic.
 *
 * <p>Module boundaries are enforced by ArchUnit: modules expose services, never entities;
 * see services/auth/docs/architecture/target-design.md §2.</p>
 */
package com.themistra.auth.common;
