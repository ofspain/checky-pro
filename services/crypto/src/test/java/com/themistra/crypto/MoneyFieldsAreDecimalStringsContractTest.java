package com.themistra.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 4 Finding #6: AC4 (money discipline, {@code agents.md}) had no executable enforcement -
 * every existing contract test checks field *names*, never field *types*. This walks every property
 * whose name is a known monetary field, across every contract file this task authors, and asserts it
 * is documented {@code type: string}, never {@code type: number}/{@code type: integer} - centralized
 * here rather than duplicated across 6 near-identical per-file assertions. */
class MoneyFieldsAreDecimalStringsContractTest {

    private static final Set<String> MONETARY_FIELD_NAMES = Set.of("expectedAmount", "amount");

    private static final Path OPENAPI_PATH = Path.of("../../contracts/api/crypto-internal.yaml");
    private static final List<Path> EVENT_SCHEMA_PATHS = List.of(
            Path.of("../../contracts/events/chain/tx-seen.v1.schema.json"),
            Path.of("../../contracts/events/chain/tx-confirmed.v1.schema.json"),
            Path.of("../../contracts/events/chain/tx-finalized.v1.schema.json"),
            Path.of("../../contracts/events/chain/tx-reorged.v1.schema.json"),
            Path.of("../../contracts/events/chain/provider-degraded.v1.schema.json"));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final YAMLMapper yamlMapper = new YAMLMapper();

    @Test
    void everyMonetaryFieldInTheOpenApiContractIsADecimalStringNeverANumber() throws IOException {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(OPENAPI_PATH));
        JsonNode schemas = contractYaml.get("components").get("schemas");

        schemas.fieldNames().forEachRemaining(schemaName -> {
            JsonNode properties = schemas.get(schemaName).get("properties");
            if (properties == null) {
                return;
            }
            for (String monetaryField : MONETARY_FIELD_NAMES) {
                JsonNode field = properties.get(monetaryField);
                if (field == null) {
                    continue;
                }
                assertThat(field.get("type").asText())
                        .as("%s.%s must be a decimal string, never a JSON number", schemaName, monetaryField)
                        .isEqualTo("string");
            }
        });
    }

    @Test
    void everyMonetaryFieldInEveryEventSchemaIsADecimalStringNeverANumber() throws IOException {
        for (Path schemaPath : EVENT_SCHEMA_PATHS) {
            JsonNode schema = objectMapper.readTree(Files.readString(schemaPath));
            JsonNode properties = schema.get("properties");
            for (String monetaryField : MONETARY_FIELD_NAMES) {
                JsonNode field = properties.get(monetaryField);
                if (field == null) {
                    continue;
                }
                assertThat(field.get("type").asText())
                        .as("%s: %s must be a decimal string, never a JSON number", schemaPath, monetaryField)
                        .isEqualTo("string");
            }
        }
    }
}
