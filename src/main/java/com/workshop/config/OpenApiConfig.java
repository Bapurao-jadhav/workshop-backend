package com.workshop.config;

import com.workshop.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI workshopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Workshop Management API")
                        .version("1.0.0")
                        .description("REST API for browsing, registering for and managing student workshops. "
                                + "Log in via POST /api/auth/login, then click 'Authorize' and paste the token "
                                + "(without the 'Bearer ' prefix)."))
                .servers(List.of(new Server().url("/").description("Current server")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT obtained from /api/auth/login or /api/auth/register")));
    }

    /** Registers the shared ErrorResponse schema so every operation can reference it. */
    @Bean
    public OpenApiCustomizer errorSchemaCustomizer() {
        return openApi -> ModelConverters.getInstance().readAll(ErrorResponse.class)
                .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
    }

    /** Adds the generic error responses (400/401/403/500) to every operation that does not declare them. */
    @Bean
    public OperationCustomizer defaultErrorResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }
            boolean secured = handlerMethod.hasMethodAnnotation(SecurityRequirement.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(SecurityRequirement.class);
            addIfAbsent(responses, "400", "Validation failed or malformed request");
            if (secured) {
                addIfAbsent(responses, "401", "Missing, invalid or expired JWT");
                addIfAbsent(responses, "403", "Authenticated but not allowed (requires a different role)");
            }
            addIfAbsent(responses, "500", "Unexpected server error");
            return operation;
        };
    }

    private static void addIfAbsent(ApiResponses responses, String code, String description) {
        if (responses.containsKey(code)) {
            return;
        }
        Schema<?> ref = new Schema<>().$ref("#/components/schemas/ErrorResponse");
        responses.addApiResponse(code, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType().schema(ref))));
    }
}
