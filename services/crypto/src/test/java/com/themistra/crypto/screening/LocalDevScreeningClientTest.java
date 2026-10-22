package com.themistra.crypto.screening;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** infra/local (2026-10-21): the local-dev screening stub and how it is selected. */
class LocalDevScreeningClientTest {

    private static final String BLOCKED = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC);

    private final ScreeningResultRepository repository = mock(ScreeningResultRepository.class);

    private static MockEnvironment profile(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private LocalDevScreeningClient client() {
        return new LocalDevScreeningClient(repository, CLOCK, profile("local"), List.of(BLOCKED, " "));
    }

    @Test
    void clearsAnAddressThatIsNotBlockedAndRecordsItUnderItsOwnProviderName() {
        assertThat(client().screen("ETHEREUM", "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", "0xtx"))
                .isEqualTo(ScreeningOutcome.CLEARED);

        ArgumentCaptor<ScreeningResult> saved = ArgumentCaptor.forClass(ScreeningResult.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().provider()).isEqualTo(LocalDevScreeningClient.PROVIDER_NAME)
                .isNotEqualTo(FailClosedScreeningClient.PROVIDER_NAME);
        assertThat(saved.getValue().outcome()).isEqualTo(ScreeningOutcome.CLEARED);
    }

    @Test
    void blocksAListedAddressWhateverItsCase() {
        assertThat(client().screen("ETHEREUM", BLOCKED.toLowerCase(), "0xtx")).isEqualTo(ScreeningOutcome.BLOCKED);
        assertThat(client().screen("ETHEREUM", BLOCKED, "0xtx")).isEqualTo(ScreeningOutcome.BLOCKED);
    }

    @Test
    void refusesToStartOutsideTheLocalProfile() {
        assertThatThrownBy(() -> new LocalDevScreeningClient(repository, CLOCK, profile("prod"), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local development only");
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ScreeningResultRepository.class, () -> repository)
            .withBean(Clock.class, () -> CLOCK)
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
            .withUserConfiguration(FailClosedScreeningClient.class, LocalDevScreeningClient.class);

    @Test
    void failClosedIsTheOnlyScreeningClientUnlessLocalDevIsChosen() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ScreeningClient.class);
            assertThat(context).hasSingleBean(FailClosedScreeningClient.class);
        });
        contextRunner.withPropertyValues("themistra.crypto.screening.mode=fail-closed").run(context ->
                assertThat(context).hasSingleBean(FailClosedScreeningClient.class));
        contextRunner.withPropertyValues("themistra.crypto.screening.mode=local-dev").run(context -> {
            assertThat(context).hasSingleBean(ScreeningClient.class);
            assertThat(context).hasSingleBean(LocalDevScreeningClient.class);
        });
    }
}
