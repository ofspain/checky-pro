package com.themistra.auth.authz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountRoleControllerTest {

    @Mock
    private RoleService roleService;

    private AdminAccountRoleController controller;
    private UUID actorUuid;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        controller = new AdminAccountRoleController(roleService);
        actorUuid = UUID.randomUUID();
        authentication = mock(Authentication.class);
        // Not every test below calls a controller method that reads the authentication (e.g.
        // effectiveRolesDelegatesDirectly doesn't), so this shared stub must be lenient.
        lenient().when(authentication.getName()).thenReturn(actorUuid.toString());
    }

    @Test
    void effectiveRolesDelegatesDirectly() {
        UUID accountUuid = UUID.randomUUID();
        when(roleService.resolveEffectiveRoles(accountUuid)).thenReturn(Set.of("USER", "MERCHANT"));

        assertThat(controller.effectiveRoles(accountUuid)).containsExactlyInAnyOrder("USER", "MERCHANT");
    }

    @Test
    void assignRoleThreadsTheAuthenticatedActorAndReturnsNoContent() {
        UUID accountUuid = UUID.randomUUID();

        var response = controller.assignRole(accountUuid, "MERCHANT", authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(roleService).assignRole(accountUuid, "MERCHANT", actorUuid);
    }

    @Test
    void removeRoleThreadsTheAuthenticatedActor() {
        UUID accountUuid = UUID.randomUUID();

        controller.removeRole(accountUuid, "MERCHANT", authentication);

        verify(roleService).removeRole(accountUuid, "MERCHANT", actorUuid);
    }

    @Test
    void assignRoleTemplateThreadsTheAuthenticatedActor() {
        UUID accountUuid = UUID.randomUUID();

        controller.assignRoleTemplate(accountUuid, "power-merchant", authentication);

        verify(roleService).assignRoleTemplate(accountUuid, "power-merchant", actorUuid);
    }

    @Test
    void removeRoleTemplateThreadsTheAuthenticatedActor() {
        UUID accountUuid = UUID.randomUUID();

        controller.removeRoleTemplate(accountUuid, "power-merchant", authentication);

        verify(roleService).removeRoleTemplate(accountUuid, "power-merchant", actorUuid);
    }
}
