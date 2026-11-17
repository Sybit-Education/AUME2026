package de.seezeit.aume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Set;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:aume-production-tests;DB_CLOSE_DELAY=-1",
    "KEYCLOAK_URL=https://identity.example.org", "KEYCLOAK_REALM=seezeit", "KEYCLOAK_CLIENT_ID=seezeit-frontend"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ProductionSecurityTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ResidenceRepository residences;
    @Autowired private RoleRepository roles;
    @Autowired private ApplicationContext context;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void mockLoginAndDemoDataAreAbsentOutsideDevelopment() throws Exception {
        assertThat(context.getBeansOfType(SampleData.class)).isEmpty();
        assertThat(context.getBeansOfType(de.seezeit.aume.auth.DevAuthController.class)).isEmpty();
        assertThat(users.count()).isZero();
        assertThat(residences.count()).isZero();
        mvc.perform(get("/api/auth/config")).andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("keycloak"));
        mvc.perform(post("/api/auth/dev-login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"userKey\":\"student\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isUnauthorized());
    }

    @Test
    void verifiedIdentityReceivesDatabaseAuthoritiesRatherThanTokenRoles() throws Exception {
        var residence = residences.save(new ResidenceEntity("Assigned residence"));
        var user = new UserEntity("real-student", "Real student",
            Set.of(roles.findByName("STUDENT").orElseThrow()), residence);
        user.linkIdentity("https://identity.example.org/realms/seezeit", "subject-123");
        users.save(user);
        when(decoder.decode("valid-token")).thenReturn(token("subject-123"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userKey").value("real-student"))
            .andExpect(jsonPath("$.roles[0]").value("STUDENT"));
        mvc.perform(get("/api/greeting").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/residences").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/dev-login").header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"clerk\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void unprovisionedAndInvalidIdentitiesAreRejected() throws Exception {
        when(decoder.decode("unknown-user")).thenReturn(token("unprovisioned"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer unknown-user"))
            .andExpect(status().isUnauthorized());
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid token"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    private Jwt token(String subject) {
        return Jwt.withTokenValue("valid-token").header("alg", "RS256")
            .issuer("https://identity.example.org/realms/seezeit").subject(subject)
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
            .claim("roles", Set.of("CLERK")).build();
    }
}
