package de.seezeit.aume.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI seezeitOpenApi(Environment environment) {
        boolean development = environment.acceptsProfiles(Profiles.of("dev"));
        String schemeName = development ? "devSession" : "bearerAuth";
        var scheme = development
            ? new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE).name("JSESSIONID")
                .description("Anmeldung über /api/auth/dev-login mit CSRF-Token.")
            : new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
        return new OpenAPI()
            .components(new Components().addSecuritySchemes(schemeName, scheme))
            .addSecurityItem(new SecurityRequirement().addList(schemeName))
            .info(new Info().title("Seezeit API").version("v1")
                .description("REST-API. Listen sind paginiert (?page=0&size=20). "
                    + "401: nicht angemeldet; 403: fehlende Berechtigung oder ungültiges CSRF-Token."));
    }
}
