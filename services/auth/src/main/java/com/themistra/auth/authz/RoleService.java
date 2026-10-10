package com.themistra.auth.authz;

import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.authz.dto.CreateRoleRequest;
import com.themistra.auth.authz.dto.CreateRoleTemplateRequest;
import com.themistra.auth.authz.dto.RoleResponse;
import com.themistra.auth.authz.dto.RoleTemplateResponse;
import com.themistra.auth.mfa.MfaService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Role/template catalog management, per-account assignment, and effective-role resolution.
 * Operates on account UUIDs only — never the internal account id (D-017) — so this module has
 * no dependency on the account module's entities. Assignment/removal are audited (D-024): these
 * are admin-only actions once exposed over HTTP, same reasoning as D-022's account-lifecycle scope.
 *
 * <p>Depends on {@link MfaService} (a cross-module service dependency, same accepted pattern as
 * {@code ApiKeyService}'s own identical dependency, and {@code LockoutService}&rarr;
 * {@code AccountService} before it — {@code ArchitectureTest}'s module-boundary rule constrains
 * controller&rarr;service dependencies only, not service&rarr;service) to enforce D-031: an
 * account cannot be granted {@code MERCHANT}/{@code ADMIN} — directly or via a template — without
 * a confirmed TOTP enrollment already in place.</p>
 */
@Service
@Validated
public class RoleService {

    /** R24/D-031: the two roles SAS's login flow (T20) refuses to authenticate without a
     * confirmed TOTP enrollment. Granting either one first would lock the account out of login
     * entirely, with no path back in except an admin removing the role again. */
    private static final Set<String> MFA_GATED_ROLES = Set.of("MERCHANT", "ADMIN");

    private final RoleRepository roleRepository;
    private final RoleTemplateRepository roleTemplateRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final AccountRoleTemplateAssignmentRepository accountRoleTemplateAssignmentRepository;
    private final AuditService auditService;
    private final MfaService mfaService;
    private final Clock clock;

    public RoleService(RoleRepository roleRepository,
                       RoleTemplateRepository roleTemplateRepository,
                       AccountRoleAssignmentRepository accountRoleAssignmentRepository,
                       AccountRoleTemplateAssignmentRepository accountRoleTemplateAssignmentRepository,
                       AuditService auditService,
                       MfaService mfaService,
                       Clock clock) {
        this.roleRepository = roleRepository;
        this.roleTemplateRepository = roleTemplateRepository;
        this.accountRoleAssignmentRepository = accountRoleAssignmentRepository;
        this.accountRoleTemplateAssignmentRepository = accountRoleTemplateAssignmentRepository;
        this.auditService = auditService;
        this.mfaService = mfaService;
        this.clock = clock;
    }

    // ===== Catalog management =====

