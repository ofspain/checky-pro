package com.themistra.auth.authz;

import com.themistra.auth.authz.dto.CreateRoleTemplateRequest;
import com.themistra.auth.authz.dto.RoleTemplateResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/role-templates")
public class AdminRoleTemplateController {

    private final RoleService roleService;

    public AdminRoleTemplateController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoleTemplateResponse> create(
            @Valid @RequestBody CreateRoleTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRoleTemplate(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<RoleTemplateResponse> list() {
        return roleService.listRoleTemplates();
    }
}
