package com.themistra.auth.authz;

import com.themistra.auth.authz.dto.CreateRoleRequest;
import com.themistra.auth.authz.dto.CreateRoleTemplateRequest;
import com.themistra.auth.authz.dto.RoleResponse;
import com.themistra.auth.authz.dto.RoleTemplateResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Role/template catalog management, per-account assignment, and effective-role resolution.
 * Operates on account UUIDs only — never the internal account id (D-017) — so this module has
 * no dependency on the account module's entities.
 */
@Service
@Validated
public class RoleService {

    private final RoleRepository roleRepository;
    private final RoleTemplateRepository roleTemplateRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final AccountRoleTemplateAssignmentRepository accountRoleTemplateAssignmentRepository;
    private final Clock clock;

    public RoleService(RoleRepository roleRepository,
                       RoleTemplateRepository roleTemplateRepository,
                       AccountRoleAssignmentRepository accountRoleAssignmentRepository,
                       AccountRoleTemplateAssignmentRepository accountRoleTemplateAssignmentRepository,
                       Clock clock) {
        this.roleRepository = roleRepository;
        this.roleTemplateRepository = roleTemplateRepository;
        this.accountRoleAssignmentRepository = accountRoleAssignmentRepository;
        this.accountRoleTemplateAssignmentRepository = accountRoleTemplateAssignmentRepository;
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

    // ===== Assignment =====

    @Transactional
    public void assignRole(UUID accountUuid, String roleName, UUID grantedBy) {
        Role role = requireRole(roleName);
        if (accountRoleAssignmentRepository.existsByIdAccountUuidAndIdRoleId(accountUuid, role.getId())) {
            return; // idempotent: already assigned
        }
        accountRoleAssignmentRepository.save(
                AccountRoleAssignment.of(accountUuid, role.getId(), grantedBy, clock.instant()));
    }

    @Transactional
    public void removeRole(UUID accountUuid, String roleName) {
        Role role = requireRole(roleName);
        accountRoleAssignmentRepository.deleteByAccountUuidAndRoleId(accountUuid, role.getId());
    }

    @Transactional
    public void assignRoleTemplate(UUID accountUuid, String templateName, UUID grantedBy) {
        RoleTemplate template = requireTemplate(templateName);
        if (accountRoleTemplateAssignmentRepository
                .existsByIdAccountUuidAndIdRoleTemplateId(accountUuid, template.getId())) {
            return; // idempotent
        }
        accountRoleTemplateAssignmentRepository.save(AccountRoleTemplateAssignment.of(
                accountUuid, template.getId(), grantedBy, clock.instant()));
    }

    @Transactional
    public void removeRoleTemplate(UUID accountUuid, String templateName) {
        RoleTemplate template = requireTemplate(templateName);
        accountRoleTemplateAssignmentRepository.deleteByAccountUuidAndRoleTemplateId(
                accountUuid, template.getId());
    }

    // ===== Resolution (called at token issuance — never cached, target-design §3) =====

    @Transactional(readOnly = true)
    public Set<String> resolveEffectiveRoles(UUID accountUuid) {
        Set<String> roles = new TreeSet<>(roleRepository.findDirectRoleNamesForAccount(accountUuid));

        roleTemplateRepository.findAssignedToAccount(accountUuid).forEach(template ->
                template.getRoles().forEach(role -> roles.add(role.getName())));

        return roles;
    }

    private Role requireRole(String name) {
        return roleRepository.findByName(name).orElseThrow(() -> new RoleNotFoundException(name));
    }

    private RoleTemplate requireTemplate(String name) {
        return roleTemplateRepository.findByName(name)
                .orElseThrow(() -> new RoleTemplateNotFoundException(name));
    }
}