    @Transactional
    public RoleResponse createRole(@Valid CreateRoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new DuplicateRoleException();
        }
        try {
            Role role = roleRepository.saveAndFlush(Role.create(request.name(), request.description()));
            return RoleResponse.from(role);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateRoleException();
        }
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream().map(RoleResponse::from).toList();
    }

    @Transactional
    public RoleTemplateResponse createRoleTemplate(@Valid CreateRoleTemplateRequest request) {
        if (roleTemplateRepository.existsByName(request.name())) {
            throw new DuplicateRoleTemplateException();
        }

        List<Role> roles = roleRepository.findByNameIn(request.roleNames());
        if (roles.size() != request.roleNames().size()) {
            Set<String> found = roles.stream().map(Role::getName).collect(Collectors.toSet());
            String missing = request.roleNames().stream()
                    .filter(name -> !found.contains(name))
                    .findFirst()
                    .orElseThrow();
            throw new RoleNotFoundException(missing);
        }

        try {
            RoleTemplate template = roleTemplateRepository.saveAndFlush(
                    RoleTemplate.create(request.name(), request.description(), new LinkedHashSet<>(roles)));
            return RoleTemplateResponse.from(template);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateRoleTemplateException();
        }
    }

    @Transactional(readOnly = true)
    public List<RoleTemplateResponse> listRoleTemplates() {
        return roleTemplateRepository.findAll().stream().map(RoleTemplateResponse::from).toList();
    }

    // ===== Assignment =====

    @Transactional
    public void assignRole(UUID accountUuid, String roleName, UUID actorUuid) {
        Role role = requireRole(roleName);
        if (accountRoleAssignmentRepository.existsByIdAccountUuidAndIdRoleId(accountUuid, role.getId())) {
            return; // idempotent: already assigned, nothing changed, nothing to audit
        }
        requireConfirmedMfaIfGated(accountUuid, Set.of(roleName));
        accountRoleAssignmentRepository.save(
                AccountRoleAssignment.of(accountUuid, role.getId(), actorUuid, clock.instant()));
        auditAssignment("role.assigned", accountUuid, actorUuid, roleName);
    }

    @Transactional
    public void removeRole(UUID accountUuid, String roleName, UUID actorUuid) {
        Role role = requireRole(roleName);
        if (!accountRoleAssignmentRepository.existsByIdAccountUuidAndIdRoleId(accountUuid, role.getId())) {
            return; // idempotent: wasn't assigned
        }
        accountRoleAssignmentRepository.deleteByAccountUuidAndRoleId(accountUuid, role.getId());
        auditAssignment("role.removed", accountUuid, actorUuid, roleName);
    }

    @Transactional
    public void assignRoleTemplate(UUID accountUuid, String templateName, UUID actorUuid) {
        RoleTemplate template = requireTemplate(templateName);
        if (accountRoleTemplateAssignmentRepository
                .existsByIdAccountUuidAndIdRoleTemplateId(accountUuid, template.getId())) {
            return; // idempotent
        }
        // D-031: a template can bundle MERCHANT/ADMIN among its roles (createRoleTemplate places
        // no restriction on which roles a template may name) - gated identically to assignRole,
        // or this would trivially bypass that guard.
        Set<String> bundledRoleNames = template.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        requireConfirmedMfaIfGated(accountUuid, bundledRoleNames);
        accountRoleTemplateAssignmentRepository.save(AccountRoleTemplateAssignment.of(
                accountUuid, template.getId(), actorUuid, clock.instant()));
        auditAssignment("role_template.assigned", accountUuid, actorUuid, templateName);
    }

    @Transactional
    public void removeRoleTemplate(UUID accountUuid, String templateName, UUID actorUuid) {
        RoleTemplate template = requireTemplate(templateName);
        if (!accountRoleTemplateAssignmentRepository
                .existsByIdAccountUuidAndIdRoleTemplateId(accountUuid, template.getId())) {
            return; // idempotent
        }
        accountRoleTemplateAssignmentRepository.deleteByAccountUuidAndRoleTemplateId(
                accountUuid, template.getId());
        auditAssignment("role_template.removed", accountUuid, actorUuid, templateName);
    }

    // ===== Resolution (called at token issuance — never cached, target-design §3) =====

    @Transactional(readOnly = true)
    public Set<String> resolveEffectiveRoles(UUID accountUuid) {
        Set<String> roles = new TreeSet<>(roleRepository.findDirectRoleNamesForAccount(accountUuid));

        roleTemplateRepository.findAssignedToAccount(accountUuid).forEach(template ->
                template.getRoles().forEach(role -> roles.add(role.getName())));

        return roles;
    }

    private void auditAssignment(String eventType, UUID accountUuid, UUID actorUuid, String roleOrTemplateName) {
        auditService.record(new RecordAuditEventRequest(
                eventType, AuditOutcome.SUCCESS, accountUuid, actorUuid,
                null, null, null, Map.of("name", roleOrTemplateName)));
    }

    /** D-031: blocks granting {@code MERCHANT}/{@code ADMIN} (directly or via a template) unless
     * the account already has a confirmed TOTP enrollment. Other roles (e.g. {@code USER},
     * {@code COMPLIANCE}) are never gated — only the two SAS's login flow (R24) itself refuses to
     * authenticate without one. */
    private void requireConfirmedMfaIfGated(UUID accountUuid, Set<String> roleNames) {
        boolean grantsGatedRole = roleNames.stream().anyMatch(MFA_GATED_ROLES::contains);
        if (grantsGatedRole && !mfaService.hasConfirmedTotpEnrollment(accountUuid)) {
            String gatedRoleName = roleNames.stream().filter(MFA_GATED_ROLES::contains).findFirst().orElseThrow();
            throw new RoleRequiresConfirmedMfaException(gatedRoleName);
        }
    }

    private Role requireRole(String name) {
        return roleRepository.findByName(name).orElseThrow(() -> new RoleNotFoundException(name));
    }

    private RoleTemplate requireTemplate(String name) {
        return roleTemplateRepository.findByName(name)
                .orElseThrow(() -> new RoleTemplateNotFoundException(name));
    }
}
