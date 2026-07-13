package com.themistra.auth.authz;

import com.themistra.auth.authz.dto.CreateRoleRequest;
import com.themistra.auth.authz.dto.RoleResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRoleControllerTest {

    @Mock
    private RoleService roleService;

    @Test
    void createReturns201WithTheCreatedRole() {
        AdminRoleController controller = new AdminRoleController(roleService);
        RoleResponse created = new RoleResponse("MERCHANT", "desc");
        when(roleService.createRole(any())).thenReturn(created);

        var response = controller.create(new CreateRoleRequest("MERCHANT", "desc"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(created);
    }

    @Test
    void listDelegatesDirectly() {
        AdminRoleController controller = new AdminRoleController(roleService);
        when(roleService.listRoles()).thenReturn(List.of(new RoleResponse("USER", null)));

        assertThat(controller.list()).extracting(RoleResponse::name).containsExactly("USER");
    }
}
