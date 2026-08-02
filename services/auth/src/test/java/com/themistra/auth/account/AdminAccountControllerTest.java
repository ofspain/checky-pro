package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.authn.LockoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
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
        when(authentication.getName()).thenReturn(actorUuid.toString());
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
    void getDelegatesDirectlyWithoutRequiringAnActor() {
        UUID accountUuid = UUID.randomUUID();
        AccountResponse expected = new AccountResponse(
                accountUuid, "user@example.com", true, AccountStatus.ACTIVE, Instant.now());
        when(accountService.getByUuid(accountUuid)).thenReturn(expected);

        assertThat(controller.get(accountUuid)).isEqualTo(expected);
    }
}
