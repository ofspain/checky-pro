package com.themistra.crypto.adapter.eth;

import com.themistra.crypto.common.config.ProviderProperties;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Builds one {@link EthereumAdapter} per configured Ethereum {@code ProviderProperties} entry —
 * matches {@code ChainAdapter}'s own "each provider is one instance of this interface" framing.
 */
@Configuration
public class EthereumAdapterConfig {

    @Bean
    public List<EthereumAdapter> ethereumAdapters(
            ProviderProperties providerProperties,
            Environment environment,
            @Value("${themistra.crypto.adapter.ethereum.poll-interval-ms}") long pollIntervalMs) {
        return providerProperties.chains().stream()
                .filter(chainProviders -> "ETHEREUM".equals(chainProviders.chain()))
                .flatMap(chainProviders -> chainProviders.providers().stream())
                .map(entry -> buildAdapter(entry, environment, pollIntervalMs))
                .toList();
    }

    private EthereumAdapter buildAdapter(
            ProviderProperties.ProviderEntry entry, Environment environment, long pollIntervalMs) {
        String resolvedUrl = resolveUrl(entry, environment);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(entry.timeoutSeconds()))
                .readTimeout(Duration.ofSeconds(entry.timeoutSeconds()))
                .build();
        Web3j web3j = Web3j.build(new HttpService(resolvedUrl, httpClient));

        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

        return new EthereumAdapter(web3j, entry.name(), scheduler, Duration.ofMillis(pollIntervalMs));
    }

    /**
     * Non-throwing by design: substitutes the resolved credential into a {@code {apiKey}} placeholder
     * in {@code entry.url()} when both the placeholder is present and the environment value resolves;
     * otherwise returns the URL unchanged. {@code local} profile's own fixture URLs contain no
     * placeholder at all, so this never fails at wiring time regardless of whether a credential
     * resolves — {@code Web3j}/{@code HttpService} construction is itself lazy (no network call until
     * a method is invoked), so an unreachable/fake URL is harmless until something actually calls it.
     */
    private String resolveUrl(ProviderProperties.ProviderEntry entry, Environment environment) {
        String url = entry.url();
        if (!url.contains("{apiKey}")) {
            return url;
        }
        String apiKey = environment.getProperty(entry.apiKeySecretName());
        return apiKey == null ? url : url.replace("{apiKey}", apiKey);
    }
}
