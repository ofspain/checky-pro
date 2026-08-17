package com.themistra.auth.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.themistra.auth.account.AccountController;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.account.AdminAccountController;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.ChangePasswordRequest;
import com.themistra.auth.account.dto.PasswordResetConfirmRequest;
import com.themistra.auth.account.dto.PasswordResetRequest;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.account.dto.RegistrationAcknowledgement;
import com.themistra.auth.account.dto.ResendVerificationRequest;
import com.themistra.auth.account.dto.VerifyEmailRequest;
import com.themistra.auth.apikey.ApiKeyController;
import com.themistra.auth.apikey.ApiKeyService;
import com.themistra.auth.apikey.dto.ApiKeyTokenResponse;
import com.themistra.auth.apikey.dto.CreateApiKeyRequest;
import com.themistra.auth.audit.AdminAuditController;
import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.dto.AuditEventResponse;
import com.themistra.auth.authz.AdminAccountRoleController;
import com.themistra.auth.authz.AdminRoleController;
import com.themistra.auth.authz.AdminRoleTemplateController;
import com.themistra.auth.authz.dto.CreateRoleRequest;
import com.themistra.auth.authz.dto.CreateRoleTemplateRequest;
import com.themistra.auth.authz.dto.RoleResponse;
import com.themistra.auth.authz.dto.RoleTemplateResponse;
import com.themistra.auth.token.dto.SessionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
 * The named test for T33/R47: {@code contracts/api/auth.yaml} must document every non-SAS
 * endpoint (completeness) and every documented response schema must match its real DTO's actual
 * serialized shape (correctness). Scoped to success responses only (Phase 4 D2) — request bodies,
 * parameters, and error responses are documented in {@code auth.yaml} for human/codegen value but
 * are not verified here, matching R47's own literal "service responses ... SHALL conform" wording.
 * No Spring context, no Testcontainers — pure reflection over the controller classes plus the
 * same plain-Jackson structural-comparison technique {@code UserLifecycleEventPayloadContractTest}
 * already established, applied to an OpenAPI document instead of a JSON Schema.
 */
class AuthOpenApiContractTest {

    private static final Path CONTRACT_PATH = Path.of("../../contracts/api/auth.yaml");

    private static final List<Class<?>> CONTROLLERS = List.of(
            AccountController.class,
            AdminAccountController.class,
            ApiKeyController.class,
            AdminAccountRoleController.class,
            AdminRoleController.class,
            AdminRoleTemplateController.class,
            AdminAuditController.class);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final YAMLMapper yamlMapper = new YAMLMapper();

    private record Route(String method, String path) {
    }

    @Test
    void everyControllerHandlerIsDocumentedInAuthYaml() throws Exception {
        Set<Route> yamlRoutes = yamlRoutes();
        Set<Route> controllerRoutes = controllerRoutes();

        for (Route route : controllerRoutes) {
            assertThat(yamlRoutes)
                    .as("auth.yaml should document handler route %s", route)
                    .contains(route);
        }
    }

    @Test
    void authYamlDocumentsNoRouteThatDoesNotHaveARealHandler() throws Exception {
        Set<Route> yamlRoutes = yamlRoutes();
        Set<Route> controllerRoutes = controllerRoutes();

        for (Route route : yamlRoutes) {
            assertThat(controllerRoutes)
                    .as("auth.yaml route %s should map to a real controller handler", route)
                    .contains(route);
        }
    }

