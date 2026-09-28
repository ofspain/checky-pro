package com.themistra.notification.preference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface ContactProjectionRepository extends JpaRepository<ContactProjection, UUID> {

    /**
     * {@code INSERT ... ON CONFLICT (account_uuid) DO UPDATE} - a genuine upsert, unlike T04's own
     * insert-or-skip {@code ProcessedEventRepository.insertIfNew}: {@code contact_projection} is
     * expected to be revised over an account's lifetime, not written once. The trailing
     * {@code WHERE ... updated_at <= EXCLUDED.updated_at} guards against out-of-order delivery -
     * {@code auth.email.requested} and {@code auth.user.lifecycle} are two different Kafka topics
     * with no cross-topic ordering guarantee, so a later-processed but chronologically-older event
     * (by its own {@code occurredAt}) must never overwrite a newer projection state with stale
     * data. {@code <=} (not {@code <}) means ties - two events for the same account at genuinely
     * equal {@code occurredAt} - resolve to the later-processed call winning; safe in practice
     * since both source events derive {@code email} from the same underlying
     * {@code Account.email} field at auth-service, making a genuine value conflict at equal
     * timestamps effectively impossible.
     *
     * <p>Returns the affected-row count: {@code 1} on a genuine insert or an accepted update,
     * {@code 0} when the {@code WHERE} guard rejected an out-of-order call.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO notifications.contact_projection (account_uuid, email, updated_at) "
            + "VALUES (:accountUuid, :email, :updatedAt) "
            + "ON CONFLICT (account_uuid) DO UPDATE SET email = EXCLUDED.email, updated_at = EXCLUDED.updated_at "
            + "WHERE notifications.contact_projection.updated_at <= EXCLUDED.updated_at",
            nativeQuery = true)
    int upsertEmail(@Param("accountUuid") UUID accountUuid, @Param("email") String email,
                     @Param("updatedAt") Instant updatedAt);
}
