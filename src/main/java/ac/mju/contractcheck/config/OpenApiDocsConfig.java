package ac.mju.contractcheck.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Swagger/OpenAPI 설명은 컨트롤러 어노테이션 대신 {@code openapi/api-docs.yml}에서 읽어
 * springdoc이 생성한 스펙에 합친다.
 */
@Slf4j
@Configuration
public class OpenApiDocsConfig {

    private static final String DOCS_RESOURCE = "/openapi/api-docs.yml";
    private static final String SESSION_SCHEME = "sessionCookie";

    private record DocsInfo(String title, String version, String description) {
        static DocsInfo of(Map<String, Object> source) {
            return new DocsInfo(text(source.get("title")), text(source.get("version")), text(source.get("description")));
        }
    }

    private record DocsTag(String name, String description) {
        static DocsTag of(Object item) {
            Map<String, Object> source = map(item);
            return new DocsTag(text(source.get("name")), text(source.get("description")));
        }
    }

    private record DocsParam(String name, String in, String description) {
        static DocsParam of(Object item) {
            Map<String, Object> source = map(item);
            return new DocsParam(text(source.get("name")), text(source.get("in")), text(source.get("description")));
        }
    }

    private record DocsOperation(
            String summary,
            String description,
            String tag,
            String auth,
            String requestBody,
            List<DocsParam> params,
            Map<String, String> responses) {
        static DocsOperation of(Object item) {
            Map<String, Object> source = map(item);
            Map<String, String> responses = new LinkedHashMap<>();
            map(source.get("responses")).forEach((code, value) -> responses.put(code, text(value)));
            return new DocsOperation(
                    text(source.get("summary")),
                    text(source.get("description")),
                    text(source.get("tag")),
                    text(source.get("auth")),
                    text(source.get("requestBody")),
                    list(source.get("params")).stream().map(DocsParam::of).toList(),
                    responses);
        }
    }

    private record DocsSchema(String name, String description, Map<String, String> fields) {
        static DocsSchema of(String name, Object item) {
            Map<String, Object> raw = new LinkedHashMap<>(map(item));
            String description = text(raw.remove("__description"));
            Map<String, String> fields = new LinkedHashMap<>();
            raw.forEach((field, value) -> fields.put(field, text(value)));
            return new DocsSchema(name, description, fields);
        }
    }

    @Bean
    OpenApiCustomizer openApiDocsCustomizer() {
        return this::applyDocs;
    }

    private void applyDocs(OpenAPI openApi) {
        Map<String, Object> docs = loadDocs();
        if (docs.isEmpty()) {
            return;
        }
        Map<String, Object> info = map(docs.get("info"));
        if (!info.isEmpty()) {
            mergeInfo(openApi, DocsInfo.of(info));
        }
        mergeTags(openApi, list(docs.get("tags")).stream().map(DocsTag::of).toList());
        ensureSessionScheme(openApi);
        Map<String, DocsOperation> paths = new LinkedHashMap<>();
        map(docs.get("paths")).forEach((key, value) -> paths.put(key, DocsOperation.of(value)));
        mergePaths(openApi, paths);
        mergeSchemas(openApi, map(docs.get("schemas")).entrySet().stream()
                .map(entry -> DocsSchema.of(entry.getKey(), entry.getValue()))
                .toList());
    }

    private Map<String, Object> loadDocs() {
        try (InputStream in = OpenApiDocsConfig.class.getResourceAsStream(DOCS_RESOURCE)) {
            if (in == null) {
                log.warn("OpenAPI 설명 파일을 찾을 수 없습니다: {}", DOCS_RESOURCE);
                return Map.of();
            }
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            Map<String, Object> docs = map(loaded);
            if (docs.isEmpty()) {
                log.warn("OpenAPI 설명 파일이 비어 있습니다: {}", DOCS_RESOURCE);
            }
            return docs;
        } catch (Exception exception) {
            log.error("OpenAPI 설명 파일을 읽지 못했습니다: {}", DOCS_RESOURCE, exception);
            return Map.of();
        }
    }