    @Test
    void everyComponentSchemaMatchesItsRealDtoShape() throws Exception {
        JsonNode authYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        JsonNode components = authYaml.get("components").get("schemas");

        for (Map.Entry<String, Object> entry : realInstancesByComponentName().entrySet()) {
            String componentName = entry.getKey();
            JsonNode componentSchema = components.get(componentName);
            assertThat(componentSchema).as("component schema '%s' exists in auth.yaml", componentName).isNotNull();

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

    /** Kimi Phase 8 Finding 2: proves auth.yaml's own AccountStatus enum doesn't silently fall
     * behind Java, mirroring UserLifecycleEventPayloadContractTest's identical-purpose test. */
    @Test
    void everyAccountStatusValueIsCoveredByTheAccountResponseSchemaEnum() throws Exception {
        JsonNode authYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        JsonNode statusEnum = authYaml.get("components").get("schemas")
                .get("AccountResponse").get("properties").get("status").get("enum");
        var allowedStatuses = StreamSupport.stream(statusEnum.spliterator(), false)
                .map(JsonNode::asText)
                .toList();

        for (AccountStatus status : AccountStatus.values()) {
            assertThat(allowedStatuses)
                    .as("AccountResponse.status schema enum covers AccountStatus.%s", status)
                    .contains(status.name());
        }
    }

    /** Kimi Phase 8 Finding 2: same proof for AuditOutcome. */
    @Test
    void everyAuditOutcomeValueIsCoveredByTheAuditEventResponseSchemaEnum() throws Exception {
        JsonNode authYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        JsonNode outcomeEnum = authYaml.get("components").get("schemas")
                .get("AuditEventResponse").get("properties").get("outcome").get("enum");
        var allowedOutcomes = StreamSupport.stream(outcomeEnum.spliterator(), false)
                .map(JsonNode::asText)
                .toList();

        for (AuditOutcome outcome : AuditOutcome.values()) {
            assertThat(allowedOutcomes)
                    .as("AuditEventResponse.outcome schema enum covers AuditOutcome.%s", outcome)
                    .contains(outcome.name());
        }
    }

    /**
     * Kimi Phase 8 Finding 1: none of the other checks prove an operation's own {@code $ref}
     * actually names the CORRECT component — {@link #everyComponentSchemaMatchesItsRealDtoShape}
     * only checks that named components are internally correct, and completeness only checks that
     * routes exist. This maps every route with a documented response to its expected component
     * (Phase 5's own inventory table, restated here as the executable check) and resolves the
     * YAML's actual {@code $ref} to compare. Deliberately an explicit expectation table, not fully
     * automated type-reflection: {@code ApiKeyService.CreateApiKeyResult}'s YAML component is
     * intentionally named {@code ApiKeyCreateResult} (not a 1:1 simple-name match), and resolving
     * generic wrapper types ({@code List<T>}/{@code Set<String>}) via reflection would add real
     * complexity for the same result this table already gives directly.
     */
    @Test
    void everyOperationResponseReferencesTheExpectedSchema() throws Exception {
        JsonNode authYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        for (Map.Entry<Route, ExpectedSchema> entry : expectedResponseSchemas().entrySet()) {
            assertThat(actualResponseSchema(authYaml, entry.getKey()))
                    .as("%s response schema", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    /** Kimi Phase 8 Finding 1, request-body half. */
    @Test
    void everyOperationRequestBodyReferencesTheExpectedSchema() throws Exception {
        JsonNode authYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        for (Map.Entry<Route, ExpectedSchema> entry : expectedRequestSchemas().entrySet()) {
            assertThat(actualRequestSchema(authYaml, entry.getKey()))
                    .as("%s request schema", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    private record ExpectedSchema(String kind, String name) {
        static ExpectedSchema ref(String name) {
            return new ExpectedSchema("ref", name);
        }

        static ExpectedSchema arrayOfRef(String name) {
            return new ExpectedSchema("arrayRef", name);
        }

        static ExpectedSchema arrayOfPrimitive() {
            return new ExpectedSchema("arrayPrimitive", null);
        }

        static ExpectedSchema none() {
            return new ExpectedSchema("none", null);
        }
    }

    private ExpectedSchema actualResponseSchema(JsonNode authYaml, Route route) {
        JsonNode operation = authYaml.get("paths").get(route.path()).get(route.method().toLowerCase(Locale.ROOT));
        JsonNode responses = operation.get("responses");
        var statusCodes = responses.fieldNames();
        while (statusCodes.hasNext()) {
            String status = statusCodes.next();
            if (status.startsWith("2")) {
                JsonNode content = responses.get(status).get("content");
                if (content == null) {
                    return ExpectedSchema.none();
                }
                return parseSchemaNode(content.get("application/json").get("schema"));
            }
        }
        return ExpectedSchema.none();
    }

    private ExpectedSchema actualRequestSchema(JsonNode authYaml, Route route) {
        JsonNode operation = authYaml.get("paths").get(route.path()).get(route.method().toLowerCase(Locale.ROOT));
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
        if (schema.has("type") && "array".equals(schema.get("type").asText())) {
            JsonNode items = schema.get("items");
            if (items.has("$ref")) {
                return ExpectedSchema.arrayOfRef(refName(items.get("$ref").asText()));
            }
            return ExpectedSchema.arrayOfPrimitive();
        }
        throw new IllegalStateException("Unrecognized schema node shape: " + schema);
    }

    private String refName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    private Map<Route, ExpectedSchema> expectedResponseSchemas() {
        Map<Route, ExpectedSchema> m = new LinkedHashMap<>();
        m.put(new Route("POST", "/accounts"), ExpectedSchema.ref("RegistrationAcknowledgement"));
        m.put(new Route("GET", "/accounts/me"), ExpectedSchema.ref("AccountResponse"));
        m.put(new Route("POST", "/accounts/verify-email"), ExpectedSchema.none());
        m.put(new Route("POST", "/accounts/resend-verification"), ExpectedSchema.ref("RegistrationAcknowledgement"));
        m.put(new Route("POST", "/accounts/password-reset-request"), ExpectedSchema.ref("RegistrationAcknowledgement"));
        m.put(new Route("POST", "/accounts/password-reset"), ExpectedSchema.none());
        m.put(new Route("POST", "/accounts/me/password"), ExpectedSchema.none());
        m.put(new Route("GET", "/accounts/me/sessions"), ExpectedSchema.arrayOfRef("SessionResponse"));
        m.put(new Route("DELETE", "/accounts/me/sessions/{familyId}"), ExpectedSchema.none());
        m.put(new Route("DELETE", "/accounts/me/sessions"), ExpectedSchema.none());
        m.put(new Route("GET", "/admin/accounts/{accountUuid}"), ExpectedSchema.ref("AccountResponse"));
        m.put(new Route("POST", "/admin/accounts/{accountUuid}/activate"), ExpectedSchema.ref("AccountResponse"));
        m.put(new Route("POST", "/admin/accounts/{accountUuid}/suspend"), ExpectedSchema.ref("AccountResponse"));
        m.put(new Route("POST", "/admin/accounts/{accountUuid}/reinstate"), ExpectedSchema.ref("AccountResponse"));
        m.put(new Route("DELETE", "/admin/accounts/{accountUuid}"), ExpectedSchema.ref("AccountResponse"));
        m.put(new Route("POST", "/admin/accounts/{accountUuid}/unlock"), ExpectedSchema.ref("AccountResponse"));
        m.put(new Route("POST", "/api-keys"), ExpectedSchema.ref("ApiKeyCreateResult"));
        m.put(new Route("GET", "/api-keys"), ExpectedSchema.arrayOfRef("ApiKeyMetadata"));
        m.put(new Route("DELETE", "/api-keys/{keyUuid}"), ExpectedSchema.none());
        m.put(new Route("POST", "/api-keys/token"), ExpectedSchema.ref("ApiKeyTokenResponse"));
        m.put(new Route("GET", "/admin/accounts/{accountUuid}/roles"), ExpectedSchema.arrayOfPrimitive());
        m.put(new Route("POST", "/admin/accounts/{accountUuid}/roles/{roleName}"), ExpectedSchema.none());
        m.put(new Route("DELETE", "/admin/accounts/{accountUuid}/roles/{roleName}"), ExpectedSchema.none());
        m.put(new Route("POST", "/admin/accounts/{accountUuid}/role-templates/{templateName}"), ExpectedSchema.none());
        m.put(new Route("DELETE", "/admin/accounts/{accountUuid}/role-templates/{templateName}"), ExpectedSchema.none());
        m.put(new Route("POST", "/admin/roles"), ExpectedSchema.ref("RoleResponse"));
        m.put(new Route("GET", "/admin/roles"), ExpectedSchema.arrayOfRef("RoleResponse"));
        m.put(new Route("POST", "/admin/role-templates"), ExpectedSchema.ref("RoleTemplateResponse"));
        m.put(new Route("GET", "/admin/role-templates"), ExpectedSchema.arrayOfRef("RoleTemplateResponse"));
        m.put(new Route("GET", "/admin/audit"), ExpectedSchema.ref("AuditEventPage"));
        return m;
    }

    private Map<Route, ExpectedSchema> expectedRequestSchemas() {
        Map<Route, ExpectedSchema> m = new LinkedHashMap<>();
        m.put(new Route("POST", "/accounts"), ExpectedSchema.ref("RegisterAccountRequest"));
        m.put(new Route("POST", "/accounts/verify-email"), ExpectedSchema.ref("VerifyEmailRequest"));
        m.put(new Route("POST", "/accounts/resend-verification"), ExpectedSchema.ref("ResendVerificationRequest"));
        m.put(new Route("POST", "/accounts/password-reset-request"), ExpectedSchema.ref("PasswordResetRequest"));
        m.put(new Route("POST", "/accounts/password-reset"), ExpectedSchema.ref("PasswordResetConfirmRequest"));
        m.put(new Route("POST", "/accounts/me/password"), ExpectedSchema.ref("ChangePasswordRequest"));
        m.put(new Route("POST", "/api-keys"), ExpectedSchema.ref("CreateApiKeyRequest"));
        m.put(new Route("POST", "/admin/roles"), ExpectedSchema.ref("CreateRoleRequest"));
        m.put(new Route("POST", "/admin/role-templates"), ExpectedSchema.ref("CreateRoleTemplateRequest"));
        return m;
    }

    private Set<Route> yamlRoutes() throws Exception {
        JsonNode authYaml = yamlMapper.readTree(Files.readString(CONTRACT_PATH));
        Set<Route> routes = new LinkedHashSet<>();
        JsonNode paths = authYaml.get("paths");
        paths.fieldNames().forEachRemaining(path -> {
            JsonNode operations = paths.get(path);
            operations.fieldNames().forEachRemaining(
                    httpMethod -> routes.add(new Route(httpMethod.toUpperCase(Locale.ROOT), path)));
        });
        return routes;
    }

    /**
     * Known limitation (Phase 7 self-review Finding 1 / Kimi Phase 8 Finding 4): a handler using a
     * bare {@code @RequestMapping} with no explicit HTTP method yields an empty
     * {@code mapping.method()} array, so the loop below silently adds zero routes for it — such a
     * handler would be skipped by both completeness checks rather than flagged. No handler in this
     * codebase does this today (every one uses a method-fixing shorthand like {@code @GetMapping}),
     * so this is a documented, currently-dormant gap, not a live defect.
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

        instances.put("RegisterAccountRequest", new RegisterAccountRequest("new@example.com", "correct-horse-battery"));
        instances.put("RegistrationAcknowledgement", RegistrationAcknowledgement.standard());
        instances.put("VerifyEmailRequest", new VerifyEmailRequest("raw-token"));
        instances.put("ResendVerificationRequest", new ResendVerificationRequest("new@example.com"));
        instances.put("PasswordResetRequest", new PasswordResetRequest("new@example.com"));
        instances.put("PasswordResetConfirmRequest", new PasswordResetConfirmRequest("raw-token", "correct-horse-battery"));
        instances.put("ChangePasswordRequest", new ChangePasswordRequest("old-correct-horse", "new-correct-horse"));
        instances.put("AccountResponse", new AccountResponse(
                UUID.randomUUID(), "new@example.com", true, AccountStatus.ACTIVE, Instant.parse("2026-07-13T00:00:00Z")));
        instances.put("SessionResponse", new SessionResponse(
                UUID.randomUUID(), null, Instant.parse("2026-07-13T00:00:00Z"), Instant.parse("2026-07-13T00:00:00Z")));
        instances.put("CreateApiKeyRequest", new CreateApiKeyRequest("my merchant key"));
        instances.put("ApiKeyCreateResult", new ApiKeyService.CreateApiKeyResult(
                UUID.randomUUID(), "ck_live_abcdefghijklmnopqrstuvwx.secretsecretsecretsecretsecretsecret",
                "my merchant key", Instant.parse("2026-07-13T00:00:00Z")));
        instances.put("ApiKeyMetadata", new ApiKeyService.ApiKeyMetadata(
                UUID.randomUUID(), "my merchant key", List.of("merchant.api"),
                Instant.parse("2026-07-13T00:00:00Z"), null, null, null));
        instances.put("ApiKeyTokenResponse", ApiKeyTokenResponse.of("signed.jwt.value", 600));
        instances.put("CreateRoleRequest", new CreateRoleRequest("ROLE_EXAMPLE", "an example role"));
        instances.put("RoleResponse", new RoleResponse("ROLE_EXAMPLE", "an example role"));
        instances.put("CreateRoleTemplateRequest", new CreateRoleTemplateRequest(
                "EXAMPLE_TEMPLATE", "an example template", Set.of("ROLE_EXAMPLE")));
        instances.put("RoleTemplateResponse", new RoleTemplateResponse(
                "EXAMPLE_TEMPLATE", "an example template", Set.of("ROLE_EXAMPLE")));
        instances.put("AuditEventResponse", new AuditEventResponse(
                1L, Instant.parse("2026-07-13T00:00:00Z"), "login.failed", AuditOutcome.FAILURE,
                UUID.randomUUID(), UUID.randomUUID(), "203.0.113.7", "hash", "trace-id", Map.of()));
        instances.put("AuditEventPage", new PageImpl<>(
                List.of(new AuditEventResponse(
                        1L, Instant.parse("2026-07-13T00:00:00Z"), "login.failed", AuditOutcome.FAILURE,
                        UUID.randomUUID(), UUID.randomUUID(), "203.0.113.7", "hash", "trace-id", Map.of())),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "occurredAt")), 1));

        return instances;
    }
}
