package de.seezeit.aume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.seezeit.aume.auth.DevAuthController;
import de.seezeit.aume.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:aume-prod-users;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Transactional
class ProdTestUsersTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ApplicationContext context;

    @Test
    void prodSeedsOnlyTheFourTestUsersAndKeycloak() throws Exception {
        assertThat(context.getBeansOfType(SampleData.class)).isEmpty();
        assertThat(context.getBeansOfType(DevAuthController.class)).isEmpty();
        assertThat(users.count()).isEqualTo(4);
        assertThat(users.findByUserKey("caretaker2").orElseThrow().getIdentitySubject())
            .isEqualTo("00000000-0000-4000-8000-000000000004");
        mvc.perform(get("/api/auth/config")).andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("keycloak"));
    }
}
