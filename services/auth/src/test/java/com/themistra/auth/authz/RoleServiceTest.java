package com.themistra.auth.authz;

import com.themistra.auth.authz.dto.CreateRoleRequest;
import com.themistra.auth.authz.dto.CreateRoleTemplateRequest;
import com.themistra.auth.authz.dto.RoleResponse;
import com.themistra.auth.authz.dto.RoleTemplateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    private static final UUID ACCOUNT_UUID = UUID.randomUUID();
    private static final UUID ADMIN_UUID = UUID.randomUUID();

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RoleTemplateRepository roleTemplateRepository;

    @Mock
    private AccountRoleAssignmentRepository accountRoleAssignmentRepository;

    @Mock
    private AccountRoleTemplateAssignmentRepository accountRoleTemplateAssignmentRepository;

    private RoleService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);
        service = new RoleService(roleRepository, roleTemplateRepository,
                accountRoleAssignmentRepository, accountRoleTemplateAssignmentRepository, fixed);
    }

    @Test
    void createRoleRejectsKnownDuplicate() {
        when(roleRepository.existsByName("MERCHANT")).thenReturn(true);

        assertThatThrownBy(() -> service.createRole(new CreateRoleRequest("MERCHANT", "desc")))
                .isInstanceOf(DuplicateRoleException.class);

        verify(roleRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRoleMapsConstraintRaceToDuplicate() {
        when(roleRepository.existsByName("MERCHANT")).thenReturn(false);
        when(roleRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> service.createRole(new CreateRoleRequest("MERCHANT", "desc")))
                .isInstanceOf(DuplicateRoleException.class);
    }

    @Test
    void createRoleReturnsView() {
        when(roleRepository.existsByName("MERCHANT")).thenReturn(false);
        when(roleRepository.saveAndFlush(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        RoleResponse response = service.createRole(new CreateRoleRequest("MERCHANT", "desc"));

        assertThat(response.name()).isEqualTo("MERCHANT");
        assertThat(response.description()).isEqualTo("desc");
    }

    @Test
    void createRoleTemplateExpandsNamesToRolesAndSucceeds() {
        Role merchant = Role.create("MERCHANT", null);
        Role user = Role.create("USER", null);
        when(roleTemplateRepository.existsByName("power-merchant")).thenReturn(false);
        when(roleRepository.findByNameIn(Set.of("MERCHANT", "USER")))
                .thenReturn(List.of(merchant, user));
        when(roleTemplateRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        RoleTemplateResponse response = service.createRoleTemplate(
                new CreateRoleTemplateRequest("power-merchant", "desc", Set.of("MERCHANT", "USER")));

        assertThat(response.name()).isEqualTo("power-merchant");
        assertThat(response.roleNames()).containsExactlyInAnyOrder("MERCHANT", "USER");
    }

    @Test
    void createRoleTemplateRejectsUnknownRoleName() {
        when(roleTemplateRepository.existsByName("bad-template")).thenReturn(false);
        when(roleRepository.findByNameIn(Set.of("GHOST"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.createRoleTemplate(
                new CreateRoleTemplateRequest("bad-template", "desc", Set.of("GHOST"))))
                .isInstanceOf(RoleNotFoundException.class);

        verify(roleTemplateRepository, never()).saveAndFlush(any());
    }

    @Test
    void assignRoleIsIdempotentWhenAlreadyAssigned() {
        Role role = roleWithId(1L, "MERCHANT");
        when(roleRepository.findByName("MERCHANT")).thenReturn(Optional.of(role));
        when(accountRoleAssignmentRepository.existsByIdAccountUuidAndIdRoleId(ACCOUNT_UUID, 1L))
                .thenReturn(true);

        service.assignRole(ACCOUNT_UUID, "MERCHANT", ADMIN_UUID);

        verify(accountRoleAssignmentRepository, never()).save(any());
    }

    @Test
    void assignRoleSavesNewAssignment() {
        Role role = roleWithId(1L, "MERCHANT");
        when(roleRepository.findByName("MERCHANT")).thenReturn(Optional.of(role));
        when(accountRoleAssignmentRepository.existsByIdAccountUuidAndIdRoleId(ACCOUNT_UUID, 1L))
                .thenReturn(false);

        service.assignRole(ACCOUNT_UUID, "MERCHANT", ADMIN_UUID);

        verify(accountRoleAssignmentRepository).save(any());
    }

    @Test
    void assignRoleRejectsUnknownRoleName() {
        when(roleRepository.findByName("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRole(ACCOUNT_UUID, "GHOST", ADMIN_UUID))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void removeRoleDelegatesToRepository() {
        Role role = roleWithId(1L, "MERCHANT");
        when(roleRepository.findByName("MERCHANT")).thenReturn(Optional.of(role));

        service.removeRole(ACCOUNT_UUID, "MERCHANT");

        verify(accountRoleAssignmentRepository).deleteByAccountUuidAndRoleId(ACCOUNT_UUID, 1L);
    }

    @Test
    void resolveEffectiveRolesUnionsDirectAndTemplateExpandedRoles() {
        when(roleRepository.findDirectRoleNamesForAccount(ACCOUNT_UUID))
                .thenReturn(Set.of("USER"));

        RoleTemplate template = RoleTemplate.create(
                "power-merchant", "desc",
                Set.of(roleWithId(2L, "MERCHANT"), roleWithId(3L, "COMPLIANCE")));
        when(roleTemplateRepository.findAssignedToAccount(ACCOUNT_UUID)).thenReturn(List.of(template));

        Set<String> effective = service.resolveEffectiveRoles(ACCOUNT_UUID);

        assertThat(effective).containsExactlyInAnyOrder("USER", "MERCHANT", "COMPLIANCE");
    }

    @Test
    void resolveEffectiveRolesIsEmptyWhenNothingAssigned() {
        when(roleRepository.findDirectRoleNamesForAccount(ACCOUNT_UUID)).thenReturn(Set.of());
        when(roleTemplateRepository.findAssignedToAccount(ACCOUNT_UUID)).thenReturn(List.of());

        assertThat(service.resolveEffectiveRoles(ACCOUNT_UUID)).isEmpty();
    }

    private static Role roleWithId(long id, String name) {
        Role role = Role.create(name, null);
        setId(role, id);
        return role;
    }

    private static void setId(Role role, long id) {
        try {
            var field = Role.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(role, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
