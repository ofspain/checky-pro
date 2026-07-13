package com.themistra.auth.account;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private Account newAccount() {
        return Account.register("merchant@example.com", "{bcrypt}hash");
    }

    @Test
    void registrationStartsPendingWithUnverifiedEmailAndFreshUuid() {
        Account account = newAccount();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(account.isEmailVerified()).isFalse();
        assertThat(account.getAccountUuid()).isNotNull();
        assertThat(account.canAuthenticate()).isFalse();
    }

    @Nested
    class EmailActivation {

        @Test
        void activatesFromPending() {
            Account account = newAccount();

            account.activateEmail();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(account.isEmailVerified()).isTrue();
            assertThat(account.canAuthenticate()).isTrue();
        }

        @Test
        void rejectsSecondActivation() {
            Account account = newAccount();
            account.activateEmail();

            assertThatThrownBy(account::activateEmail)
                    .isInstanceOf(InvalidAccountStateException.class);
        }
    }

    @Nested
    class Suspension {

        @Test
        void suspendsActiveAccount() {
            Account account = newAccount();
            account.activateEmail();

            account.suspend();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
            assertThat(account.canAuthenticate()).isFalse();
        }

        @Test
        void suspendsPendingAccount() {
            Account account = newAccount();

            account.suspend();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        }

        @Test
        void rejectsDoubleSuspension() {
            Account account = newAccount();
            account.suspend();

            assertThatThrownBy(account::suspend)
                    .isInstanceOf(InvalidAccountStateException.class);
        }

        @Test
        void reinstateReturnsVerifiedAccountToActive() {
            Account account = newAccount();
            account.activateEmail();
            account.suspend();

            account.reinstate();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void reinstateReturnsUnverifiedAccountToPending() {
            Account account = newAccount();
            account.suspend();

            account.reinstate();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        }

        @Test
        void reinstateRequiresSuspendedState() {
            Account account = newAccount();

            assertThatThrownBy(account::reinstate)
                    .isInstanceOf(InvalidAccountStateException.class);
        }
    }

    @Nested
    class Locking {

        @Test
        void locksAndUnlocksActiveAccount() {
            Account account = newAccount();
            account.activateEmail();

            account.lock();
            assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
            assertThat(account.canAuthenticate()).isFalse();

            account.unlock();
            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void cannotLockPendingAccount() {
            assertThatThrownBy(newAccount()::lock)
                    .isInstanceOf(InvalidAccountStateException.class);
        }
    }

    @Nested
    class Deletion {

        @Test
        void deletionIsTerminalAndClearsCredential() {
            Account account = newAccount();
            account.activateEmail();

            account.markDeleted();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.DELETED);
            assertThat(account.getPasswordHash()).isNull();
            assertThatThrownBy(account::suspend).isInstanceOf(InvalidAccountStateException.class);
            assertThatThrownBy(account::markDeleted).isInstanceOf(InvalidAccountStateException.class);
            assertThatThrownBy(() -> account.changePasswordHash("{bcrypt}new"))
                    .isInstanceOf(InvalidAccountStateException.class);
        }
    }
}
