package de.seezeit.aume.auth;

import java.time.Instant;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration
public class TestJwtDecoder {
    private static final String ISSUER = "http://localhost:8081/realms/seezeit";
    private static final Map<String, String> SUBJECTS = Map.of(
        "student", "00000000-0000-4000-8000-000000000001",
        "clerk", "00000000-0000-4000-8000-000000000002",
        "caretaker", "00000000-0000-4000-8000-000000000003",
        "caretaker2", "00000000-0000-4000-8000-000000000004");

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token).header("alg", "none").issuer(ISSUER)
            .subject(SUBJECTS.getOrDefault(token, token)).issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300)).build();
    }
}
