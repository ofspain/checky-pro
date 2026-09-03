package com.themistra.crypto.adapter.tron;

import com.themistra.crypto.common.config.ProviderProperties;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.ApiWrapperBuilder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Required Tests (frozen brief / Phase 5 plan) AC5/AC11, plus the wiring-count sanity checks
 * mirroring {@code EthereumAdapterConfigTest}'s precedent.
 *
 * <p><b>Deliberate deviation from {@code EthereumAdapterConfigTest}'s structure.</b> That test uses a
 * full {@code ApplicationContextRunner} because {@code EthereumAdapter}'s underlying {@code HttpService}
 * is cheap and safe to construct for real (lazy, no I/O). {@code ApiWrapperBuilder.build()} eagerly
 * constructs a real gRPC {@code ManagedChannel} (confirmed via bytecode inspection, Phase 7/8), and
 * {@code ApiWrapper} itself exposes no reflectable field for its applied timeout the way
 * {@code HttpService}'s backing {@code OkHttpClient} did. These tests instead intercept
 * {@code ApiWrapperBuilder}'s construction directly via Mockito's {@code mockConstruction} (no real
 * channel is ever built) and call {@link TronAdapterConfig#tronAdapters} /
 * {@link TronAdapterConfig#shutdown} directly rather than through a Spring context - the same logic
 * a real context would exercise via {@code @Bean}/{@code @PreDestroy}, verified more directly.
 */
class TronAdapterConfigTest {

    private static ProviderProperties.ProviderEntry entry(String name, String url, int timeoutSeconds,
                                                            String apiKeySecretName) {
        return new ProviderProperties.ProviderEntry(name, url, timeoutSeconds, apiKeySecretName);
    }

    private static ProviderProperties providerPropertiesOf(String chain,
                                                             ProviderProperties.ProviderEntry... entries) {
        return new ProviderProperties(
                List.of(new ProviderProperties.ChainProviders(chain, List.of(entries))), 1);
    }

    @Test
    void credentialReachesApiWrapperBuilderWithApiKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("TRON_KEY", "super-secret-123");
        ProviderProperties properties = providerPropertiesOf("TRON",
                entry("fake-tron-a", "localhost:9903", 5, "TRON_KEY"));

        try (MockedConstruction<ApiWrapperBuilder> mocked = mockBuilderConstruction()) {
            new TronAdapterConfig().tronAdapters(properties, environment, 3000L);

            ApiWrapperBuilder builder = mocked.constructed().get(0);
            verify(builder).withApiKey("super-secret-123");
        }
    }

    @Test
    void credentialIsSkippedWhenTheResolvedValueIsNullOrBlank() {
        // Amendment #9: keeps the local-profile fixture (whose apiKeySecretName names an environment
        // variable that isn't actually set locally) working - no {apiKey} placeholder to gate on here,
        // unlike EthereumAdapterConfig's URL-templating mechanism.
        MockEnvironment environment = new MockEnvironment();
        ProviderProperties properties = providerPropertiesOf("TRON",
                entry("fake-tron-a", "localhost:9903", 5, "UNSET_SECRET"));

        try (MockedConstruction<ApiWrapperBuilder> mocked = mockBuilderConstruction()) {
            new TronAdapterConfig().tronAdapters(properties, environment, 3000L);

            ApiWrapperBuilder builder = mocked.constructed().get(0);
            verify(builder, never()).withApiKey(any());
        }
    }

    @Test
    void timeoutSecondsReachesApiWrapperBuilderWithTimeout() {
        // Amendment #8: confirmed via bytecode (TimeoutInterceptor uses TimeUnit.MILLISECONDS) that
        // withTimeout expects milliseconds, not seconds.
        MockEnvironment environment = new MockEnvironment();
        ProviderProperties properties = providerPropertiesOf("TRON",
                entry("fake-tron-a", "localhost:9903", 7, "UNSET_SECRET"));

        try (MockedConstruction<ApiWrapperBuilder> mocked = mockBuilderConstruction()) {
            new TronAdapterConfig().tronAdapters(properties, environment, 3000L);

            ApiWrapperBuilder builder = mocked.constructed().get(0);
            verify(builder).withTimeout(TimeUnit.SECONDS.toMillis(7));
        }
    }

    @Test
    void grpcEndpointSolidityReceivesTheSameUrlAsGrpcEndpoint() {
        // Amendment #12 (still an open question for a real deployment, Phase 4/5): the provisional
        // plan is one url serving both parameters.
        MockEnvironment environment = new MockEnvironment();
        ProviderProperties properties = providerPropertiesOf("TRON",
                entry("fake-tron-a", "localhost:9903", 5, "UNSET_SECRET"));

        try (MockedConstruction<ApiWrapperBuilder> mocked = mockBuilderConstruction()) {
            new TronAdapterConfig().tronAdapters(properties, environment, 3000L);

            ApiWrapperBuilder builder = mocked.constructed().get(0);
            verify(builder).withGrpcEndpointSolidity("localhost:9903");
        }
    }

    @Test
    void buildsOneAdapterPerConfiguredTronProviderEntry() {
        // Mirrors the real local-profile fixture shape: two TRON providers, one ETHEREUM provider
        // that must be filtered out entirely (this config only ever builds Tron adapters).
        MockEnvironment environment = new MockEnvironment();
        ProviderProperties properties = new ProviderProperties(List.of(
                new ProviderProperties.ChainProviders("TRON", List.of(
                        entry("fake-tron-a", "localhost:9903", 5, "UNSET_A"),
                        entry("fake-tron-b", "localhost:9904", 5, "UNSET_B"))),
                new ProviderProperties.ChainProviders("ETHEREUM", List.of(
                        entry("fake-eth-a", "http://localhost:9901/fake-eth-a", 5, "UNSET_C")))),
                1);

        try (MockedConstruction<ApiWrapperBuilder> mocked = mockBuilderConstruction()) {
            List<TronAdapter> adapters = new TronAdapterConfig().tronAdapters(properties, environment, 3000L);

            assertThat(adapters).hasSize(2);
            assertThat(adapters).allSatisfy(adapter -> assertThat(adapter.chain().name()).isEqualTo("TRON"));
            assertThat(mocked.constructed()).hasSize(2);
        }
    }

    @Test
    void buildsNoAdaptersWhenNoTronChainIsConfigured() {
        MockEnvironment environment = new MockEnvironment();
        ProviderProperties properties = providerPropertiesOf("ETHEREUM",
                entry("fake-eth-a", "http://localhost:9901/fake-eth-a", 5, "UNSET_A"));

        try (MockedConstruction<ApiWrapperBuilder> mocked = mockBuilderConstruction()) {
            List<TronAdapter> adapters = new TronAdapterConfig().tronAdapters(properties, environment, 3000L);

            assertThat(adapters).isEmpty();
        }
    }

    @Test
    void shutdownClosesEveryBuiltAdapterAndItsScheduler() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        ProviderProperties properties = providerPropertiesOf("TRON",
                entry("fake-tron-a", "localhost:9903", 5, "UNSET_A"),
                entry("fake-tron-b", "localhost:9904", 5, "UNSET_B"));

        TronAdapterConfig config = new TronAdapterConfig();
        try (MockedConstruction<ApiWrapperBuilder> mocked = mockBuilderConstruction()) {
            List<TronAdapter> adapters = config.tronAdapters(properties, environment, 3000L);

            ScheduledExecutorService schedulerA = schedulerOf(adapters.get(0));
            ScheduledExecutorService schedulerB = schedulerOf(adapters.get(1));
            assertThat(schedulerA.isShutdown()).isFalse();
            assertThat(schedulerB.isShutdown()).isFalse();

            config.shutdown();

            assertThat(schedulerA.isShutdown()).isTrue();
            assertThat(schedulerB.isShutdown()).isTrue();
        }
    }

    @Configuration
    @EnableConfigurationProperties(ProviderProperties.class)
    static class TestConfig {
    }

    @Test
    void preDestroyIsHonoredWhenTheSpringContextCloses() {
        // Phase 11 Gap 11: proves the @PreDestroy annotation itself is wired, not just that
        // TronAdapterConfig.shutdown()'s own logic works (shutdownClosesEveryBuiltAdapterAndItsScheduler
        // above calls it directly). Confirmed via a spike that Mockito's mockConstruction interception
        // does span a synchronous ApplicationContextRunner.run() call (Spring's context refresh runs on
        // the calling thread), resolving the uncertainty the class Javadoc's original reasoning left
        // open - this is a real context test, not a documented manual-verification gap.
        try (MockedConstruction<ApiWrapperBuilder> mocked = mockBuilderConstruction()) {
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class, TronAdapterConfig.class)
                    .withPropertyValues(
                            "themistra.crypto.adapter.tron.poll-interval-ms=3000",
                            "themistra.crypto.providers.chains[0].chain=TRON",
                            "themistra.crypto.providers.chains[0].providers[0].name=fake-tron-a",
                            "themistra.crypto.providers.chains[0].providers[0].url=localhost:9903",
                            "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=5",
                            "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=UNSET",
                            "themistra.crypto.providers.quorum-threshold=1")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        @SuppressWarnings("unchecked")
                        List<TronAdapter> adapters = context.getBean("tronAdapters", List.class);
                        assertThat(adapters).hasSize(1);
                        ScheduledExecutorService scheduler = schedulerOf(adapters.get(0));
                        assertThat(scheduler.isShutdown()).isFalse();

                        context.close();

                        assertThat(scheduler.isShutdown()).isTrue();
                    });
        }
    }

    private static MockedConstruction<ApiWrapperBuilder> mockBuilderConstruction() {
        return mockConstruction(ApiWrapperBuilder.class, (builder, context) -> {
            when(builder.withGrpcEndpointSolidity(any())).thenReturn(builder);
            when(builder.withTimeout(anyLong())).thenReturn(builder);
            when(builder.withApiKey(any())).thenReturn(builder);
            when(builder.build()).thenReturn(mock(ApiWrapper.class));
        });
    }

    private static ScheduledExecutorService schedulerOf(TronAdapter adapter) throws Exception {
        Field schedulerField = TronAdapter.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        return (ScheduledExecutorService) schedulerField.get(adapter);
    }
}
