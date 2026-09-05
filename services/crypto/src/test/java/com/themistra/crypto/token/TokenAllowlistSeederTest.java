package com.themistra.crypto.token;

import com.themistra.crypto.common.config.TokenAllowlistProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AC5 (seed data), AC8 (seeder resilience). */
@ExtendWith(MockitoExtension.class)
class TokenAllowlistSeederTest {

    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");

    @Mock
    private TokenAllowlistRepository repository;

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private TokenAllowlistProperties.Entry entry(String chain, String address, int version) {
        return new TokenAllowlistProperties.Entry(chain, address, "USDT", 6, version, "sig");
    }

    @Test
    void seedsEveryConfiguredEntryThatDoesNotAlreadyExist() {
        // Phase 11 Gap 5: distinct symbol/decimals/signature per entry so a mapping/factory bug that
        // swapped fields between entries would actually be caught, not just contractAddress/createdAt.
        TokenAllowlistProperties.Entry ethereumEntry =
                new TokenAllowlistProperties.Entry("ETHEREUM", "0xa", "USDT", 6, 1, "sig-a");
        TokenAllowlistProperties.Entry tronEntry =
                new TokenAllowlistProperties.Entry("TRON", "Tb", "USDC", 18, 2, "sig-b");
        TokenAllowlistProperties properties = new TokenAllowlistProperties(List.of(ethereumEntry, tronEntry));
        when(repository.findByChainAndContractAddressAndVersion(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Optional.empty());
        TokenAllowlistSeeder seeder = new TokenAllowlistSeeder(repository, properties, fixedClock);

        seeder.run(new DefaultApplicationArguments());

        ArgumentCaptor<TokenAllowlist> captor = ArgumentCaptor.forClass(TokenAllowlist.class);
        verify(repository, times(2)).save(captor.capture());
        TokenAllowlist savedEthereum = captor.getAllValues().stream()
                .filter(t -> t.chain().equals("ETHEREUM")).findFirst().orElseThrow();
        TokenAllowlist savedTron = captor.getAllValues().stream()
                .filter(t -> t.chain().equals("TRON")).findFirst().orElseThrow();

        assertThat(savedEthereum.contractAddress()).isEqualTo("0xa");
        assertThat(savedEthereum.symbol()).isEqualTo("USDT");
        assertThat(savedEthereum.decimals()).isEqualTo((short) 6);
        assertThat(savedEthereum.version()).isEqualTo(1);
        assertThat(savedEthereum.signature()).isEqualTo("sig-a");
        assertThat(savedEthereum.createdAt()).isEqualTo(NOW);

        assertThat(savedTron.contractAddress()).isEqualTo("Tb");
        assertThat(savedTron.symbol()).isEqualTo("USDC");
        assertThat(savedTron.decimals()).isEqualTo((short) 18);
        assertThat(savedTron.version()).isEqualTo(2);
        assertThat(savedTron.signature()).isEqualTo("sig-b");
        assertThat(savedTron.createdAt()).isEqualTo(NOW);
    }

    @Test
    void skipsAnEntryThatAlreadyExists() {
        TokenAllowlistProperties properties = new TokenAllowlistProperties(List.of(entry("ETHEREUM", "0xa", 1)));
        when(repository.findByChainAndContractAddressAndVersion("ETHEREUM", "0xa", 1))
                .thenReturn(Optional.of(TokenAllowlist.create("ETHEREUM", "0xa", "USDT", 6, 1, "sig", NOW)));
        TokenAllowlistSeeder seeder = new TokenAllowlistSeeder(repository, properties, fixedClock);

        seeder.run(new DefaultApplicationArguments());

        verify(repository, never()).save(any());
    }

    @Test
    void catchesAConcurrentDuplicateInsertWhenTheRowNowActuallyExists() {
        TokenAllowlistProperties properties = new TokenAllowlistProperties(List.of(entry("ETHEREUM", "0xa", 1)));
        when(repository.findByChainAndContractAddressAndVersion("ETHEREUM", "0xa", 1))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(TokenAllowlist.create("ETHEREUM", "0xa", "USDT", 6, 1, "sig", NOW)));
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        TokenAllowlistSeeder seeder = new TokenAllowlistSeeder(repository, properties, fixedClock);

        seeder.run(new DefaultApplicationArguments());

        verify(repository, times(2)).findByChainAndContractAddressAndVersion("ETHEREUM", "0xa", 1);
    }

    @Test
    void rethrowsWhenTheDataIntegrityViolationIsNotABenignConcurrentDuplicate() {
        // Phase 9 (Kimi Phase 8 Issue 5): if the row still doesn't exist after the exception, this
        // was a genuine, different integrity violation - must not be swallowed as a harmless race.
        TokenAllowlistProperties properties = new TokenAllowlistProperties(List.of(entry("ETHEREUM", "0xa", 1)));
        when(repository.findByChainAndContractAddressAndVersion("ETHEREUM", "0xa", 1))
                .thenReturn(Optional.empty());
        DataIntegrityViolationException genuineFailure = new DataIntegrityViolationException("not null constraint");
        when(repository.save(any())).thenThrow(genuineFailure);
        TokenAllowlistSeeder seeder = new TokenAllowlistSeeder(repository, properties, fixedClock);

        assertThatThrownBy(() -> seeder.run(new DefaultApplicationArguments()))
                .isSameAs(genuineFailure);
    }

    @Test
    void continuesProcessingRemainingEntriesAfterOneEntryHitsTheBenignConcurrentRace() {
        TokenAllowlistProperties properties = new TokenAllowlistProperties(
                List.of(entry("ETHEREUM", "0xa", 1), entry("TRON", "Tb", 1)));
        when(repository.findByChainAndContractAddressAndVersion("ETHEREUM", "0xa", 1))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(TokenAllowlist.create("ETHEREUM", "0xa", "USDT", 6, 1, "sig", NOW)));
        when(repository.findByChainAndContractAddressAndVersion("TRON", "Tb", 1))
                .thenReturn(Optional.empty());
        when(repository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TokenAllowlistSeeder seeder = new TokenAllowlistSeeder(repository, properties, fixedClock);

        seeder.run(new DefaultApplicationArguments());

        ArgumentCaptor<TokenAllowlist> captor = ArgumentCaptor.forClass(TokenAllowlist.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).contractAddress()).isEqualTo("Tb");
    }

    @Test
    void runRejectsANullEntriesList() {
        // Phase 9 (Kimi Phase 8 Issue 9).
        TokenAllowlistProperties properties = new TokenAllowlistProperties(null);
        TokenAllowlistSeeder seeder = new TokenAllowlistSeeder(repository, properties, fixedClock);

        assertThatThrownBy(() -> seeder.run(new DefaultApplicationArguments()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entries");
    }
}
