package de.seezeit.aume.inspection;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Vorgeschichte der WG in der Kontrolle (US-51). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-previous-findings-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PreviousFindingsTests {
    @Autowired private MockMvc mvc;
    @Autowired private InspectionRepository inspections;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;

    @Test
    void onlyCaretakersMaySeeThePreviousFindings() throws Exception {
        String path = previousOf(planned("S-01"));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String key : new String[]{"student", "clerk"}) {
            mvc.perform(get(path).session(login(key))).andExpect(status().isForbidden());
        }
    }

    @Test
    void plannedInspectionShowsTheEarlierInspectionsOfTheSameFlatNewestFirst() throws Exception {
        mvc.perform(get(previousOf(planned("S-01"))).session(login("caretaker")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            // Nachkontrolle vor 23 Tagen ohne Mängel, davor die Erstkontrolle vor 30 Tagen
            .andExpect(jsonPath("$[0].round").value(1))
            .andExpect(jsonPath("$[0].overallResult").value("PASSED"))
            .andExpect(jsonPath("$[0].defects.length()").value(0))
            .andExpect(jsonPath("$[1].round").value(0))
            .andExpect(jsonPath("$[1].overallResult").value("FAILED"))
            .andExpect(jsonPath("$[1].defects.length()").value(3))
            .andExpect(jsonPath("$[1].defects[0].area").value("Küche"))
            .andExpect(jsonPath("$[1].defects[0].criterionTitle").value("Herd & Backofen"))
            .andExpect(jsonPath("$[1].defects[0].severity").value("SEVERE"))
            .andExpect(jsonPath("$[1].defects[0].note").isNotEmpty())
            .andExpect(jsonPath("$[1].defects[2].rating").value("RESOLVED_ON_SITE"));

        mvc.perform(get(previousOf(planned("S-02"))).session(login("caretaker")))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].defects.length()").value(0));
    }

    @Test
    void aFinishedInspectionOnlyShowsWhatCameBeforeIt() throws Exception {
        var session = login("caretaker");
        Long initial = finished("S-01", 0);
        Long followUp = finished("S-01", 1);
        mvc.perform(get(previousOf(initial)).session(session)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(previousOf(followUp)).session(session))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(initial));
    }

    @Test
    void atMostThreeEarlierInspectionsAreShown() throws Exception {
        var flat = inspections.findById(planned("S-01")).orElseThrow().getFlat();
        for (int days = 40; days <= 70; days += 10) {
            var old = new InspectionEntity(flat, null);
            old.setStatus(InspectionEntity.Status.FINALIZED);
            old.setOverallResult(InspectionEntity.Result.PASSED);
            old.setInspectedAt(Instant.now().minus(Duration.ofDays(days)));
            inspections.save(old);
        }
        mvc.perform(get(previousOf(planned("S-01"))).session(login("caretaker")))
            .andExpect(jsonPath("$.length()").value(PreviousFindingsService.LIMIT))
            .andExpect(jsonPath("$[2].overallResult").value("PASSED"));
    }

    @Test
    void startedInspectionKeepsItsPreviousFindings() throws Exception {
        var session = login("caretaker");
        Long id = planned("S-01");
        mvc.perform(post("/api/inspections/" + id + "/start").session(session).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(get(previousOf(id)).session(session)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void inspectionsOfOtherResidencesAndUnknownIdsAreNotFound() throws Exception {
        var other = residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var foreign = inspections.save(new InspectionEntity(flats.save(new FlatEntity(other, "1.01", 2)), null));
        var session = login("caretaker");
        mvc.perform(get(previousOf(foreign.getId())).session(session)).andExpect(status().isNotFound());
        mvc.perform(get(previousOf(999_999L)).session(session)).andExpect(status().isNotFound());
    }

    private Long planned(String label) {
        return inspections.findAll().stream()
            .filter(i -> i.getFlat().getLabel().equals(label) && i.getStatus() == InspectionEntity.Status.PLANNED)
            .findFirst().orElseThrow().getId();
    }

    private Long finished(String label, int round) {
        return inspections.findAll().stream()
            .filter(i -> i.getFlat().getLabel().equals(label) && InspectionHistoryService.DONE.contains(i.getStatus())
                && i.getRound() == round)
            .findFirst().orElseThrow().getId();
    }

    private static String previousOf(Long id) {
        return "/api/inspections/" + id + "/previous-findings";
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
