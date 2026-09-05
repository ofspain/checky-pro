package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.authn.LockoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountControllerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private LockoutService lockoutService;

    private AdminAccountController controller;
    private UUID actorUuid;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        controller = new AdminAccountController(accountService, lockoutService);
        actorUuid = UUID.randomUUID();
        authentication = mock(Authentication.class);
        // lenient: not every test needs an actor (get() takes none; the new reflection-only
        // unlockRequiresExactlyAdminOrComplianceRole test needs no mock at all) - was already
        // pre-existing UnnecessaryStubbingException territory for getDelegatesDirectlyWithout
        // RequiringAnActor before this task; made lenient here because Phase 11's own new
        // reflection test hits the identical failure and must not be left broken.
        lenient().when(authentication.getName()).thenReturn(actorUuid.toString());
    }

    @Test
    void activateThreadsTheAuthenticatedActorThrough() {
        UUID accountUuid = UUID.randomUUID();
        AccountResponse expected = new AccountResponse(
                accountUuid, "user@example.com", true, AccountStatus.ACTIVE, Instant.now());
        when(accountService.activateEmail(accountUuid, actorUuid)).thenReturn(expected);

        assertThat(controller.activate(accountUuid, authentication)).isEqualTo(expected);
        verify(accountService).activateEmail(accountUuid, actorUuid);
    }

    @Test
    void suspendThreadsTheAuthenticatedActorThrough() {
        UUID accountUuid = UUID.randomUUID();
        AccountResponse expected = new AccountResponse(
                accountUuid, "user@example.com", true, AccountStatus.SUSPENDED, Instant.now());
        when(accountService.suspend(accountUuid, actorUuid)).thenReturn(expected);

        assertThat(controller.suspend(accountUuid, authentication)).isEqualTo(expected);
    }

    @Test
    void reinstateThreadsTheAuthenticatedActorThrough() {
        UUID accountUuid = UUID.randomUUID();
        AccountResponse expected = new AccountResponse(
                accountUuid, "user@example.com", true, AccountStatus.ACTIVE, Instant.now());
        when(accountService.reinstate(accountUuid, actorUuid)).thenReturn(expected);

        assertThat(controller.reinstate(accountUuid, authentication)).isEqualTo(expected);
    }

    @Test
    void deleteThreadsTheAuthenticatedActorThrough() {
        UUID accountUuid = UUID.randomUUID();
        AccountResponse expected = new AccountResponse(
                accountUuid, "user@example.com", true, AccountStatus.DELETED, Instant.now());
        when(accountService.delete(accountUuid, actorUuid)).thenReturn(expected);

        assertThat(controller.delete(accountUuid, authentication)).isEqualTo(expected);
    }

    @Test
    void unlockCallsResetLockoutThenAdminUnlockWithTheAuthenticatedActor() {
        UUID accountUuid = UUID.randomUUID();
        AccountResponse expected = new AccountResponse(
                accountUuid, "user@example.com", true, AccountStatus.ACTIVE, Instant.now());
        when(accountService.adminUnlock(accountUuid, actorUuid)).thenReturn(expected);

        assertThat(controller.unlock(accountUuid, authentication)).isEqualTo(expected);

        InOrder order = inOrder(lockoutService, accountService);
        order.verify(lockoutService).resetLockout(accountUuid);
        order.verify(accountService).adminUnlock(accountUuid, actorUuid);
    }

    @Test
    void unlockRequiresExactlyAdminOrComplianceRole() throws NoSuchMethodException {
        // Phase 11 Gap 3: ArchitectureTest only proves @PreAuthorize is present on every admin
        // handler, not that the expression is correct - a typo (e.g. hasAnyRole('ADMIN') alone)
        // would pass that ArchUnit rule but silently narrow AC5's required access. A live
        // MockMvc/WebTestClient 403 test would be the first such test anywhere in this module
        // (confirmed absent at Phase 0/10) - a bigger infrastructure decision than this one
        // assertion warrants, so this checks the exact SpEL expression by reflection instead.
        Method unlock = AdminAccountController.class.getMethod("unlock", UUID.class, Authentication.class);
        PreAuthorize preAuthorize = unlock.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAnyRole('ADMIN', 'COMPLIANCE')");
    }

    @Test
    void getDelegatesDirectlyWithoutRequiringAnActor() {
        UUID accountUuid = UUID.randomUUID();
        AccountResponse expected = new AccountResponse(
                accountUuid, "user@example.com", true, AccountStatus.ACTIVE, Instant.now());
        when(accountService.getByUuid(accountUuid)).thenReturn(expected);

        assertThat(controller.get(accountUuid)).isEqualTo(expected);
    }
}
