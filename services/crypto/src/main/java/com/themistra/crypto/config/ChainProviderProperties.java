package com.themistra.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Which chains this service observes, and who serves them (ARCHITECTURE §6.1).
 *
 * <p>Bound from {@code themistra.crypto.chains.*}. Endpoints arrive from the environment and are
 * never written into a committed file (D-010); only the variable reference appears in
 * {@code application.properties}.
 */
@ConfigurationProperties(prefix = "themistra.crypto")
public class ChainProviderProperties {

    /** Declared chains. Empty is valid: a service not yet given work is not misconfigured. */
    private List<Chain> chains = new ArrayList<>();

    public List<Chain> getChains() {
        return chains;
    }

    public void setChains(List<Chain> chains) {
        this.chains = chains == null ? new ArrayList<>() : chains;
    }

    /** One chain and the independent providers serving it. */
    public static class Chain {

        /** Namespaced chain identifier, e.g. {@code eip155:1}. */
        private String id;

        private List<Provider> providers = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<Provider> getProviders() {
            return providers;
        }

        public void setProviders(List<Provider> providers) {
            this.providers = providers == null ? new ArrayList<>() : providers;
        }
    }

    /** One provider's identity and where to reach it. */
    public static class Provider {

        /**
         * Stable, non-sensitive label, e.g. {@code evm-provider-a}. Reaches the observation log
         * and published events, so it must never encode an endpoint or a vendor credential.
         */
        private String label;

        /** JSON-RPC endpoint. Supplied by the environment. */
        private String endpoint;

        /**
         * Optional subscription endpoint for the watcher layer. Unused here: nothing subscribes
         * until the watcher exists, and a subscription is only ever a trigger, never an
         * observation.
         */
        private String subscriptionEndpoint;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getSubscriptionEndpoint() {
            return subscriptionEndpoint;
        }

        public void setSubscriptionEndpoint(String subscriptionEndpoint) {
            this.subscriptionEndpoint = subscriptionEndpoint;
        }
    }
}
