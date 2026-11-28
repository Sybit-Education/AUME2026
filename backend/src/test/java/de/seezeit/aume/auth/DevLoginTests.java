package de.seezeit.aume.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-dev-login;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DevLoginTests {
    @Autowired private MockMvc mvc;

    @Test
    void devLoginAcceptsOnlyTheFourTestUsers() throws Exception {
        for (String key : new String[]{"student", "caretaker", "caretaker2", "clerk"}) {
            mvc.perform(post("/api/auth/dev-login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userKey\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userKey").value(key));
        }
        mvc.perform(post("/api/auth/dev-login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userKey\":\"caretaker3\"}"))
            .andExpect(status().isUnauthorized());
    }
}
