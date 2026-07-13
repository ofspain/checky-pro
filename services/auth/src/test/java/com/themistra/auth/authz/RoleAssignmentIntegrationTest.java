package com.themistra.auth.authz;

import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.authz.dto.CreateRoleRequest;
import com.themistra.auth.authz.dto.CreateRoleTemplateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises RoleService's custom JPQL (RoleRepository#findDirectRoleNamesForAccount,
 * RoleTemplateRepository#findAssignedToAccount) against real Postgres — a mocked-repository
 * unit test can assert the service calls the repository correctly, but only a real database can
 * confirm the @Query strings themselves are correct (the risk this stage exists to retire).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RoleAssignmentIntegrationTest {

    @Autowired
    private RoleService roleService;

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void directAndTemplateExpandedRolesAreUnionedCorrectly() {
        UUID accountUuid = UUID.randomUUID();
        String directRole = unique("ROLE_DIRECT");
        String templateRoleA = unique("ROLE_TPL_A");
        String templateRoleB = unique("ROLE_TPL_B");
        String templateName = unique("template");

        roleService.createRole(new CreateRoleRequest(directRole, "direct"));
        roleService.createRole(new CreateRoleRequest(templateRoleA, "a"));
        roleService.createRole(new CreateRoleRequest(templateRoleB, "b"));
        roleService.createRoleTemplate(new CreateRoleTemplateRequest(
                templateName, "bundle", Set.of(templateRoleA, templateRoleB)));

        roleService.assignRole(accountUuid, directRole, null);
        roleService.assignRoleTemplate(accountUuid, templateName, null);

        assertThat(roleService.resolveEffectiveRoles(accountUuid))
                .containsExactlyInAnyOrder(directRole, templateRoleA, templateRoleB);
    }

    @Test
    void removingATemplateRemovesItsContributionButNotDirectRoles() {
        UUID accountUuid = UUID.randomUUID();
        String directRole = unique("ROLE_DIRECT");
        String templateRole = unique("ROLE_TPL");
        String templateName = unique("template");

        roleService.createRole(new CreateRoleRequest(directRole, null));
        roleService.createRole(new CreateRoleRequest(templateRole, null));
        roleService.createRoleTemplate(
                new CreateRoleTemplateRequest(templateName, null, Set.of(templateRole)));

        roleService.assignRole(accountUuid, directRole, null);
        roleService.assignRoleTemplate(accountUuid, templateName, null);
        assertThat(roleService.resolveEffectiveRoles(accountUuid)).contains(directRole, templateRole);

        roleService.removeRoleTemplate(accountUuid, templateName);

        assertThat(roleService.resolveEffectiveRoles(accountUuid))
                .contains(directRole)
                .doesNotContain(templateRole);
    }

    @Test
    void accountWithNothingAssignedHasNoEffectiveRoles() {
        assertThat(roleService.resolveEffectiveRoles(UUID.randomUUID())).isEmpty();
    }
}
