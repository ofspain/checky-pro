package com.themistra.auth.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Package-private on purpose: other modules reach accounts through {@link AccountService},
 * never the repository (module-boundary rule, target-design §2).
 */
interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountUuid(UUID accountUuid);

    // email is citext in Postgres, so matching is case-insensitive at the database level
    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);
}
