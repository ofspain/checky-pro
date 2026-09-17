package com.themistra.crypto.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.themistra.crypto.attest.AttestController;
import com.themistra.crypto.attest.AttestRequest;
import com.themistra.crypto.attest.AttestResponse;
import com.themistra.crypto.attest.PublicKeyInfo;
import com.themistra.crypto.attest.VerificationKeysController;
import com.themistra.crypto.attest.VerificationKeysResponse;
import com.themistra.crypto.watch.WatchController;
import com.themistra.crypto.watch.dto.RegisterWatchRequest;
import com.themistra.crypto.watch.dto.RegisterWatchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The named test for T23/R28: {@code contracts/api/crypto-internal.yaml} must document every real
 * endpoint (completeness) and every documented response schema must match its real DTO's actual
 * serialized shape (correctness). Scoped to success responses only, mirroring {@code
 * AuthOpenApiContractTest}'s own identical R47-precedent scoping - the {@code 409} refusal path is
 * documented in the YAML for human/codegen value but not verified here. No Spring context, no
 * Testcontainers - pure reflection over the controller classes plus the same plain-Jackson structural
 * comparison technique {@code UserLifecycleEventPayloadContractTest} already established.
 */
class CryptoInternalOpenApiContractTest {

    private static final Path CONTRACT_PATH = Path.of("../../contracts/api/crypto-internal.yaml");

    private static final List<Class<?>> CONTROLLERS = List.of(
            WatchController.class,
            AttestController.class,
            VerificationKeysController.class);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final YAMLMapper yamlMapper = new YAMLMapper();

    private record Route(String method, String path) {
    }

    @Test
    void everyControllerHandlerIsDocumentedInCryptoInternalYaml() throws Exception {
        Set<Route> yamlRoutes = yamlRoutes();
        Set<Route> controllerRoutes = controllerRoutes();

        for (Route route : controllerRoutes) {
            assertThat(yamlRoutes)
                    .as("crypto-internal.yaml should document handler route %s", route)
                    .contains(route);
        }
    }

    @Test
    void cryptoInternalYamlDocumentsNoRouteThatDoesNotHaveARealHandler() throws Exception {
        Set<Route> yamlRoutes = yamlRoutes();
        Set<Route> controllerRoutes = controllerRoutes();

        for (Route route : yamlRoutes) {
            assertThat(controllerRoutes)
                    .as("crypto-internal.yaml route %s should map to a real controller handler", route)
                    .contains(route);
        }
    }

    @Test
    void everyComponentSchemaMatchesItsRealDtoShape() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        JsonNode components = contractYaml.get("components").get("schemas");

