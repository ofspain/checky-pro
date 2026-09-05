package com.themistra.auth.apikey;

import com.themistra.auth.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end against real Postgres (Testcontainers) — verifies what a plain-JUnit entity test
 * structurally cannot: that {@code ApiKey} actually maps every {@code api_keys} column against
 * the real schema, most importantly the {@code scopes TEXT[]} column, which no other entity in
 * this service maps and which has no working precedent to copy (frozen brief, Phase 3 finding
 * #3). Account rows are inserted directly via {@link JdbcTemplate} rather than through
 * {@code AccountService} — this test has no need for a real account, only a valid
 * {@code account_id} to satisfy the FK, and going through {@code AccountService.register} would
 * pull in an unrelated HIBP breach-check network call this module has no reason to depend on
 * (frozen brief, disposition of Phase 3 finding #5/#8).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApiKeyPersistenceIntegrationTest {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private Long insertAccount(String email) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO accounts (account_uuid, email) VALUES (?, ?) RETURNING id",
                Long.class, UUID.randomUUID(), email);
    }

    @Test // AC1, AC5 — the actual point of this task: prove the scopes array mapping works
          // against the real column, not just that it compiles
    void apiKeyRoundTripsEveryColumnIncludingTheScopesArray() {
        Long accountId = insertAccount("apikey-round-trip@example.com");
        List<String> scopes = List.of("merchant.api", "merchant.reports");

        Long id = apiKeyRepository.saveAndFlush(
                ApiKey.create(accountId, "ck_live_abcd1234", "a".repeat(64), "CI pipeline key",
                        scopes, NOW)).getId();
        entityManager.clear();

        ApiKey reloaded = apiKeyRepository.findById(id).orElseThrow();
        assertThat(reloaded.getKeyUuid()).isNotNull();
        assertThat(reloaded.getAccountId()).isEqualTo(accountId);
        assertThat(reloaded.getPrefix()).isEqualTo("ck_live_abcd1234");
        assertThat(reloaded.getKeyHash()).isEqualTo("a".repeat(64));
        assertThat(reloaded.getName()).isEqualTo("CI pipeline key");
        assertThat(reloaded.getScopes()).containsExactly("merchant.api", "merchant.reports");
        assertThat(reloaded.getCreatedAt()).isEqualTo(NOW);

        String dbType = jdbcTemplate.queryForObject(
                "SELECT pg_typeof(scopes)::text FROM api_keys WHERE id = ?", String.class, id);
        assertThat(dbType).isEqualTo("text[]");
    }

    @Test // AC1 — nullable columns must round-trip as null, not as some default sentinel
    void apiKeyPersistsWithNullableFieldsAbsent() {
        Long accountId = insertAccount("apikey-nullable@example.com");

        Long id = apiKeyRepository.saveAndFlush(
                ApiKey.create(accountId, "ck_live_null0000", "b".repeat(64), "No optional fields",
                        List.of(), NOW)).getId();
        entityManager.clear();

        ApiKey reloaded = apiKeyRepository.findById(id).orElseThrow();
        assertThat(reloaded.getLastUsedAt()).isNull();
        assertThat(reloaded.getExpiresAt()).isNull();
        assertThat(reloaded.getRevokedAt()).isNull();
        assertThat(reloaded.getScopes()).isEmpty();
    }

    @Test // AC3 — the task statement's named requirement
    void findByPrefixReturnsMatchingKeys() {
        Long accountId = insertAccount("apikey-lookup@example.com");
        apiKeyRepository.saveAndFlush(
                ApiKey.create(accountId, "ck_live_lookupme", "c".repeat(64), "Lookup target",
                        List.of(), NOW));

        List<ApiKey> found = apiKeyRepository.findByPrefix("ck_live_lookupme");

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getPrefix()).isEqualTo("ck_live_lookupme");
    }

    @Test // Phase 3 finding #4's resolution: List, not Optional — "no match" is an empty list,
          // never null, never an exception
    void findByPrefixReturnsEmptyListForUnknownPrefix() {
        List<ApiKey> found = apiKeyRepository.findByPrefix("ck_live_doesnotexist");

        assertThat(found).isEmpty();
    }

    @Test // Null-safety constraint from the frozen brief
    void createRejectsNullRequiredArguments() {
        assertThatThrownBy(() -> ApiKey.create(null, "ck_live_x", "h".repeat(64), "n", List.of(), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ApiKey.create(1L, null, "h".repeat(64), "n", List.of(), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ApiKey.create(1L, "ck_live_x", null, "n", List.of(), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ApiKey.create(1L, "ck_live_x", "h".repeat(64), null, List.of(), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ApiKey.create(1L, "ck_live_x", "h".repeat(64), "n", List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test // Phase 9 finding #3's resolution: null elements rejected, the column is NOT NULL
    void createRejectsNullScopeElements() {
        List<String> scopesWithNull = java.util.Arrays.asList("merchant.api", null);

        assertThatThrownBy(() -> ApiKey.create(1L, "ck_live_x", "h".repeat(64), "n", scopesWithNull, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test // Phase 7/8 finding #1's resolution: the getter must not leak a mutable reference
    void getScopesReturnsADefensiveCopy() {
        Long accountId = insertAccount("apikey-defensive-copy@example.com");
        ApiKey saved = apiKeyRepository.saveAndFlush(
                ApiKey.create(accountId, "ck_live_defcopy0", "d".repeat(64), "Defensive copy check",
                        List.of("merchant.api"), NOW));

        List<String> scopes = saved.getScopes();

        assertThatThrownBy(() -> scopes.add("should-not-be-allowed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test // Phase 11 gap #1 — the reason findByPrefix returns List and not Optional
    void findByPrefixReturnsAllKeysSharingAPrefix() {
        Long accountId = insertAccount("apikey-shared-prefix@example.com");
        apiKeyRepository.saveAndFlush(
                ApiKey.create(accountId, "ck_live_dupe0001", "e".repeat(64), "First key",
                        List.of(), NOW));
        apiKeyRepository.saveAndFlush(
                ApiKey.create(accountId, "ck_live_dupe0001", "f".repeat(64), "Second key",
                        List.of(), NOW));

        List<ApiKey> found = apiKeyRepository.findByPrefix("ck_live_dupe0001");

        assertThat(found).hasSize(2);
        assertThat(found).extracting(ApiKey::getKeyHash)
                .containsExactlyInAnyOrder("e".repeat(64), "f".repeat(64));
    }

    @Test // Phase 11 gap #2 — the factory's documented null-to-empty-list default
    void createDefaultsNullScopesToEmptyList() {
        ApiKey apiKey = ApiKey.create(1L, "ck_live_x", "h".repeat(64), "n", null, NOW);

        assertThat(apiKey.getScopes()).isEmpty();
    }

    @Test // Phase 11 gap #3 — the factory's write-side defensive copy
    void createDefensivelyCopiesMutableScopesList() {
        Long accountId = insertAccount("apikey-defensive-write@example.com");
        List<String> mutableScopes = new ArrayList<>(List.of("merchant.api"));

        ApiKey apiKey = ApiKey.create(accountId, "ck_live_defwrit0", "i".repeat(64),
                "Defensive write check", mutableScopes, NOW);
        mutableScopes.add("caller-added-after-create");

        assertThat(apiKey.getScopes()).containsExactly("merchant.api");

        Long id = apiKeyRepository.saveAndFlush(apiKey).getId();
        entityManager.clear();
        assertThat(apiKeyRepository.findById(id).orElseThrow().getScopes())
                .containsExactly("merchant.api");
    }

    @Test // Phase 11 gap #4 — DB-level UNIQUE(key_hash) is actually enforced, not just mapped
    void duplicateKeyHashIsRejectedByTheDatabase() {
        Long accountId = insertAccount("apikey-dupe-hash@example.com");
        String sharedHash = "j".repeat(64);
        apiKeyRepository.saveAndFlush(
                ApiKey.create(accountId, "ck_live_hash0001", sharedHash, "Original key",
                        List.of(), NOW));

        assertThatThrownBy(() -> apiKeyRepository.saveAndFlush(
                ApiKey.create(accountId, "ck_live_hash0002", sharedHash, "Colliding key",
                        List.of(), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test // Phase 11 gap #5 — prefix is VARCHAR, not citext; lookup is case-sensitive
    void findByPrefixIsCaseSensitive() {
        Long accountId = insertAccount("apikey-case-sensitive@example.com");
        apiKeyRepository.saveAndFlush(
                ApiKey.create(accountId, "ck_live_ABCD5678", "k".repeat(64), "Mixed-case prefix",
                        List.of(), NOW));

        assertThat(apiKeyRepository.findByPrefix("ck_live_abcd5678")).isEmpty();
        assertThat(apiKeyRepository.findByPrefix("ck_live_ABCD5678")).hasSize(1);
    }
}
