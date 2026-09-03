package com.themistra.crypto.adapter.eth;

import com.themistra.crypto.common.config.ProviderProperties;
import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Builds one {@link EthereumAdapter} per configured Ethereum {@code ProviderProperties} entry —
 * matches {@code ChainAdapter}'s own "each provider is one instance of this interface" framing.
 */
@Configuration
public class EthereumAdapterConfig {

    /** Every adapter this config has ever built, so {@link #shutdown()} can close them all on
     * context shutdown (Phase 9 Finding: nothing was closing the Web3j client or scheduler before). */
    private final List<EthereumAdapter> createdAdapters = new CopyOnWriteArrayList<>();

    @Bean
    public List<EthereumAdapter> ethereumAdapters(
            ProviderProperties providerProperties,
            Environment environment,
            @Value("${themistra.crypto.adapter.ethereum.poll-interval-ms}") long pollIntervalMs) {
        List<EthereumAdapter> adapters = providerProperties.chains().stream()
                .filter(chainProviders -> "ETHEREUM".equals(chainProviders.chain()))
                .flatMap(chainProviders -> chainProviders.providers().stream())
                .map(entry -> buildAdapter(entry, environment, pollIntervalMs))
                .toList();
        createdAdapters.addAll(adapters);
        return adapters;
    }

    @PreDestroy
    public void shutdown() {
        createdAdapters.forEach(EthereumAdapter::close);
    }

    private EthereumAdapter buildAdapter(
            ProviderProperties.ProviderEntry entry, Environment environment, long pollIntervalMs) {
        String resolvedUrl = resolveUrl(entry, environment);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(entry.timeoutSeconds()))
                .readTimeout(Duration.ofSeconds(entry.timeoutSeconds()))
                .writeTimeout(Duration.ofSeconds(entry.timeoutSeconds()))
                .callTimeout(Duration.ofSeconds(entry.timeoutSeconds()))
                .build();
        Web3j web3j = Web3j.build(new HttpService(resolvedUrl, httpClient));

        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

        return new EthereumAdapter(web3j, entry.name(), scheduler, Duration.ofMillis(pollIntervalMs));
    }

    /**
     * Substitutes the resolved credential into a {@code {apiKey}} placeholder in {@code entry.url()}.
     * Two cases are handled differently on purpose (Phase 9 Finding 5 refined this):
     * <ul>
     *   <li>No {@code {apiKey}} placeholder at all → the URL is returned unchanged, no lookup
     *       attempted. This is what keeps {@code local} profile's own fixture URLs (which have no
     *       placeholder) safe — {@code Web3j}/{@code HttpService} construction is itself lazy, so an
     *       unreachable/fake URL is harmless until something actually calls it.</li>
     *   <li>A placeholder is present but the environment value does not resolve → **fails fast here**,
     *       at wiring time, rather than silently booting with a broken URL that would only surface as
     *       a confusing network/DNS error on the first real RPC call. This is the one case L13's
     *       "fail startup on missing/invalid config" principle actually applies to for this value —
     *       a missing credential for a URL that structurally requires one.</li>
     * </ul>
     *
     * <p><b>No URL-encoding of the substituted value (Phase 11 Gap 9).</b> The resolved credential is
     * substituted with a literal {@link String#replace}, not percent-encoded. If a real provider ever
     * issues a key containing characters that are not URL-safe in whatever position the
     * {@code {apiKey}} placeholder occupies (path segment vs. query value have different reserved-
     * character sets), the resulting URL could be malformed or misinterpreted. Left unencoded
     * deliberately for now rather than guessing at an encoding scheme — the actual provider(s) this
     * fixture will point at in production are still unresolved (package.md §11 Q1), and a wrong
     * assumption here (e.g. blindly applying {@code URLEncoder}, which is form/query-encoding, not
     * safe for a path segment) could break a real key that today needs no encoding at all. Revisit
     * once Q1 is resolved and the real key format is known.</p>
     */
    private String resolveUrl(ProviderProperties.ProviderEntry entry, Environment environment) {
        String url = entry.url();
        if (!url.contains("{apiKey}")) {
            return url;
        }
        String apiKey = environment.getProperty(entry.apiKeySecretName());
        if (apiKey == null) {
            throw new IllegalStateException(
                    "Provider '" + entry.name() + "'s url contains {apiKey} but no value resolved for "
                            + "apiKeySecretName '" + entry.apiKeySecretName() + "' - set that "
                            + "environment variable/property before this service can reach a real "
                            + "Ethereum RPC endpoint.");
        }
        return url.replace("{apiKey}", apiKey);
    }
}
