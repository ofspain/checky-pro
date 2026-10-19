package com.themistra.payment.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.themistra.payment.export.ExportController;
import com.themistra.payment.export.TransactionExportRow;
import com.themistra.payment.invoice.InvoiceController;
import com.themistra.payment.invoice.InvoiceState;
import com.themistra.payment.invoice.dto.CreateInvoiceRequest;
import com.themistra.payment.invoice.dto.InvoiceResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * T24 (R31): the named contract test for {@code contracts/api/payments.yaml}. Proves every real
 * endpoint is documented and nothing else is (completeness), and that every documented schema
 * matches its real record field-for-field, including required/nullable status and Java type to
 * type/format mapping (correctness). Mirrors {@code NotificationOpenApiContractTest}'s and
 * {@code CryptoInternalOpenApiContractTest}'s structure: no Spring context, no Testcontainers,
 * just reflection over the controllers plus plain Jackson. Error responses are not documented
 * per route (R47 precedent); instead the {@code Problem} schema's {@code type} enum must equal
 * the {@link ProblemTypes} catalogue exactly (Phase 4 G3).
 */
class PaymentsOpenApiContractTest {

    private static final Path CONTRACT_PATH = Path.of("../../contracts/api/payments.yaml");

    private static final List<Class<?>> CONTROLLERS = List.of(
            InvoiceController.class,
            ExportController.class);

    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final YAMLMapper yamlMapper = new YAMLMapper();

    private record Route(String method, String path) {
    }

    @Test
    void everyControllerHandlerIsDocumentedInPaymentsYaml() throws Exception {
        Set<Route> yamlRoutes = yamlRoutes();
        for (Route route : controllerRoutes()) {
            assertThat(yamlRoutes).as("payments.yaml should document handler route %s", route).contains(route);
        }
    }

    @Test
    void paymentsYamlDocumentsNoRouteThatDoesNotHaveARealHandler() throws Exception {
        Set<Route> controllerRoutes = controllerRoutes();
        for (Route route : yamlRoutes()) {
            assertThat(controllerRoutes)
                    .as("payments.yaml route %s should map to a real controller handler", route)
                    .contains(route);
        }
    }

    @Test
    void componentSchemasAreExactlyTheFourExpected() throws Exception {
        assertThat(fieldNames(contract().get("components").get("schemas")))
                .isEqualTo(Set.of("CreateInvoiceRequest", "InvoiceResponse", "TransactionExportRow", "Problem"));
    }

