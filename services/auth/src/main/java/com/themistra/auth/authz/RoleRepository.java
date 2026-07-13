package com.themistra.auth.authz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    List<Role> findByNameIn(Set<String> names);

    @Query("""
            select r.name from Role r
            join AccountRoleAssignment a on a.id.roleId = r.id
            where a.id.accountUuid = :accountUuid
            """)
    Set<String> findDirectRoleNamesForAccount(@Param("accountUuid") UUID accountUuid);
}