        for (Map.Entry<String, Object> entry : realInstancesByComponentName().entrySet()) {
            String instanceKey = entry.getKey();
            // Phase 4 Finding #4: "AttestResponse-signed"/"AttestResponse-blocked" are two distinct
            // real instances checked against the one "AttestResponse" schema - the suffix after '-'
            // disambiguates the map key without needing a separate schema entry per variant.
            String componentName = instanceKey.contains("-") ? instanceKey.substring(0, instanceKey.indexOf('-')) : instanceKey;
            JsonNode componentSchema = components.get(componentName);
            assertThat(componentSchema).as("component schema '%s' exists in crypto-internal.yaml", componentName)
                    .isNotNull();

            JsonNode serialized = objectMapper.valueToTree(entry.getValue());

            JsonNode required = componentSchema.get("required");
            if (required != null) {
                required.forEach(field -> assertThat(serialized.has(field.asText()))
                        .as("%s: required field '%s' present", instanceKey, field.asText())
                        .isTrue());
            }

            JsonNode declaredProperties = componentSchema.get("properties");
            serialized.fieldNames().forEachRemaining(field -> assertThat(declaredProperties.has(field))
                    .as("%s: serialized field '%s' is declared in the schema", instanceKey, field)
                    .isTrue());
        }
    }

    /** Phase 4 Finding #2: {@code DELETE /internal/v1/watches/{watchId}} must declare its path
     * parameter explicitly, mirroring auth.yaml's own {@code {accountUuid}} convention. */
    @Test
    void watchIdPathParameterIsDeclaredOnTheDeleteOperation() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        JsonNode operation = contractYaml.get("paths").get("/internal/v1/watches/{watchId}").get("delete");
        JsonNode parameters = operation.get("parameters");

        assertThat(parameters).as("DELETE /internal/v1/watches/{watchId} must declare its parameters").isNotNull();
        boolean hasWatchIdPathParam = StreamSupport.stream(parameters.spliterator(), false)
                .anyMatch(p -> "watchId".equals(p.get("name").asText()) && "path".equals(p.get("in").asText())
                        && p.get("required").asBoolean()
                        && "string".equals(p.get("schema").get("type").asText())
                        && "uuid".equals(p.get("schema").get("format").asText()));
        assertThat(hasWatchIdPathParam)
                .as("watchId must be declared as a required path parameter with schema type: string, format: uuid")
                .isTrue();
    }

    /** Phase 4 Finding #5, refined at Phase 5: {@code AttestResponse.outcome}'s schema enum must equal
     * exactly the two values this specific DTO can ever actually produce - {@code SIGNED}/{@code
     * BLOCKED} - not the full 3-value {@code AttestOutcome} enum. {@code REFUSED} is structurally
     * impossible here: a refusal is always a {@code 409}, produced by {@code
     * AttestationRefusedException}, never by this record - demanding "REFUSED" appear in this {@code
     * 200}-only response schema would itself be a fidelity violation, not an improvement. */
    @Test
    void attestResponseOutcomeSchemaEnumMatchesTheTwoValuesTheDtoCanActuallyProduce() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        JsonNode outcomeEnum = contractYaml.get("components").get("schemas")
                .get("AttestResponse").get("properties").get("outcome").get("enum");
        var allowedOutcomes = StreamSupport.stream(outcomeEnum.spliterator(), false)
                .map(JsonNode::asText)
                .toList();

        assertThat(allowedOutcomes).containsExactlyInAnyOrder("SIGNED", "BLOCKED");
    }

    /** Phase 11 (Kimi) Gap 4: the three internal routes' {@code security: [bearerAuth:
     * [internal.crypto:write]]} requirement, and the well-known endpoint's {@code security: []},
     * were documented but never contract-tested - a regression dropping either would pass every
     * other test in this class. Not part of the named test's delegation list since AC1 scopes that
     * test to routes/schemas, not security. */
    @Test
    void everyInternalRouteRequiresBearerAuthWithInternalCryptoWriteScope() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));

        assertThat(contractYaml.get("components").get("securitySchemes").get("bearerAuth").get("type").asText())
                .isEqualTo("http");
        assertThat(contractYaml.get("components").get("securitySchemes").get("bearerAuth").get("scheme").asText())
                .isEqualTo("bearer");

        List<Route> internalRoutes = List.of(
                new Route("POST", "/internal/v1/watches"),
                new Route("DELETE", "/internal/v1/watches/{watchId}"),
                new Route("POST", "/internal/v1/attest"));
        for (Route route : internalRoutes) {
            JsonNode operation = contractYaml.get("paths").get(route.path()).get(route.method().toLowerCase(Locale.ROOT));
            JsonNode security = operation.get("security");
            assertThat(security).as("%s declares a security requirement", route).isNotNull();
            JsonNode scopes = security.get(0).get("bearerAuth");
            assertThat(scopes).as("%s requires bearerAuth", route).isNotNull();
            assertThat(StreamSupport.stream(scopes.spliterator(), false).map(JsonNode::asText).toList())
                    .as("%s requires exactly the internal.crypto:write scope", route)
                    .containsExactly("internal.crypto:write");
        }
    }

    @Test
    void publicVerificationKeysEndpointHasNoSecurity() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        JsonNode operation = contractYaml.get("paths").get("/.well-known/themistra-verification-keys").get("get");
        JsonNode security = operation.get("security");
        assertThat(security).as("verification-keys operation declares a security array").isNotNull();
        assertThat(security.isEmpty()).as("verification-keys endpoint requires no auth").isTrue();
    }

    /** Phase 11 (Kimi) Gap 5: {@code RegisterWatchRequest.chain}/{@code AttestRequest.chain} declare
     * {@code enum: [ETHEREUM, TRON]}, but nothing tied that literal list to
     * {@link com.themistra.crypto.adapter.Chain#values()} - a new chain added to the Java enum
     * without a contract update would pass every other test in this class. */
    @Test
    void chainEnumsInRequestSchemasCoverEveryChainValue() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        JsonNode schemas = contractYaml.get("components").get("schemas");
        var chainNames = java.util.Arrays.stream(com.themistra.crypto.adapter.Chain.values())
                .map(Enum::name)
                .toList();

        for (String schemaName : List.of("RegisterWatchRequest", "AttestRequest")) {
            JsonNode chainEnum = schemas.get(schemaName).get("properties").get("chain").get("enum");
            var declaredChains = StreamSupport.stream(chainEnum.spliterator(), false)
                    .map(JsonNode::asText)
                    .toList();
            assertThat(declaredChains)
                    .as("%s.chain enum matches Chain.values() exactly", schemaName)
                    .containsExactlyInAnyOrderElementsOf(chainNames);
        }
    }

    /** Phase 11 (Kimi) Gap 6: {@code everyComponentSchemaMatchesItsRealDtoShape} proves {@code status}
     * is declared and present, not that it's a plain {@code type: string} rather than an enum -
     * matching {@code WatchController}'s deliberate choice ({@code watch.status().name()}, not the
     * internal {@code WatchStatus} enum type itself, per {@code RegisterWatchResponse}'s own Javadoc). */
    @Test
    void registerWatchResponseStatusIsAPlainStringNotAnEnum() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        JsonNode statusSchema = contractYaml.get("components").get("schemas")
                .get("RegisterWatchResponse").get("properties").get("status");
        assertThat(statusSchema.get("type").asText()).isEqualTo("string");
        assertThat(statusSchema.has("enum")).as("status has no enum constraint").isFalse();
    }

    @Test
    void everyOperationResponseReferencesTheExpectedSchema() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        for (Map.Entry<Route, ExpectedSchema> entry : expectedResponseSchemas().entrySet()) {
            assertThat(actualResponseSchema(contractYaml, entry.getKey()))
                    .as("%s response schema", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void everyOperationRequestBodyReferencesTheExpectedSchema() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        for (Map.Entry<Route, ExpectedSchema> entry : expectedRequestSchemas().entrySet()) {
            assertThat(actualRequestSchema(contractYaml, entry.getKey()))
                    .as("%s request schema", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    /** The named test (`package.md` §8): {@code shouldConformToCryptoInternalOpenApiContract}. */
    @Test
    void shouldConformToCryptoInternalOpenApiContract() throws Exception {
        everyControllerHandlerIsDocumentedInCryptoInternalYaml();
        cryptoInternalYamlDocumentsNoRouteThatDoesNotHaveARealHandler();
        everyComponentSchemaMatchesItsRealDtoShape();
        watchIdPathParameterIsDeclaredOnTheDeleteOperation();
        attestResponseOutcomeSchemaEnumMatchesTheTwoValuesTheDtoCanActuallyProduce();
        everyOperationResponseReferencesTheExpectedSchema();
        everyOperationRequestBodyReferencesTheExpectedSchema();
    }

    /** Guards against silently forgetting to update {@link #expectedResponseSchemas()} when a route
     * is added - mirrors {@code AuthOpenApiContractTest}'s identical guard. */
    @Test
    void expectedResponseSchemasCoverEveryControllerRoute() {
        assertThat(expectedResponseSchemas().keySet())
                .as("expectedResponseSchemas() must have one entry per real controller route")
                .isEqualTo(controllerRoutes());
    }

    @Test
    void expectedRequestSchemasCoverEveryRequestBodyHandler() {
        assertThat(expectedRequestSchemas().keySet())
                .as("expectedRequestSchemas() must have one entry per real @RequestBody handler")
                .isEqualTo(routesWithRequestBody());
    }

    private Set<Route> routesWithRequestBody() {
        Set<Route> routes = new LinkedHashSet<>();
        for (Class<?> controller : CONTROLLERS) {
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String basePath = classMapping != null && classMapping.value().length > 0 ? classMapping.value()[0] : "";
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                boolean hasRequestBody = false;
                for (var parameter : method.getParameters()) {
                    if (parameter.isAnnotationPresent(RequestBody.class)) {
                        hasRequestBody = true;
                        break;
                    }
                }
                if (!hasRequestBody) {
                    continue;
                }
                String subPath = mapping.value().length > 0 ? mapping.value()[0] : "";
                String fullPath = basePath + subPath;
                for (var httpMethod : mapping.method()) {
                    routes.add(new Route(httpMethod.name(), fullPath));
                }
            }
        }
        return routes;
    }

    private record ExpectedSchema(String kind, String name) {
        static ExpectedSchema ref(String name) {
            return new ExpectedSchema("ref", name);
        }

        static ExpectedSchema none() {
            return new ExpectedSchema("none", null);
        }
    }

    private ExpectedSchema actualResponseSchema(JsonNode contractYaml, Route route) {
        JsonNode operation = contractYaml.get("paths").get(route.path()).get(route.method().toLowerCase(Locale.ROOT));
        JsonNode responses = operation.get("responses");
        Integer lowest = null;
        var statusCodes = responses.fieldNames();
        while (statusCodes.hasNext()) {
            String status = statusCodes.next();
            if (status.startsWith("2")) {
                int code = Integer.parseInt(status);
                if (lowest == null || code < lowest) {
                    lowest = code;
                }
            }
        }
        if (lowest == null) {
            return ExpectedSchema.none();
        }
        JsonNode content = responses.get(String.valueOf(lowest)).get("content");
        if (content == null) {
            return ExpectedSchema.none();
        }
        return parseSchemaNode(content.get("application/json").get("schema"));
    }

    private ExpectedSchema actualRequestSchema(JsonNode contractYaml, Route route) {
        JsonNode operation = contractYaml.get("paths").get(route.path()).get(route.method().toLowerCase(Locale.ROOT));
        JsonNode requestBody = operation.get("requestBody");
        if (requestBody == null) {
            return ExpectedSchema.none();
        }
        return parseSchemaNode(requestBody.get("content").get("application/json").get("schema"));
    }

    private ExpectedSchema parseSchemaNode(JsonNode schema) {
        if (schema.has("$ref")) {
            return ExpectedSchema.ref(refName(schema.get("$ref").asText()));
        }
        throw new IllegalStateException("Unrecognized schema node shape: " + schema);
    }

    private String refName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    private Map<Route, ExpectedSchema> expectedResponseSchemas() {
        Map<Route, ExpectedSchema> m = new LinkedHashMap<>();
        m.put(new Route("POST", "/internal/v1/watches"), ExpectedSchema.ref("RegisterWatchResponse"));
        m.put(new Route("DELETE", "/internal/v1/watches/{watchId}"), ExpectedSchema.none());
        m.put(new Route("POST", "/internal/v1/attest"), ExpectedSchema.ref("AttestResponse"));
        m.put(new Route("GET", "/.well-known/themistra-verification-keys"), ExpectedSchema.ref("VerificationKeysResponse"));
        return m;
    }

    private Map<Route, ExpectedSchema> expectedRequestSchemas() {
        Map<Route, ExpectedSchema> m = new LinkedHashMap<>();
        m.put(new Route("POST", "/internal/v1/watches"), ExpectedSchema.ref("RegisterWatchRequest"));
        m.put(new Route("POST", "/internal/v1/attest"), ExpectedSchema.ref("AttestRequest"));
        return m;
    }

    private Set<Route> yamlRoutes() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        Set<Route> routes = new LinkedHashSet<>();
        JsonNode paths = contractYaml.get("paths");
        paths.fieldNames().forEachRemaining(path -> {
            JsonNode operations = paths.get(path);
            operations.fieldNames().forEachRemaining(
                    httpMethod -> routes.add(new Route(httpMethod.toUpperCase(Locale.ROOT), path)));
        });
        return routes;
    }

    /**
     * Known limitation (Phase 7 self-review Finding 2 / Kimi Phase 8 Finding 6): a handler using a
     * bare {@code @RequestMapping} with no explicit HTTP method yields an empty
     * {@code mapping.method()} array, so the loop below silently adds zero routes for it — such a
     * handler would be skipped by both completeness checks rather than flagged. No handler in
     * {@link WatchController}, {@link AttestController}, or {@link VerificationKeysController} does
     * this today (each uses a method-fixing shorthand like {@code @PostMapping}), so this is a
     * documented, currently-dormant gap, not a live defect.
     */
    private Set<Route> controllerRoutes() {
        Set<Route> routes = new LinkedHashSet<>();
        for (Class<?> controller : CONTROLLERS) {
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String basePath = classMapping != null && classMapping.value().length > 0 ? classMapping.value()[0] : "";
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String subPath = mapping.value().length > 0 ? mapping.value()[0] : "";
                String fullPath = basePath + subPath;
                for (var httpMethod : mapping.method()) {
                    routes.add(new Route(httpMethod.name(), fullPath));
                }
            }
        }
        return routes;
    }

    private Map<String, Object> realInstancesByComponentName() {
        Map<String, Object> instances = new LinkedHashMap<>();

        instances.put("RegisterWatchRequest", new RegisterWatchRequest(
                UUID.randomUUID(), "ETHEREUM", "0x5AEDA56215b167893e80B4fE645BA6d5Bab767DE",
                "0x5AEDA56215b167893e80B4fE645BA6d5Bab767DE", "1000000",
                Instant.parse("2026-09-17T00:00:00Z")));
        instances.put("RegisterWatchResponse", new RegisterWatchResponse(UUID.randomUUID(), "REGISTERED"));
        instances.put("AttestRequest", new AttestRequest("d".repeat(64), "ETHEREUM", "0xtx"));
        // Phase 4 Finding #4: both AttestResponse variants, under distinct synthetic keys, checked
        // against the same "AttestResponse" schema below.
        instances.put("AttestResponse-signed", AttestResponse.signed(
                "c2ln", "arn:aws:kms:us-east-1:111122223333:key/abc", Instant.parse("2026-09-17T00:00:00Z")));
        instances.put("AttestResponse-blocked", AttestResponse.blocked("counterparty address is sanctioned"));
        instances.put("PublicKeyInfo", new PublicKeyInfo(
                "arn:aws:kms:us-east-1:111122223333:key/abc", "arn:aws:kms:us-east-1:111122223333:key/abc",
                "ECDSA_SHA_256", "-----BEGIN PUBLIC KEY-----\nZmFrZQ==\n-----END PUBLIC KEY-----\n"));
        instances.put("VerificationKeysResponse", new VerificationKeysResponse(List.of(
                new PublicKeyInfo("arn:aws:kms:us-east-1:111122223333:key/abc",
                        "arn:aws:kms:us-east-1:111122223333:key/abc", "ECDSA_SHA_256",
                        "-----BEGIN PUBLIC KEY-----\nZmFrZQ==\n-----END PUBLIC KEY-----\n"))));

        return instances;
    }
}
