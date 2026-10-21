package com.themistra.payment.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.themistra.payment.common.crypto.AttestRequest;
import com.themistra.payment.common.crypto.AttestResponse;
import com.themistra.payment.common.crypto.RegisterWatchRequest;
import com.themistra.payment.common.crypto.RegisterWatchResponse;
import com.themistra.payment.consumer.dto.ChainTxConfirmed;
import com.themistra.payment.consumer.dto.ChainTxFinalized;
import com.themistra.payment.consumer.dto.ChainTxReorged;
import com.themistra.payment.consumer.dto.ChainTxSeen;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Open-issues sweep, 2026-10-21: closes the "no test boots crypto and payment together" gap by
 * contract. No test runs both services at once; instead both sides are pinned to the same files.
 * crypto-service checks the payloads it emits against {@code contracts/events/chain/*.schema.json}
 * ({@code *PayloadContractTest}) and its handlers against {@code contracts/api/crypto-internal.yaml}
 * ({@code CryptoInternalOpenApiContractTest}). This test checks that every type payment-service
 * sends to or reads from crypto-service has exactly the contract's fields, so a rename or a new
 * field on either side fails one of the two suites.
 *
 * <p>The behaviour across the boundary is proven in halves: crypto's
 * {@code EndToEndIntegrationTest} (unregister at SEEN still emits finalized) and payment's
 * {@code PaymentEndToEndIntegrationTest} (against a crypto stand-in).</p>
 */
class CryptoContractConformanceTest {

    private static final Path CHAIN_EVENTS = Path.of("../../contracts/events/chain");
    private static final Path CRYPTO_INTERNAL = Path.of("../../contracts/api/crypto-internal.yaml");

    static Stream<Arguments> chainEvents() {
        return Stream.of(
                Arguments.of("tx-seen.v1.schema.json", ChainTxSeen.class),
                Arguments.of("tx-confirmed.v1.schema.json", ChainTxConfirmed.class),
                Arguments.of("tx-finalized.v1.schema.json", ChainTxFinalized.class),
                Arguments.of("tx-reorged.v1.schema.json", ChainTxReorged.class));
    }

    static Stream<Arguments> internalApiSchemas() {
        return Stream.of(
                Arguments.of("RegisterWatchRequest", RegisterWatchRequest.class),
                Arguments.of("RegisterWatchResponse", RegisterWatchResponse.class),
                Arguments.of("AttestRequest", AttestRequest.class),
                Arguments.of("AttestResponse", AttestResponse.class));
    }

    @ParameterizedTest(name = "{1} matches {0}")
    @MethodSource("chainEvents")
    void everyConsumedChainEventHasExactlyTheSchemasFields(String schemaFile, Class<?> type) throws IOException {
        JsonNode schema = new ObjectMapper().readTree(CHAIN_EVENTS.resolve(schemaFile).toFile());
        assertFieldsMatch(schema, type);
    }

    @ParameterizedTest(name = "{1} matches {0}")
    @MethodSource("internalApiSchemas")
    void everyInternalApiBodyHasExactlyTheContractsFields(String schemaName, Class<?> type) throws IOException {
        JsonNode schema = new YAMLMapper().readTree(CRYPTO_INTERNAL.toFile())
                .path("components").path("schemas").path(schemaName);
        assertThat(schema.isMissingNode()).as("%s is in crypto-internal.yaml", schemaName).isFalse();
        assertFieldsMatch(schema, type);
    }

    private static void assertFieldsMatch(JsonNode schema, Class<?> type) {
        Set<String> components = new TreeSet<>(Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName).toList());
        Set<String> properties = new TreeSet<>();
        schema.path("properties").fieldNames().forEachRemaining(properties::add);
        Set<String> required = new TreeSet<>();
        schema.path("required").forEach(name -> required.add(name.asText()));

        assertThat(properties).as("the schema declares properties").isNotEmpty();
        assertThat(components).as("%s fields", type.getSimpleName()).isEqualTo(properties);
        assertThat(components).as("%s carries every required field", type.getSimpleName()).containsAll(required);
    }
}
