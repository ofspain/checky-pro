package com.themistra.crypto.adapter.tron;

import com.themistra.crypto.common.config.ProviderProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.ApiWrapperBuilder;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Builds one {@link TronAdapter} per configured Tron {@code ProviderProperties} entry — mirrors
 * {@link com.themistra.crypto.adapter.eth.EthereumAdapterConfig}'s exact precedent.
 */
@Configuration
public class TronAdapterConfig {

    private final List<TronAdapter> createdAdapters = new CopyOnWriteArrayList<>();

    @Bean
    public List<TronAdapter> tronAdapters(
            ProviderProperties providerProperties,
            Environment environment,
            @Value("${themistra.crypto.adapter.tron.poll-interval-ms}") long pollIntervalMs) {
        List<TronAdapter> adapters = providerProperties.chains().stream()
                .filter(chainProviders -> "TRON".equals(chainProviders.chain()))
                .flatMap(chainProviders -> chainProviders.providers().stream())
                .map(entry -> buildAdapter(entry, environment, pollIntervalMs))
                .toList();
        createdAdapters.addAll(adapters);
        return adapters;
    }

    @PreDestroy
    public void shutdown() {
        createdAdapters.forEach(TronAdapter::close);
    }

    private TronAdapter buildAdapter(
            ProviderProperties.ProviderEntry entry, Environment environment, long pollIntervalMs) {
        // Amendment #12: ProviderEntry has one url field but ApiWrapperBuilder wants a separate
        // solidity-node endpoint - the provisional plan (still open for a real deployment) is to
        // point both at the same address. The 1-arg constructor + withGrpcEndpointSolidity avoids
        // needing a private key at all (this adapter is read-only, never signs anything - L11).
        ApiWrapperBuilder builder = new ApiWrapperBuilder(entry.url())
                .withGrpcEndpointSolidity(entry.url())
                .withTimeout(Duration.ofSeconds(entry.timeoutSeconds()).toMillis());

        String apiKey = environment.getProperty(entry.apiKeySecretName());
        if (apiKey != null && !apiKey.isBlank()) {
            // Amendment #9: skip attaching a credential entirely when unresolved, rather than
            // passing null/blank through to trident - keeps the local-profile fixture (whose
            // apiKeySecretName names an environment variable that isn't actually set locally)
            // working, the Tron-shaped equivalent of EthereumAdapterConfig's placeholder-presence
            // check (which doesn't apply here, since a gRPC target has no {apiKey} token to gate on).
            builder = builder.withApiKey(apiKey);
        }

        ApiWrapper apiWrapper = builder.build();

        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

        return new TronAdapter(apiWrapper, entry.name(), scheduler, Duration.ofMillis(pollIntervalMs));
    }
}
