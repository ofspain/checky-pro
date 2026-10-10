package com.themistra.notification.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.themistra.notification.inapp.InappNotification;
import com.themistra.notification.inapp.InappReadController;
import com.themistra.notification.inapp.InappStreamController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The named test for the notification-service's own `contracts/api/notifications.yaml`:
 * documents every real endpoint (completeness) and every documented response schema matches its
 * real DTO's actual serialized shape (correctness). Scoped to success responses only, mirroring
 * {@code AuthOpenApiContractTest}'s and {@code CryptoInternalOpenApiContractTest}'s own identical
 * R47-precedent scoping — the DTO-mapped error responses (R15-safe, via {@code ApiExceptionHandler})
 * are not documented or verified here. No Spring context, no Testcontainers — pure reflection over
 * the controller classes plus the same plain-Jackson structural-comparison technique established
 * elsewhere in this repo.
 *
 * <p>This is this service's first OpenAPI contract file — it did not exist before (frontend spec's
 * own Q3). Both endpoints it documents ({@link InappStreamController}, {@link InappReadController})
 * were already fully built, at task 13; this test only adds the missing documentation/conformance
 * guard, no production code changes.</p>
 */
class NotificationOpenApiContractTest {

    private static final Path CONTRACT_PATH = Path.of("../../contracts/api/notifications.yaml");

    private static final List<Class<?>> CONTROLLERS = List.of(
            InappStreamController.class,
            InappReadController.class);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final YAMLMapper yamlMapper = new YAMLMapper();

    private record Route(String method, String path) {
    }

    @Test
    void everyControllerHandlerIsDocumentedInNotificationsYaml() throws Exception {
        Set<Route> yamlRoutes = yamlRoutes();
        Set<Route> controllerRoutes = controllerRoutes();

        for (Route route : controllerRoutes) {
            assertThat(yamlRoutes)
                    .as("notifications.yaml should document handler route %s", route)
                    .contains(route);
        }
    }

    @Test
    void notificationsYamlDocumentsNoRouteThatDoesNotHaveARealHandler() throws Exception {
        Set<Route> yamlRoutes = yamlRoutes();
        Set<Route> controllerRoutes = controllerRoutes();

        for (Route route : yamlRoutes) {
            assertThat(controllerRoutes)
                    .as("notifications.yaml route %s should map to a real controller handler", route)
                    .contains(route);
        }
    }

    @Test
    void everyComponentSchemaMatchesItsRealDtoShape() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        JsonNode components = contractYaml.get("components").get("schemas");

