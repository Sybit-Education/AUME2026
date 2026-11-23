package de.seezeit.aume.standard;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-checklist-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ResidentChecklistTests {
    @Autowired private MockMvc mvc;
    @Autowired private ChecklistVersionRepository versions;
    @Autowired private CriterionTranslationRepository translations;

    @Test
    void studentReadsThePublishedChecklistGroupedByAreaInGerman() throws Exception {
        mvc.perform(get("/api/checklist").session(login("student")).header("Accept-Language", "de"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Language", "de"))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.areas.length()").value(4))
            .andExpect(jsonPath("$.areas[0].name").value("Küche"))
            .andExpect(jsonPath("$.areas[0].criteria[0].title").value("Arbeitsflächen"))
            .andExpect(jsonPath("$.areas[0].criteria[0].description").isNotEmpty())
            .andExpect(jsonPath("$.areas[3].name").value("Bad"));
    }

    @Test
    void studentReadsTheEnglishTranslation() throws Exception {
        mvc.perform(get("/api/checklist").session(login("student")).header("Accept-Language", "en-US"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Language", "en"))
            .andExpect(jsonPath("$.areas[0].name").value("Kitchen"))
            .andExpect(jsonPath("$.areas[0].criteria[0].title").value("Work surfaces"));
    }

    @Test
    void missingTranslationsFallBackToGerman() throws Exception {
        translations.deleteAll();
        mvc.perform(get("/api/checklist").session(login("student")).header("Accept-Language", "en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.areas[0].name").value("Küche"));
    }

    @Test
    void unpublishedChecklistReturnsAStableErrorCode() throws Exception {
        versions.findAll().forEach(version -> version.setStatus(ChecklistVersionEntity.Status.DRAFT));
        mvc.perform(get("/api/checklist").session(login("student")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("checklist_not_published"));
    }

    @Test
    void anonymousAndUnprivilegedUsersCannotReadTheChecklist() throws Exception {
        mvc.perform(get("/api/checklist")).andExpect(status().isUnauthorized());
        var session = login("caretaker");
        mvc.perform(get("/api/me").session(session))
            .andExpect(jsonPath("$.privileges", not(hasItem("CHECKLIST_READ"))));
        mvc.perform(get("/api/checklist").session(session)).andExpect(status().isForbidden());
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
