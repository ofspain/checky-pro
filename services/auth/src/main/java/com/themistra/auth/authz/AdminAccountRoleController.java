package com.themistra.auth.authz;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/** Per-account role/template assignment. All admin-only — RBAC administration, not self-service. */
@RestController
@RequestMapping("/admin/accounts/{accountUuid}")
public class AdminAccountRoleController {

    private final RoleService roleService;

    public AdminAccountRoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public Set<String> effectiveRoles(@PathVariable UUID accountUuid) {
        return roleService.resolveEffectiveRoles(accountUuid);
    }

    @PostMapping("/roles/{roleName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignRole(
            @PathVariable UUID accountUuid, @PathVariable String roleName, Authentication authentication) {
        roleService.assignRole(accountUuid, roleName, actorUuid(authentication));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/roles/{roleName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeRole(
            @PathVariable UUID accountUuid, @PathVariable String roleName, Authentication authentication) {
        roleService.removeRole(accountUuid, roleName, actorUuid(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/role-templates/{templateName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignRoleTemplate(
            @PathVariable UUID accountUuid, @PathVariable String templateName, Authentication authentication) {
        roleService.assignRoleTemplate(accountUuid, templateName, actorUuid(authentication));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/role-templates/{templateName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeRoleTemplate(
            @PathVariable UUID accountUuid, @PathVariable String templateName, Authentication authentication) {
        roleService.removeRoleTemplate(accountUuid, templateName, actorUuid(authentication));
        return ResponseEntity.noContent().build();
    }

    private static UUID actorUuid(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
