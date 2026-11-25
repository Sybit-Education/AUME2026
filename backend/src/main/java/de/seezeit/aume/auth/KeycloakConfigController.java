package de.seezeit.aume.auth;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirements
@Profile("!dev")
public class KeycloakConfigController {
    private final Config config;

    public KeycloakConfigController(@Value("${KEYCLOAK_URL}") String url,
            @Value("${KEYCLOAK_REALM}") String realm, @Value("${KEYCLOAK_CLIENT_ID}") String clientId) {
        config = new Config("keycloak", url, realm, clientId);
    }

    @GetMapping("/api/auth/config")
    public Config config() {
        return config;
    }

    public record Config(String mode, String url, String realm, String clientId) {}
}
