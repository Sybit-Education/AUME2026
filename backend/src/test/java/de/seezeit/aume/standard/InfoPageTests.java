package de.seezeit.aume.standard;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import de.seezeit.aume.auth.TestJwtDecoder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-info-page-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestJwtDecoder.class)
@Transactional
class InfoPageTests {
    private static final String VALID = "{\"process\":\"Neu A\",\"consequences\":\"Neu B\",\"standards\":\"Neu C\"}";

    @Autowired private MockMvc mvc;

    @Test
    void residentsReadTheVersionSavedByTheClerk() throws Exception {
        mvc.perform(put("/api/info-pages").with(login("clerk")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.process").value("Neu A"));

        mvc.perform(get("/api/info-pages").with(login("student")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.process").value("Neu A"))
            .andExpect(jsonPath("$.consequences").value("Neu B"))
            .andExpect(jsonPath("$.standards").value("Neu C"))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andExpect(jsonPath("$.updatedBy").doesNotExist());
    }

    @Test
    void englishVersionIsStoredSeparately() throws Exception {
        mvc.perform(put("/api/info-pages").session(login("clerk")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(
                    "{\"process\":\"A\",\"consequences\":\"B\",\"standards\":\"C\","
                        + "\"processEn\":\"A en\",\"consequencesEn\":\"B en\",\"standardsEn\":\"C en\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/info-pages").session(login("student")))
            .andExpect(jsonPath("$.process").value("A"))
            .andExpect(jsonPath("$.processEn").value("A en"))
            .andExpect(jsonPath("$.consequencesEn").value("B en"))
            .andExpect(jsonPath("$.standardsEn").value("C en"));
    }

    @Test
    void residentsCanReadButNotSave() throws Exception {
        var session = login("student");
        mvc.perform(get("/api/info-pages").with(session)).andExpect(status().isOk());
        mvc.perform(put("/api/info-pages").with(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/info-pages").with(session))
            .andExpect(jsonPath("$.process").value(not("Neu A")));
    }

    @Test
    void caretakersHaveNoAccess() throws Exception {
        var session = login("caretaker");
        mvc.perform(get("/api/me").with(session))
            .andExpect(jsonPath("$.privileges", not(hasItem("STANDARDS_VIEW"))));
        mvc.perform(get("/api/info-pages").with(session)).andExpect(status().isForbidden());
        mvc.perform(put("/api/info-pages").with(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousAccessAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(get("/api/info-pages")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/info-pages").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTextsAreRejectedWithoutPartialChanges() throws Exception {
        var session = login("clerk");
        var before = mvc.perform(get("/api/info-pages").with(session)).andReturn().getResponse().getContentAsString();

        mvc.perform(put("/api/info-pages").with(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"process\":\"Neu A\",\"consequences\":\"   \",\"standards\":\"Neu C\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("info_text_required"));
        mvc.perform(put("/api/info-pages").with(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"process\":\"Neu A\",\"consequences\":\"Neu B\",\"standards\":\"" + "x".repeat(20001) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("info_text_too_long"));

        mvc.perform(get("/api/info-pages").with(session)).andExpect(content().json(before));
    }

    private RequestPostProcessor login(String key) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + key);
            return request;
        };
    }
}
