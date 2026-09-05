package com.themistra.crypto.token;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** The named tests from package.md §8 (`shouldIdentifyTokenByContractAddressNotSymbol`,
 * `shouldSurfaceUnknownTokenForNonAllowlistedContract`), AC1, AC2, AC7. {@link
 * TokenAllowlistRepository} is mocked - the actual per-chain current-version query logic is proven
 * against a real Postgres by {@code TokenAllowlistRepositoryIntegrationTest}, not here. */
@ExtendWith(MockitoExtension.class)
class TokenValidatorTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-05T12:00:00Z");

    @Mock
    private TokenAllowlistRepository repository;

    private TokenValidator validator;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        validator = new TokenValidator(repository);
    }

    @Test
    void shouldIdentifyTokenByContractAddressNotSymbol() {
        // AC1: a deliberately misleading symbol must never change the outcome - identity is the
        // address alone.
        TokenAllowlist misleadinglyLabeled = TokenAllowlist.create("ETHEREUM", "0xusdc", "USDT", 6, 1,
                "sig", CREATED_AT);
        when(repository.findCurrentVersionEntry("ETHEREUM", "0xusdc")).thenReturn(Optional.of(misleadinglyLabeled));

        Optional<TokenAllowlist> result = validator.validate("ETHEREUM", "0xusdc");

        assertThat(result).isPresent();
        assertThat(result.get().contractAddress()).isEqualTo("0xusdc");
        assertThat(result.get().symbol()).isEqualTo("USDT"); // display-only, not what identified it
    }

    @Test
    void validateHasNoOverloadAcceptingASymbolParameter() {
        boolean anyOverloadTakesAString3rdParam = Arrays.stream(TokenValidator.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("validate"))
                .anyMatch(m -> m.getParameterCount() > 2);

        assertThat(anyOverloadTakesAString3rdParam).isFalse();
    }

    @Test
    void shouldSurfaceUnknownTokenForNonAllowlistedContract() {
        when(repository.findCurrentVersionEntry("ETHEREUM", "0xdeadbeef")).thenReturn(Optional.empty());

        Optional<TokenAllowlist> result = validator.validate("ETHEREUM", "0xdeadbeef");

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheAllowlistHasNoRowsAtAllForThatChain() {
        when(repository.findCurrentVersionEntry(any(), any())).thenReturn(Optional.empty());

        Optional<TokenAllowlist> result = validator.validate("TRON", "TFakeAddress");

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsNullChainOrContractAddress() {
        assertThatThrownBy(() -> validator.validate(null, "0xabc"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("chain");
        assertThatThrownBy(() -> validator.validate("ETHEREUM", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("contractAddress");
    }

    @Test
    void throwsForAnUnrecognizedChain() {
        assertThatThrownBy(() -> validator.validate("SOLANA", "0xabc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SOLANA");
        assertThatThrownBy(() -> validator.validate("ethereum", "0xabc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void logsAWarnLineOnUnknownToken() {
        when(repository.findCurrentVersionEntry("ETHEREUM", "0xdeadbeef")).thenReturn(Optional.empty());

        Logger logger = (Logger) LoggerFactory.getLogger(TokenValidator.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            validator.validate("ETHEREUM", "0xdeadbeef");

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.get(0);
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains("ETHEREUM")
                    .contains("0xdeadbeef")
                    .contains("UNKNOWN_TOKEN");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void doesNotLogWhenTheTokenIsFound() {
        when(repository.findCurrentVersionEntry("ETHEREUM", "0xabc"))
                .thenReturn(Optional.of(TokenAllowlist.create("ETHEREUM", "0xabc", "USDT", 6, 1, "sig", CREATED_AT)));

        Logger logger = (Logger) LoggerFactory.getLogger(TokenValidator.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            validator.validate("ETHEREUM", "0xabc");

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }
}
