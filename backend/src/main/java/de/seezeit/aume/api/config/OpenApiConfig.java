package de.seezeit.aume.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI seezeitOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Seezeit API")
				.version("v1")
				.description("REST-API. Listen sind paginiert, zum Beispiel ?page=0&size=20."));
	}

}