    private void mergeInfo(OpenAPI openApi, DocsInfo info) {
        Info target = existingOrNew(openApi.getInfo(), Info::new);
        setIfPresent(info.title(), target::setTitle);
        setIfPresent(info.version(), target::setVersion);
        setIfPresent(info.description(), target::setDescription);
        openApi.setInfo(target);
    }

    private void mergeTags(OpenAPI openApi, List<DocsTag> tags) {
        if (tags.isEmpty()) {
            return;
        }
        List<Tag> existing = openApi.getTags() == null ? new ArrayList<>() : new ArrayList<>(openApi.getTags());
        for (DocsTag source : tags) {
            if (source.name() == null) {
                continue;
            }
            Tag tag = existing.stream()
                    .filter(candidate -> source.name().equals(candidate.getName()))
                    .findFirst()
                    .orElseGet(() -> {
                        Tag created = new Tag().name(source.name());
                        existing.add(created);
                        return created;
                    });
            setIfPresent(source.description(), tag::setDescription);
        }
        openApi.setTags(existing);
    }

    private void ensureSessionScheme(OpenAPI openApi) {
        Components components = componentsOf(openApi);
        if (components.getSecuritySchemes() != null && components.getSecuritySchemes().containsKey(SESSION_SCHEME)) {
            return;
        }
        components.addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name("SESSION")
                .description("Spring Session 쿠키. 로그인 후 브라우저가 자동으로 첨부한다."));
    }

    private void mergePaths(OpenAPI openApi, Map<String, DocsOperation> paths) {
        paths.forEach((key, source) -> {
            String pathKey = key.trim();
            Operation operation = findOperation(openApi, pathKey);
            if (operation == null) {
                log.warn("openapi/api-docs.yml 항목이 실제 엔드포인트와 맞지 않습니다: {} (프로필에 따라 없을 수 있음)", pathKey);
                return;
            }
            applyOperation(operation, source);
        });
    }

    private Operation findOperation(OpenAPI openApi, String key) {
        String[] parts = key.split("\\s+", 2);
        if (parts.length != 2 || openApi.getPaths() == null) {
            return null;
        }
        HttpMethod method;
        try {
            method = HttpMethod.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
        PathItem pathItem = openApi.getPaths().get(parts[1]);
        return pathItem == null ? null : pathItem.readOperationsMap().get(method);
    }

    private void applyOperation(Operation operation, DocsOperation source) {
        setIfPresent(source.summary(), operation::setSummary);
        setIfPresent(withAuth(source.description(), source.auth()), operation::setDescription);
        if (source.tag() != null) {
            operation.setTags(List.of(source.tag()));
        }
        applySecurity(operation, source.auth());
        applyRequestBody(operation, source.requestBody());
        applyParams(operation, source.params(), source.summary());
        applyResponses(operation, source.responses());
    }

    private void applySecurity(Operation operation, String auth) {
        if (auth == null) {
            return;
        }
        if ("public".equals(auth)) {
            operation.setSecurity(List.of());
            return;
        }
        operation.setSecurity(List.of(new SecurityRequirement().addList(SESSION_SCHEME)));
    }

    private void applyRequestBody(Operation operation, String description) {
        if (description == null) {
            return;
        }
        RequestBody requestBody = existingOrNew(operation.getRequestBody(), RequestBody::new);
        operation.setRequestBody(requestBody);
        requestBody.setDescription(description);
    }

    private void applyParams(Operation operation, List<DocsParam> params, String operationKey) {
        for (DocsParam source : params) {
            if (source.name() == null) {
                continue;
            }
            Parameter parameter = findParameter(operation, source.name(), source.in());
            if (parameter == null) {
                log.warn("openapi/api-docs.yml 파라미터가 실제 엔드포인트와 맞지 않습니다: {} {} ({})",
                        source.in() == null ? "?" : source.in(), source.name(), operationKey == null ? "" : operationKey);
                continue;
            }
            setIfPresent(source.description(), parameter::setDescription);
        }
    }

    private Parameter findParameter(Operation operation, String name, String in) {
        if (operation.getParameters() == null) {
            return null;
        }
        Parameter byNameAndIn = null;
        Parameter byName = null;
        for (Parameter parameter : operation.getParameters()) {
            if (!name.equals(parameter.getName())) {
                continue;
            }
            if (byName == null) {
                byName = parameter;
            }
            if (in != null && in.equalsIgnoreCase(parameter.getIn())) {
                byNameAndIn = parameter;
            }
        }
        return byNameAndIn != null ? byNameAndIn : byName;
    }

    private void applyResponses(Operation operation, Map<String, String> responses) {
        if (responses.isEmpty()) {
            return;
        }
        ApiResponses target = existingOrNew(operation.getResponses(), ApiResponses::new);
        operation.setResponses(target);
        responses.forEach((code, description) -> {
            if (description == null) {
                return;
            }
            ApiResponse response = existingOrNew(target.get(code), ApiResponse::new);
            if (target.get(code) == null) {
                target.addApiResponse(code, response);
            }
            response.setDescription(description);
        });
        // YAML에 적은 상태 코드만 남긴다. springdoc 기본 200이 201/202/204 응답과 겹치지 않게 한다.
        target.keySet().removeIf(code -> !responses.containsKey(code));
    }

    @SuppressWarnings("rawtypes")
    private void mergeSchemas(OpenAPI openApi, List<DocsSchema> schemas) {
        if (schemas.isEmpty()) {
            return;
        }
        Components components = openApi.getComponents();
        Map<String, Schema> generated = components == null ? null : components.getSchemas();
        if (generated == null || generated.isEmpty()) {
            schemas.forEach(schema -> log.warn("openapi/api-docs.yml 스키마가 생성된 OpenAPI에 없습니다: {}", schema.name()));
            return;
        }
        for (DocsSchema source : schemas) {
            Schema<?> schema = findSchema(generated, source.name());
            if (schema == null) {
                log.warn("openapi/api-docs.yml 스키마가 생성된 OpenAPI에 없습니다: {}", source.name());
                continue;
            }
            setIfPresent(source.description(), schema::setDescription);
            Map<String, Schema> properties = schema.getProperties();
            if (properties == null) {
                continue;
            }
            source.fields().forEach((field, description) -> {
                Schema<?> property = properties.get(field);
                if (property == null) {
                    log.warn("openapi/api-docs.yml 스키마 필드가 생성된 OpenAPI에 없습니다: {}.{}", source.name(), field);
                    return;
                }
                setIfPresent(description, property::setDescription);
            });
        }
    }

    @SuppressWarnings("rawtypes")
    private Schema<?> findSchema(Map<String, Schema> generated, String name) {
        if (generated.containsKey(name)) {
            return generated.get(name);
        }
        Schema<?> suffixMatch = null;
        int matches = 0;
        for (Map.Entry<String, Schema> entry : generated.entrySet()) {
            if (entry.getKey().endsWith("." + name) || entry.getKey().endsWith(name)) {
                suffixMatch = entry.getValue();
                matches++;
            }
        }
        return matches == 1 ? suffixMatch : null;
    }

    private Components componentsOf(OpenAPI openApi) {
        Components components = existingOrNew(openApi.getComponents(), Components::new);
        openApi.setComponents(components);
        return components;
    }

    private static String withAuth(String description, String auth) {
        String note = switch (auth == null ? "" : auth) {
            case "public" -> "인증: 공개(로그인 불필요).";
            case "ADMIN" -> "인증: 로그인 필요, ADMIN 권한.";
            case "login" -> "인증: 로그인 필요.";
            default -> null;
        };
        if (note == null) {
            return description;
        }
        if (description == null || description.isBlank()) {
            return note;
        }
        if (description.contains("인증:")) {
            return description;
        }
        return description + "\n\n" + note;
    }

    private static void setIfPresent(String value, Consumer<String> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private static <T> T existingOrNew(T value, Supplier<T> factory) {
        return value == null ? factory.get() : value;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        raw.forEach((key, item) -> copy.put(String.valueOf(key), item));
        return copy;
    }

    private static List<Object> list(Object value) {
        if (value instanceof List<?> raw) {
            return new ArrayList<>(raw);
        }
        return List.of();
    }
}
