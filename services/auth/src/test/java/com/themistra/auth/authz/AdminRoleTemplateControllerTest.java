package com.themistra.auth.authz;

import com.themistra.auth.authz.dto.CreateRoleTemplateRequest;
import com.themistra.auth.authz.dto.RoleTemplateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRoleTemplateControllerTest {

    @Mock
    private RoleService roleService;

    @Test
    void createReturns201WithTheCreatedTemplate() {
        AdminRoleTemplateController controller = new AdminRoleTemplateController(roleService);
        RoleTemplateResponse created = new RoleTemplateResponse("power-merchant", "desc", Set.of("MERCHANT"));
        when(roleService.createRoleTemplate(any())).thenReturn(created);

        var response = controller.create(
                new CreateRoleTemplateRequest("power-merchant", "desc", Set.of("MERCHANT")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(created);
    }

    @Test
    void listDelegatesDirectly() {
        AdminRoleTemplateController controller = new AdminRoleTemplateController(roleService);
        when(roleService.listRoleTemplates()).thenReturn(
                List.of(new RoleTemplateResponse("power-merchant", null, Set.of("MERCHANT"))));

        assertThat(controller.list())
                .extracting(RoleTemplateResponse::name)
                .containsExactly("power-merchant");
    }
}
