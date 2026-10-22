package clothing.app.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * OpenAPI docs (springdoc-openapi). Enabled only in local/dev per ADR;
 * disabled elsewhere via springdoc.api-docs.enabled / swagger-ui.enabled.
 *
 * The bearerAuth scheme lets Swagger UI send the access token returned by
 * /auth/login or /auth/register, so protected endpoints can be tested from the UI.
 */
@Configuration
@Profile({"local", "dev"})
public class OpenApiConfig {

	@Bean
	public OpenAPI clothingOpenAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("Clothing Shopping API")
				.version("0.0.1")
				.description("Clothing shopping application backend"))
			.components(new Components()
				.addSecuritySchemes("bearerAuth", new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("Access token from /auth/login or /auth/register")));
	}
}