        for (Map.Entry<String, Object> entry : realInstancesByComponentName().entrySet()) {
            String componentName = entry.getKey();
            JsonNode componentSchema = components.get(componentName);
            assertThat(componentSchema).as("component schema '%s' exists in notifications.yaml", componentName)
                    .isNotNull();

            JsonNode serialized = objectMapper.valueToTree(entry.getValue());

            JsonNode required = componentSchema.get("required");
            if (required != null) {
                required.forEach(field -> assertThat(serialized.has(field.asText()))
                        .as("%s: required field '%s' present", componentName, field.asText())
                        .isTrue());
            }

            JsonNode declaredProperties = componentSchema.get("properties");
            serialized.fieldNames().forEachRemaining(field -> assertThat(declaredProperties.has(field))
                    .as("%s: serialized field '%s' is declared in the schema", componentName, field)
                    .isTrue());
        }
    }

    /** Both routes require {@code bearerAuth} with no specific scope — any authenticated caller,
     * scoped to their own {@code sub} by the handler itself, not by a role check. Neither route
     * accepts an unauthenticated connection ({@link InappStreamController}'s and
     * {@link InappReadController}'s own Javadoc). */
    @Test
    void bothRoutesRequireBearerAuthWithNoSpecificScope() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));

        assertThat(contractYaml.get("components").get("securitySchemes").get("bearerAuth").get("type").asText())
                .isEqualTo("http");
        assertThat(contractYaml.get("components").get("securitySchemes").get("bearerAuth").get("scheme").asText())
                .isEqualTo("bearer");

        for (Route route : List.of(
                new Route("GET", "/notifications/stream"),
                new Route("GET", "/notifications/unread"))) {
            JsonNode operation = contractYaml.get("paths").get(route.path()).get(route.method().toLowerCase(Locale.ROOT));
            JsonNode security = operation.get("security");
            assertThat(security).as("%s declares a security requirement", route).isNotNull();
            assertThat(security.get(0).has("bearerAuth")).as("%s requires bearerAuth", route).isTrue();
            assertThat(security.get(0).get("bearerAuth").isEmpty())
                    .as("%s requires no specific scope", route).isTrue();
        }
    }

    /** The stream endpoint's content type is {@code text/event-stream}, not {@code
     * application/json} — documented explicitly, not left to the generic per-event schema check
     * (which only inspects the JSON-envelope half of the two routes). */
    @Test
    void streamEndpointDocumentsTextEventStreamContentType() throws Exception {
        JsonNode contractYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        JsonNode content = contractYaml.get("paths").get("/notifications/stream").get("get")
                .get("responses").get("200").get("content");

        assertThat(content.has("text/event-stream")).as("stream response is text/event-stream").isTrue();
        assertThat(content.has("application/json")).as("stream response is not also application/json").isFalse();
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

    /** The named test: {@code shouldConformToNotificationsOpenApiContract}. */
    @Test
    void shouldConformToNotificationsOpenApiContract() throws Exception {
        everyControllerHandlerIsDocumentedInNotificationsYaml();
        notificationsYamlDocumentsNoRouteThatDoesNotHaveARealHandler();
        everyComponentSchemaMatchesItsRealDtoShape();
        bothRoutesRequireBearerAuthWithNoSpecificScope();
        streamEndpointDocumentsTextEventStreamContentType();
        everyOperationResponseReferencesTheExpectedSchema();
    }

    /** Guards against silently forgetting to update {@link #expectedResponseSchemas()} when a
     * route is added — mirrors {@code AuthOpenApiContractTest}'s/{@code
     * CryptoInternalOpenApiContractTest}'s identical guard. */
    @Test
    void expectedResponseSchemasCoverEveryControllerRoute() {
        assertThat(expectedResponseSchemas().keySet())
                .as("expectedResponseSchemas() must have one entry per real controller route")
                .isEqualTo(controllerRoutes());
    }

    private record ExpectedSchema(String kind, String name) {
        static ExpectedSchema ref(String name) {
            return new ExpectedSchema("ref", name);
        }

        static ExpectedSchema arrayOfRef(String name) {
            return new ExpectedSchema("arrayRef", name);
        }
    }

    /** Tries {@code application/json} first, then {@code text/event-stream} — the only two
     * content types either route in this file ever uses. */
    private ExpectedSchema actualResponseSchema(JsonNode contractYaml, Route route) {
        JsonNode operation = contractYaml.get("paths").get(route.path()).get(route.method().toLowerCase(Locale.ROOT));
        JsonNode content = operation.get("responses").get("200").get("content");
        JsonNode schema = content.has("application/json")
                ? content.get("application/json").get("schema")
                : content.get("text/event-stream").get("schema");
        return parseSchemaNode(schema);
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

    private String refName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    private Map<Route, ExpectedSchema> expectedResponseSchemas() {
        Map<Route, ExpectedSchema> m = new LinkedHashMap<>();
        m.put(new Route("GET", "/notifications/stream"), ExpectedSchema.ref("InAppNotification"));
        m.put(new Route("GET", "/notifications/unread"), ExpectedSchema.arrayOfRef("InAppNotification"));
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

        instances.put("InAppNotification", new InappNotification.View(
                UUID.randomUUID(), "dispute.opened", "A dispute was opened",
                "Your transaction has been disputed.", "/app/disputes/123",
                Instant.parse("2026-07-13T00:00:00Z")));

        return instances;
    }
}