    @Test
    void everyComponentSchemaMatchesItsRealRecordShape() throws Exception {
        JsonNode schemas = contract().get("components").get("schemas");

        for (Map.Entry<String, Record> entry : realInstancesByComponentName().entrySet()) {
            String name = entry.getKey();
            Class<?> type = entry.getValue().getClass();
            JsonNode schema = schemas.get(name);
            assertThat(schema).as("component schema '%s' exists", name).isNotNull();
            JsonNode properties = schema.get("properties");

            Set<String> componentNames = Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getName)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            assertThat(fieldNames(properties)).as("%s properties equal the record's components", name)
                    .isEqualTo(componentNames);
            assertThat(textSet(schema.get("required"))).as("%s required set", name)
                    .isEqualTo(expectedRequired(name, componentNames));

            Set<String> nullable = expectedNullable().getOrDefault(name, Set.of());
            for (RecordComponent component : type.getRecordComponents()) {
                assertTypeMatches(name, component, properties.get(component.getName()),
                        nullable.contains(component.getName()));
            }

            JsonNode serialized = objectMapper.valueToTree(entry.getValue());
            serialized.fieldNames().forEachRemaining(field -> assertThat(properties.has(field))
                    .as("%s: serialized field '%s' is declared", name, field).isTrue());
        }
    }

    @Test
    void invoiceStateSchemaEnumMatchesInvoiceStateValues() throws Exception {
        JsonNode state = contract().get("components").get("schemas").get("InvoiceResponse")
                .get("properties").get("state");
        Set<String> expected = Arrays.stream(InvoiceState.values()).map(Enum::name).collect(Collectors.toSet());
        assertThat(textSet(state.get("enum"))).isEqualTo(expected);
    }

    /** Mirrors {@code CreateInvoiceRequest.chain}'s own {@code @Pattern("ETHEREUM|TRON")}. */
    @Test
    void createInvoiceRequestChainEnumMatchesItsValidationPattern() throws Exception {
        JsonNode chain = contract().get("components").get("schemas").get("CreateInvoiceRequest")
                .get("properties").get("chain");
        assertThat(textSet(chain.get("enum"))).isEqualTo(Set.of("ETHEREUM", "TRON"));
    }

    @Test
    void problemTypeEnumMatchesProblemTypesCatalogue() throws Exception {
        Set<String> catalogue = new HashSet<>();
        for (Field field : ProblemTypes.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                    && field.getType() == URI.class) {
                catalogue.add(field.get(null).toString());
            }
        }
        assertThat(catalogue).as("ProblemTypes catalogue size").hasSize(11);

        JsonNode type = contract().get("components").get("schemas").get("Problem").get("properties").get("type");
        assertThat(type.get("format").asText()).isEqualTo("uri");
        assertThat(textSet(type.get("enum"))).as("Problem.type enum equals ProblemTypes").isEqualTo(catalogue);
    }

    /** Every route is {@code @PreAuthorize("hasRole('MERCHANT')")}; this service has no public route. */
    @Test
    void everyOperationRequiresBearerAuthWithMerchantScope() throws Exception {
        JsonNode contract = contract();
        JsonNode bearerAuth = contract.get("components").get("securitySchemes").get("bearerAuth");
        assertThat(bearerAuth.get("type").asText()).isEqualTo("http");
        assertThat(bearerAuth.get("scheme").asText()).isEqualTo("bearer");

        for (Route route : yamlRoutes()) {
            JsonNode security = operation(contract, route).get("security");
            assertThat(security).as("%s declares a security requirement", route).isNotNull();
            assertThat(security).as("%s has exactly one security requirement", route).hasSize(1);
            JsonNode scopes = security.get(0).get("bearerAuth");
            assertThat(scopes).as("%s requires bearerAuth", route).isNotNull();
            assertThat(textSet(scopes)).as("%s requires the MERCHANT role", route).isEqualTo(Set.of("MERCHANT"));
        }
    }

    @Test
    void invoiceUuidPathParameterIsDeclared() throws Exception {
        JsonNode parameter = parameter(contract(), new Route("GET", "/api/v1/invoices/{invoiceUuid}"), "invoiceUuid");
        assertThat(parameter.get("in").asText()).isEqualTo("path");
        assertThat(parameter.get("required").asBoolean()).isTrue();
        assertThat(parameter.get("schema").get("type").asText()).isEqualTo("string");
        assertThat(parameter.get("schema").get("format").asText()).isEqualTo("uuid");
    }

    @Test
    void exportEndpointDocumentsItsQueryParametersAndAcceptHeader() throws Exception {
        JsonNode contract = contract();
        Route export = new Route("GET", "/api/v1/exports/transactions");

        for (String name : List.of("from", "to")) {
            JsonNode parameter = parameter(contract, export, name);
            assertThat(parameter.get("in").asText()).as(name).isEqualTo("query");
            assertThat(parameter.get("required").asBoolean()).as("%s is required", name).isTrue();
            assertThat(parameter.get("schema").get("type").asText()).as(name).isEqualTo("string");
            assertThat(parameter.get("schema").get("format").asText()).as(name).isEqualTo("date-time");
        }

        JsonNode format = parameter(contract, export, "format");
        assertThat(format.get("in").asText()).isEqualTo("query");
        assertThat(format.path("required").asBoolean(false)).as("format is optional").isFalse();
        assertThat(textSet(format.get("schema").get("enum"))).isEqualTo(Set.of("csv", "json"));

        JsonNode accept = parameter(contract, export, "Accept");
        assertThat(accept.get("in").asText()).isEqualTo("header");
        assertThat(accept.path("required").asBoolean(false)).as("Accept is optional").isFalse();
    }

    @Test
    void exportEndpointDocumentsJsonAndCsvContent() throws Exception {
        JsonNode ok = operation(contract(), new Route("GET", "/api/v1/exports/transactions"))
                .get("responses").get("200");
        JsonNode content = ok.get("content");

        assertThat(fieldNames(content)).isEqualTo(Set.of("application/json", "text/csv"));
        assertThat(content.get("text/csv").get("schema").get("type").asText()).isEqualTo("string");
        assertThat(ok.get("headers").has("Content-Disposition")).as("CSV download header documented").isTrue();
    }

    @Test
    void everyOperationResponseReferencesTheExpectedSchema() throws Exception {
        JsonNode contract = contract();
        for (Map.Entry<Route, ExpectedResponse> entry : expectedResponseSchemas().entrySet()) {
            ExpectedResponse expected = entry.getValue();
            JsonNode responses = operation(contract, entry.getKey()).get("responses");
            assertThat(fieldNames(responses)).as("%s documents exactly its success status", entry.getKey())
                    .isEqualTo(Set.of(expected.status()));
            JsonNode schema = responses.get(expected.status()).get("content").get("application/json").get("schema");
            assertThat(parseSchemaNode(schema)).as("%s response schema", entry.getKey()).isEqualTo(expected.schema());
        }
    }

    @Test
    void everyOperationRequestBodyReferencesTheExpectedSchema() throws Exception {
        JsonNode contract = contract();
        for (Route route : yamlRoutes()) {
            JsonNode requestBody = operation(contract, route).get("requestBody");
            ExpectedSchema expected = expectedRequestSchemas().get(route);
            if (expected == null) {
                assertThat(requestBody).as("%s documents no request body", route).isNull();
                continue;
            }
            assertThat(requestBody).as("%s documents a request body", route).isNotNull();
            assertThat(requestBody.get("required").asBoolean()).as("%s request body is required", route).isTrue();
            assertThat(parseSchemaNode(requestBody.get("content").get("application/json").get("schema")))
                    .as("%s request schema", route).isEqualTo(expected);
        }
    }

    /** Phase 4 Finding 1: the contract says {@code amount} is a string while the DTO is
     * {@code BigDecimal}; this proves a contract-conforming client's string is accepted. The value
     * is a whole number of base units, the only kind the service accepts (Phase 9, Kimi #2). */
    @Test
    void createInvoiceRequestAcceptsADecimalStringAmount() throws Exception {
        CreateInvoiceRequest request = objectMapper.readValue("""
                {"chain":"ETHEREUM","tokenContractAddress":"0xtoken","tokenDecimals":6,
                 "amount":"1000000","payToAddress":"0xpayto","reference":null}
                """, CreateInvoiceRequest.class);
        assertThat(request.amount()).isEqualByComparingTo(new BigDecimal("1000000"));
    }

    /** Phase 9 (Kimi #6, partial): the documented limits must equal the DTO's own Bean
     * Validation annotations, so changing one without the other fails here. */
    @Test
    void createInvoiceRequestLimitsMatchItsValidationAnnotations() throws Exception {
        JsonNode properties = contract().get("components").get("schemas").get("CreateInvoiceRequest")
                .get("properties");
        assertThat(properties.get("tokenDecimals").get("minimum").asLong()).as("tokenDecimals minimum")
                .isEqualTo(fieldAnnotation(CreateInvoiceRequest.class, "tokenDecimals", Min.class).value());
        assertThat(properties.get("tokenDecimals").get("maximum").asLong()).as("tokenDecimals maximum")
                .isEqualTo(fieldAnnotation(CreateInvoiceRequest.class, "tokenDecimals", Max.class).value());
        assertThat(properties.get("reference").get("maxLength").asInt()).as("reference maxLength")
                .isEqualTo(fieldAnnotation(CreateInvoiceRequest.class, "reference", Size.class).max());
    }

    /** Phase 9 (self F1 / Kimi #1): the request amount pattern admits exactly positive whole
     * base units in canonical form, the only amounts {@code InvoiceService} accepts (R4). */
    @Test
    void createInvoiceRequestAmountPatternAdmitsOnlyPositiveWholeBaseUnits() throws Exception {
        JsonNode amount = contract().get("components").get("schemas").get("CreateInvoiceRequest")
                .get("properties").get("amount");
        String pattern = amount.get("pattern").asText();
        assertThat(pattern).isEqualTo("^[1-9][0-9]*$");

        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
        for (String valid : List.of("1", "1000000", "115792089237316195423570985008687907853269984665640564039457584007913129639935")) {
            assertThat(regex.matcher(valid).matches()).as("'%s' is a valid amount", valid).isTrue();
        }
        for (String invalid : List.of("0", "-1", "10.5", "1e6", "01", "", " 1")) {
            assertThat(regex.matcher(invalid).matches()).as("'%s' is not a valid amount", invalid).isFalse();
        }
    }

    /** Phase 9 (self F4 / Kimi #3): {@code Problem.violations} items mirror the handler's own
     * {@code Violation} record, and {@code trace_id} is documented; neither is required. */
    @Test
    void problemExtensionMembersMatchWhatTheHandlersEmit() throws Exception {
        JsonNode problem = contract().get("components").get("schemas").get("Problem");
        JsonNode items = problem.get("properties").get("violations").get("items");

        Set<String> violationComponents = Arrays.stream(ApiExceptionHandler.Violation.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertThat(problem.get("properties").get("violations").get("type").asText()).isEqualTo("array");
        assertThat(fieldNames(items.get("properties"))).isEqualTo(violationComponents);
        assertThat(textSet(items.get("required"))).isEqualTo(violationComponents);
        assertThat(problem.get("properties").get("trace_id").get("type").asText()).isEqualTo("string");
        assertThat(textSet(problem.get("required"))).isEqualTo(Set.of("type", "title", "status"));
    }

    /** The CSV variant's documented column list is exactly {@link TransactionExportRow}'s own
     * component order, which is the order {@code ExportController.toCsv} writes (design.md O5).
     * Parsed and compared as a list, so an extra or missing column fails (Phase 11, Kimi Gap 1). */
    @Test
    void csvDescriptionListsTheExportColumnsInRecordOrder() throws Exception {
        String description = operation(contract(), new Route("GET", "/api/v1/exports/transactions"))
                .get("responses").get("200").get("content").get("text/csv").get("schema")
                .get("description").asText().replaceAll("\\s+", " ");
        String marker = "columns in this order: ";
        int start = description.indexOf(marker);
        assertThat(start).as("CSV description introduces its column list with '%s'", marker).isNotNegative();
        int end = description.indexOf('.', start);
        assertThat(end).as("CSV column list ends with a period").isPositive();
        List<String> documented = Arrays.stream(description.substring(start + marker.length(), end).split(","))
                .map(String::trim)
                .toList();
        List<String> columns = Arrays.stream(TransactionExportRow.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(documented).containsExactlyElementsOf(columns);
    }

    /** Dependency-free OpenAPI 3.0 structural check (Phase 11, Kimi Gap 4): version and info
     * present, every operation documents responses, every security requirement names a defined
     * scheme, and every {@code $ref} resolves inside this file. A full validator library is
     * deliberately not added (AC5: exactly one new dependency). */
    @Test
    void paymentsYamlIsStructurallyValidOpenApi3() throws Exception {
        JsonNode contract = contract();
        assertThat(contract.get("openapi").asText()).isEqualTo("3.0.3");
        assertThat(contract.path("info").path("title").asText()).isNotBlank();
        assertThat(contract.path("info").path("version").asText()).isNotBlank();

        JsonNode schemes = contract.get("components").get("securitySchemes");
        for (Route route : yamlRoutes()) {
            JsonNode operation = operation(contract, route);
            assertThat(operation.path("responses").isObject() && !operation.get("responses").isEmpty())
                    .as("%s documents at least one response", route).isTrue();
            operation.path("security").forEach(requirement -> requirement.fieldNames().forEachRemaining(
                    scheme -> assertThat(schemes.has(scheme)).as("%s security scheme '%s' is defined", route, scheme)
                            .isTrue()));
        }

        List<String> refs = new java.util.ArrayList<>();
        collectRefs(contract, refs);
        assertThat(refs).as("the contract uses $refs").isNotEmpty();
        for (String ref : refs) {
            assertThat(ref).as("$ref is local").startsWith("#/");
            assertThat(contract.at(ref.substring(1)).isMissingNode()).as("$ref %s resolves", ref).isFalse();
        }
    }

    /** Phase 11 (Kimi Gap 6, partial): every amount description states whole base units. No
     * outbound {@code pattern} is added (Phase 9 human decision); only the request carries one. */
    @Test
    void everyAmountDescriptionStatesWholeBaseUnits() throws Exception {
        JsonNode schemas = contract().get("components").get("schemas");
        for (String component : List.of("CreateInvoiceRequest", "InvoiceResponse", "TransactionExportRow")) {
            assertThat(schemas.get(component).get("properties").get("amount").get("description").asText()
                    .replaceAll("\\s+", " "))
                    .as("%s.amount description", component)
                    .contains("whole number of token base units");
        }
    }

    /** The named test: {@code shouldConformToPaymentsOpenApiContract} (R31). */
    @Test
    void shouldConformToPaymentsOpenApiContract() throws Exception {
        everyControllerHandlerIsDocumentedInPaymentsYaml();
        paymentsYamlDocumentsNoRouteThatDoesNotHaveARealHandler();
        componentSchemasAreExactlyTheFourExpected();
        everyComponentSchemaMatchesItsRealRecordShape();
        invoiceStateSchemaEnumMatchesInvoiceStateValues();
        createInvoiceRequestChainEnumMatchesItsValidationPattern();
        problemTypeEnumMatchesProblemTypesCatalogue();
        everyOperationRequiresBearerAuthWithMerchantScope();
        invoiceUuidPathParameterIsDeclared();
        exportEndpointDocumentsItsQueryParametersAndAcceptHeader();
        exportEndpointDocumentsJsonAndCsvContent();
        everyOperationResponseReferencesTheExpectedSchema();
        everyOperationRequestBodyReferencesTheExpectedSchema();
        createInvoiceRequestAcceptsADecimalStringAmount();
        createInvoiceRequestLimitsMatchItsValidationAnnotations();
        createInvoiceRequestAmountPatternAdmitsOnlyPositiveWholeBaseUnits();
        problemExtensionMembersMatchWhatTheHandlersEmit();
        csvDescriptionListsTheExportColumnsInRecordOrder();
        paymentsYamlIsStructurallyValidOpenApi3();
        everyAmountDescriptionStatesWholeBaseUnits();
    }

    /** Guards against silently forgetting to update {@link #expectedResponseSchemas()} when a
     * route is added (sibling-identical guard). */
    @Test
    void expectedResponseSchemasCoverEveryControllerRoute() {
        assertThat(expectedResponseSchemas().keySet())
                .as("expectedResponseSchemas() must have one entry per real controller route")
                .isEqualTo(controllerRoutes());
    }

    @Test
    void expectedRequestSchemasCoverEveryRequestBodyHandler() {
        assertThat(expectedRequestSchemas().keySet())
                .as("expectedRequestSchemas() must have one entry per handler taking a @RequestBody")
                .isEqualTo(requestBodyHandlerRoutes());
    }

    private record ExpectedSchema(String kind, String name) {
        static ExpectedSchema ref(String name) {
            return new ExpectedSchema("ref", name);
        }

        static ExpectedSchema arrayOfRef(String name) {
            return new ExpectedSchema("arrayRef", name);
        }
    }

    private record ExpectedResponse(String status, ExpectedSchema schema) {
    }

    private Map<Route, ExpectedResponse> expectedResponseSchemas() {
        Map<Route, ExpectedResponse> m = new LinkedHashMap<>();
        m.put(new Route("POST", "/api/v1/invoices"), new ExpectedResponse("201", ExpectedSchema.ref("InvoiceResponse")));
        m.put(new Route("GET", "/api/v1/invoices"), new ExpectedResponse("200", ExpectedSchema.arrayOfRef("InvoiceResponse")));
        m.put(new Route("GET", "/api/v1/invoices/{invoiceUuid}"),
                new ExpectedResponse("200", ExpectedSchema.ref("InvoiceResponse")));
        m.put(new Route("GET", "/api/v1/exports/transactions"),
                new ExpectedResponse("200", ExpectedSchema.arrayOfRef("TransactionExportRow")));
        return m;
    }

    private Map<Route, ExpectedSchema> expectedRequestSchemas() {
        Map<Route, ExpectedSchema> m = new LinkedHashMap<>();
        m.put(new Route("POST", "/api/v1/invoices"), ExpectedSchema.ref("CreateInvoiceRequest"));
        return m;
    }

    private Map<String, Record> realInstancesByComponentName() {
        Map<String, Record> instances = new LinkedHashMap<>();
        instances.put("CreateInvoiceRequest", new CreateInvoiceRequest(
                "ETHEREUM", "0xtoken", (short) 6, new BigDecimal("1000000"), "0xpayto", "order-1"));
        instances.put("InvoiceResponse", new InvoiceResponse(
                UUID.randomUUID(), UUID.randomUUID(), "ETHEREUM", "0xtoken", (short) 6, "1000000",
                "0xpayto", null, InvoiceState.OPEN, NOW, NOW, NOW));
        instances.put("TransactionExportRow", new TransactionExportRow(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ETHEREUM", "0xtx", "0xtoken",
                (short) 6, "1000000", "0xpayto", NOW, NOW, "s3://bucket/key"));
        return instances;
    }

    /** Fields that may be absent or null on the wire; everything else is required. */
    private Map<String, Set<String>> expectedNullable() {
        return Map.of(
                "CreateInvoiceRequest", Set.of("reference"),
                "InvoiceResponse", Set.of("reference"));
    }

    private Set<String> expectedRequired(String componentName, Set<String> componentNames) {
        Set<String> required = new HashSet<>(componentNames);
        required.removeAll(expectedNullable().getOrDefault(componentName, Set.of()));
        return required;
    }

    /** Java type to documented OpenAPI 3.0 type/format. Unknown types fail loudly, so a new
     * field type can never pass unchecked. Nullability is OpenAPI 3.0's {@code nullable: true}. */
    private static void assertTypeMatches(String owner, RecordComponent component, JsonNode property,
                                          boolean nullable) {
        String name = component.getName();
        Class<?> javaType = component.getType();
        String expectedType;
        String expectedFormat = null;
        if (javaType == UUID.class) {
            expectedType = "string";
            expectedFormat = "uuid";
        } else if (javaType == Instant.class) {
            expectedType = "string";
            expectedFormat = "date-time";
        } else if (javaType == String.class || javaType == BigDecimal.class || javaType == InvoiceState.class) {
            expectedType = "string";
        } else if (javaType == short.class || javaType == int.class) {
            expectedType = "integer";
        } else if (javaType == boolean.class) {
            expectedType = "boolean";
        } else {
            fail("%s.%s: no contract type mapping for Java type %s", owner, name, javaType.getName());
            return;
        }

        assertThat(property.get("type").asText()).as("%s.%s type", owner, name).isEqualTo(expectedType);
        if (expectedFormat == null) {
            assertThat(property.has("format")).as("%s.%s declares no format", owner, name).isFalse();
        } else {
            assertThat(property.path("format").asText()).as("%s.%s format", owner, name).isEqualTo(expectedFormat);
        }
        assertThat(property.path("nullable").asBoolean(false)).as("%s.%s nullable", owner, name).isEqualTo(nullable);
    }

    private ExpectedSchema parseSchemaNode(JsonNode schema) {
        if (schema.has("$ref")) {
            return ExpectedSchema.ref(refName(schema.get("$ref").asText()));
        }
        if (schema.has("type") && "array".equals(schema.get("type").asText())) {
            JsonNode items = schema.get("items");
            if (items.has("$ref")) {
                return ExpectedSchema.arrayOfRef(refName(items.get("$ref").asText()));
            }
        }
        throw new IllegalStateException("Unrecognized schema node shape: " + schema);
    }

    private static String refName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    private JsonNode contract() throws Exception {
        return yamlMapper.readTree(Files.readString(CONTRACT_PATH));
    }

    /** Asserts the path and method exist first, so a renamed or removed route fails with a
     * message naming it rather than a NullPointerException (Phase 9, self F5 / Kimi #5). */
    private static JsonNode operation(JsonNode contract, Route route) {
        JsonNode path = contract.get("paths").get(route.path());
        assertThat(path).as("payments.yaml documents path %s", route.path()).isNotNull();
        JsonNode operation = path.get(route.method().toLowerCase(Locale.ROOT));
        assertThat(operation).as("payments.yaml documents %s", route).isNotNull();
        return operation;
    }

    /** Bean Validation annotations on a record component propagate to its private field. */
    private static <A extends java.lang.annotation.Annotation> A fieldAnnotation(
            Class<?> type, String name, Class<A> annotation) throws NoSuchFieldException {
        A found = type.getDeclaredField(name).getAnnotation(annotation);
        assertThat(found).as("%s.%s carries @%s", type.getSimpleName(), name, annotation.getSimpleName()).isNotNull();
        return found;
    }

    private static JsonNode parameter(JsonNode contract, Route route, String name) {
        JsonNode parameters = operation(contract, route).get("parameters");
        assertThat(parameters).as("%s declares parameters", route).isNotNull();
        for (JsonNode parameter : parameters) {
            if (name.equals(parameter.get("name").asText())) {
                return parameter;
            }
        }
        throw new AssertionError(route + " does not declare parameter '" + name + "'");
    }

    private Set<Route> yamlRoutes() throws Exception {
        Set<Route> routes = new LinkedHashSet<>();
        JsonNode paths = contract().get("paths");
        paths.fieldNames().forEachRemaining(path -> paths.get(path).fieldNames().forEachRemaining(
                httpMethod -> routes.add(new Route(httpMethod.toUpperCase(Locale.ROOT), path))));
        return routes;
    }

    private Set<Route> controllerRoutes() {
        Set<Route> routes = new LinkedHashSet<>();
        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                routes.addAll(routesOf(controller, method));
            }
        }
        return routes;
    }

    private Set<Route> requestBodyHandlerRoutes() {
        Set<Route> routes = new LinkedHashSet<>();
        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                boolean takesBody = Arrays.stream(method.getParameters())
                        .anyMatch((Parameter p) -> p.isAnnotationPresent(RequestBody.class));
                if (takesBody) {
                    routes.addAll(routesOf(controller, method));
                }
            }
        }
        return routes;
    }

    private static Set<Route> routesOf(Class<?> controller, Method method) {
        Set<Route> routes = new LinkedHashSet<>();
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            return routes;
        }
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        String basePath = classMapping != null && classMapping.value().length > 0 ? classMapping.value()[0] : "";
        String subPath = mapping.value().length > 0 ? mapping.value()[0] : "";
        for (var httpMethod : mapping.method()) {
            routes.add(new Route(httpMethod.name(), basePath + subPath));
        }
        return routes;
    }

    private static void collectRefs(JsonNode node, List<String> refs) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                if ("$ref".equals(field.getKey())) {
                    refs.add(field.getValue().asText());
                } else {
                    collectRefs(field.getValue(), refs);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectRefs(child, refs));
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Set<String> textSet(JsonNode arrayNode) {
        Set<String> values = new HashSet<>();
        arrayNode.forEach(value -> values.add(value.asText()));
        return values;
    }
}
