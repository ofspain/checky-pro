package com.themistra.crypto.adapter.eth;

import com.themistra.crypto.common.config.ProviderProperties;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Required Tests (frozen brief / Phase 5 plan) AC4/AC11, and the wiring-count sanity check.
 * {@code leavesPlaceholderUnsubstitutedWhenTheEnvironmentValueIsAbsent} from the original plan is
 * superseded here by {@link #throwsWhenPlaceholderIsPresentButTheEnvironmentValueIsAbsent()} — Phase
 * 9 Resolution item 5 changed that case from silent to fail-fast; see
 * {@code 09-review-resolution.md}.
 */
class EthereumAdapterConfigTest {

    @Configuration
    @EnableConfigurationProperties(ProviderProperties.class)
    static class TestConfig {
    }

    private static final String[] POLL_INTERVAL = {
            "themistra.crypto.adapter.ethereum.poll-interval-ms=15000"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, EthereumAdapterConfig.class)
            .withPropertyValues(POLL_INTERVAL);

    // ---------- reflection helpers: EthereumAdapter/Web3j/HttpService expose no public accessor for
    // the resolved URL or HTTP timeouts, so these Required Tests reach in the same way this session's
    // established javap-verification approach already trusts the real library's internal shape. ----

    private static HttpService httpServiceOf(EthereumAdapter adapter) throws Exception {
        Field web3jField = EthereumAdapter.class.getDeclaredField("web3j");
        web3jField.setAccessible(true);
        Web3j web3j = (Web3j) web3jField.get(adapter);
        Field serviceField = web3j.getClass().getDeclaredField("web3jService");
        serviceField.setAccessible(true);
        return (HttpService) serviceField.get(web3j);
    }

    private static OkHttpClient httpClientOf(HttpService httpService) throws Exception {
        Field clientField = HttpService.class.getDeclaredField("httpClient");
        clientField.setAccessible(true);
        return (OkHttpClient) clientField.get(httpService);
    }

    @Test
    void substitutesResolvedCredentialIntoAUrlContainingThePlaceholder() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.providers.chains[0].chain=ETHEREUM",
                        "themistra.crypto.providers.chains[0].providers[0].name=alchemy",
                        "themistra.crypto.providers.chains[0].providers[0].url=http://rpc.example.com/{apiKey}",
                        "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=5",
                        "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=ALCHEMY_KEY",
                        "themistra.crypto.providers.quorum-threshold=1",
                        "ALCHEMY_KEY=super-secret-123")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    List<EthereumAdapter> adapters = context.getBean("ethereumAdapters", List.class);
                    assertThat(adapters).hasSize(1);
                    assertThat(httpServiceOf(adapters.get(0)).getUrl())
                            .isEqualTo("http://rpc.example.com/super-secret-123");
                });
    }

    @Test
    void leavesUrlUnchangedWhenNoPlaceholderIsPresent() {
        // Mirrors the local profile's own T03 fixture: a plain URL, no {apiKey} token, no matching
        // environment value set anywhere - must not fail or attempt any substitution.
        contextRunner.withPropertyValues(
                        "themistra.crypto.providers.chains[0].chain=ETHEREUM",
                        "themistra.crypto.providers.chains[0].providers[0].name=fake-eth-a",
                        "themistra.crypto.providers.chains[0].providers[0].url=http://localhost:9901/fake-eth-a",
                        "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=5",
                        "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=UNSET_SECRET",
                        "themistra.crypto.providers.quorum-threshold=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    List<EthereumAdapter> adapters = context.getBean("ethereumAdapters", List.class);
                    assertThat(httpServiceOf(adapters.get(0)).getUrl())
                            .isEqualTo("http://localhost:9901/fake-eth-a");
                });
    }

    @Test
    void throwsWhenPlaceholderIsPresentButTheEnvironmentValueIsAbsent() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.providers.chains[0].chain=ETHEREUM",
                        "themistra.crypto.providers.chains[0].providers[0].name=alchemy",
                        "themistra.crypto.providers.chains[0].providers[0].url=http://rpc.example.com/{apiKey}",
                        "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=5",
                        "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=MISSING_KEY",
                        "themistra.crypto.providers.quorum-threshold=1")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("alchemy")
                        .hasMessageContaining("MISSING_KEY"));
    }

    @Test
    void configuresHttpClientTimeoutFromProviderEntryTimeoutSeconds() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.providers.chains[0].chain=ETHEREUM",
                        "themistra.crypto.providers.chains[0].providers[0].name=fake-eth-a",
                        "themistra.crypto.providers.chains[0].providers[0].url=http://localhost:9901/fake-eth-a",
                        "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=7",
                        "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=UNSET_SECRET",
                        "themistra.crypto.providers.quorum-threshold=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    List<EthereumAdapter> adapters = context.getBean("ethereumAdapters", List.class);
                    OkHttpClient client = httpClientOf(httpServiceOf(adapters.get(0)));
                    long expectedMillis = TimeUnit.SECONDS.toMillis(7);
                    assertThat(client.connectTimeoutMillis()).isEqualTo(expectedMillis);
                    assertThat(client.readTimeoutMillis()).isEqualTo(expectedMillis);
                    assertThat(client.writeTimeoutMillis()).isEqualTo(expectedMillis);
                    assertThat(client.callTimeoutMillis()).isEqualTo(expectedMillis);
                });
    }

    @Test
    void buildsOneAdapterPerConfiguredEthereumProviderEntry() {
        // Mirrors the real local-profile T03 fixture shape: two ETHEREUM providers, one TRON provider
        // that must be filtered out entirely (this config only ever builds Ethereum adapters).
        contextRunner.withPropertyValues(
                        "themistra.crypto.providers.chains[0].chain=ETHEREUM",
                        "themistra.crypto.providers.chains[0].providers[0].name=fake-eth-a",
                        "themistra.crypto.providers.chains[0].providers[0].url=http://localhost:9901/fake-eth-a",
                        "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=5",
                        "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=UNSET_A",
                        "themistra.crypto.providers.chains[0].providers[1].name=fake-eth-b",
                        "themistra.crypto.providers.chains[0].providers[1].url=http://localhost:9902/fake-eth-b",
                        "themistra.crypto.providers.chains[0].providers[1].timeout-seconds=5",
                        "themistra.crypto.providers.chains[0].providers[1].api-key-secret-name=UNSET_B",
                        "themistra.crypto.providers.chains[1].chain=TRON",
                        "themistra.crypto.providers.chains[1].providers[0].name=fake-tron-a",
                        "themistra.crypto.providers.chains[1].providers[0].url=http://localhost:9903/fake-tron-a",
                        "themistra.crypto.providers.chains[1].providers[0].timeout-seconds=5",
                        "themistra.crypto.providers.chains[1].providers[0].api-key-secret-name=UNSET_C",
                        "themistra.crypto.providers.quorum-threshold=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    List<EthereumAdapter> adapters = context.getBean("ethereumAdapters", List.class);
                    assertThat(adapters).hasSize(2);
                    assertThat(adapters).allSatisfy(adapter ->
                            assertThat(adapter.chain().name()).isEqualTo("ETHEREUM"));
                });
    }
}
