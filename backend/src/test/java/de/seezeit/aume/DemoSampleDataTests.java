package de.seezeit.aume;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Die Demo-Daten zeigen je einen Fall für Anfragen, „ab TT.MM.“ und „Kontrolle starten“. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-demo-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "demo"})
class DemoSampleDataTests {
    @Autowired private MockMvc mvc;

    @Test
    void caretakerSeesOneRequestAndTwoAnnouncedInspections() throws Exception {
        var session = login("caretaker");
        var today = LocalDate.now(ZoneId.of("Europe/Berlin"));

        mvc.perform(get("/api/inspection-periods/assigned").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].status").value("WAITING_FOR_CARETAKER"))
            .andExpect(jsonPath("$.content[0].startsOn").value(today.plusDays(14).toString()));

        // Anstehend: zuerst der laufende Zeitraum (S-05), dann der zukünftige (S-04), dann die Kontrollen ohne Zeitraum.
        mvc.perform(get("/api/inspections").session(session))
            .andExpect(jsonPath("$.page.totalElements").value(4))
            .andExpect(jsonPath("$.content[0].flatLabel").value("S-05"))
            .andExpect(jsonPath("$.content[0].startsOn").value(today.minusDays(1).toString()))
            .andExpect(jsonPath("$.content[1].flatLabel").value("S-04"))
            .andExpect(jsonPath("$.content[1].startsOn").value(today.plusDays(7).toString()));
    }

    @Test
    void onlyTheRunningPeriodCanBeStarted() throws Exception {
        var session = login("caretaker");
        var content = mvc.perform(get("/api/inspections").session(session)).andReturn().getResponse().getContentAsString();
        Integer running = com.jayway.jsonpath.JsonPath.read(content, "$.content[0].id");
        Integer future = com.jayway.jsonpath.JsonPath.read(content, "$.content[1].id");

        mvc.perform(post("/api/inspections/" + future + "/start").session(session).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("period_not_started"));
        mvc.perform(post("/api/inspections/" + running + "/start").session(session).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    private MockHttpSession login(String userKey) throws Exception {
        var session = new MockHttpSession();
        mvc.perform(post("/api/auth/dev-login").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + userKey + "\"}"))
            .andExpect(status().isOk());
        return session;
    }
}
