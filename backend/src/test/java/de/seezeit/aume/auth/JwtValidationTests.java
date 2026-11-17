package de.seezeit.aume.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwtValidationTests {
    private static final String ISSUER = "https://identity.example.org/realms/seezeit";
    private static RSAKey key;
    private static NimbusJwtDecoder decoder;

    @BeforeAll
    static void configureDecoder() throws JOSEException {
        key = new RSAKeyGenerator(2048).generate();
        decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(SecurityConfig.tokenValidator(ISSUER, "seezeit-api"));
    }

    @Test
    void acceptsAValidSignedAccessToken() throws JOSEException {
        assertThat(decoder.decode(sign(claims().build(), key)).getSubject()).isEqualTo("subject-123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "missing-audience", "expired", "not-yet-valid", "signature"})
    void rejectsInvalidAccessTokens(String fault) throws JOSEException {
        var claims = claims();
        var signingKey = key;
        switch (fault) {
            case "issuer" -> claims.issuer("https://another-issuer.example.org");
            case "audience" -> claims.audience("another-api");
            case "missing-audience" -> claims.audience((String) null);
            case "expired" -> claims.expirationTime(Date.from(Instant.now().minusSeconds(300)));
            case "not-yet-valid" -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(300)));
            case "signature" -> signingKey = new RSAKeyGenerator(2048).generate();
            default -> throw new IllegalArgumentException(fault);
        }
        String token = sign(claims.build(), signingKey);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(ISSUER).subject("subject-123").audience("seezeit-api")
            .issueTime(Date.from(Instant.now().minusSeconds(600)))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }

    private static String sign(JWTClaimsSet claims, RSAKey signingKey) throws JOSEException {
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}
