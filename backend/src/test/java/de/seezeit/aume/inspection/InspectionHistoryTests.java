package de.seezeit.aume.inspection;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import java.time.Instant;
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
import org.springframework.transaction.annotation.Transactional;

/** Kontroll-Historie (#40, #42) und Detailansicht abgeschlossener Kontrollen (#43). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-inspection-history-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class InspectionHistoryTests {
    private static final String HISTORY = "/api/inspections/history";

    @Autowired private MockMvc mvc;
    @Autowired private InspectionRepository inspections;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;

    @Test
    void onlyCaretakersMaySeeTheHistory() throws Exception {
        mvc.perform(get(HISTORY)).andExpect(status().isUnauthorized());
        for (String key : new String[]{"student", "clerk"}) {
            mvc.perform(get(HISTORY).session(login(key))).andExpect(status().isForbidden());
        }
    }

    @Test
    void historyListsFinishedInspectionsNewestFirst() throws Exception {
        mvc.perform(get(HISTORY).session(login("caretaker")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(3))
            // Nachkontrolle S-01 (vor 23 Tagen), S-02 (vor 28 Tagen), Erstkontrolle S-01 (vor 30 Tagen)
            .andExpect(jsonPath("$.content[0].flatLabel").value("S-01"))
            .andExpect(jsonPath("$.content[0].round").value(1))
            .andExpect(jsonPath("$.content[0].overallResult").value("PASSED"))
            .andExpect(jsonPath("$.content[1].flatLabel").value("S-02"))
            .andExpect(jsonPath("$.content[2].overallResult").value("FAILED"))
            .andExpect(jsonPath("$.content[2].deficiencies").value(2))
            .andExpect(jsonPath("$.content[2].inspectorName").isNotEmpty())
            .andExpect(jsonPath("$.content[2].status").value("FINALIZED"));
    }

    @Test
    void historyCanBeFiltered() throws Exception {
        var session = login("caretaker");
        expectTotal(session, "?flat=S-01", 2);
        expectTotal(session, "?flat=01", 2);
        expectTotal(session, "?result=FAILED", 1);
        expectTotal(session, "?followUp=true", 1);
        expectTotal(session, "?followUp=false", 2);
        expectTotal(session, "?flat=S-01&followUp=false&result=FAILED", 1);

        var cutoff = LocalDate.now(ZoneId.of("Europe/Berlin")).minusDays(29);
        expectTotal(session, "?from=" + cutoff, 2);
        expectTotal(session, "?to=" + cutoff, 1);
        expectTotal(session, "?from=" + cutoff + "&to=" + cutoff, 0);
    }

    @Test
    void likeWildcardsInTheFlatFilterAreTakenLiterally() throws Exception {
        var session = login("caretaker");
        for (String wildcard : new String[]{"%", "_", "S_01", "S%"}) {
            mvc.perform(get(HISTORY).param("flat", wildcard).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
        }
        mvc.perform(get(HISTORY).param("flat", "  s-01 ").session(session))
            .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void invalidFiltersAreRejected() throws Exception {
        var session = login("caretaker");
        mvc.perform(get(HISTORY + "?from=2026-05-02&to=2026-05-01").session(session))
            .andExpect(status().isBadRequest());
        mvc.perform(get(HISTORY + "?result=MAYBE").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get(HISTORY + "?from=gestern").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get(HISTORY + "?flat=" + "x".repeat(81)).session(session)).andExpect(status().isBadRequest());
    }

    @Test
    void finishedInspectionsOfOtherResidencesAreInvisible() throws Exception {
        var other = residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var foreign = new InspectionEntity(flats.save(new FlatEntity(other, "1.01", 2)), null);
        foreign.setStatus(InspectionEntity.Status.FINALIZED);
        foreign.setOverallResult(InspectionEntity.Result.PASSED);
        foreign.setInspectedAt(Instant.now());
        inspections.save(foreign);

        var session = login("caretaker");
        expectTotal(session, "", 3);
        mvc.perform(get("/api/inspections/" + foreign.getId()).session(session)).andExpect(status().isNotFound());
    }

    @Test
    void detailShowsTheRecordedInspection() throws Exception {
        Long id = inspections.findAll().stream()
            .filter(i -> i.getStatus() == InspectionEntity.Status.FINALIZED
                && i.getOverallResult() == InspectionEntity.Result.FAILED)
            .findFirst().orElseThrow().getId();
        mvc.perform(get("/api/inspections/" + id).session(login("caretaker")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FINALIZED"))
            .andExpect(jsonPath("$.inspectedAt").isNotEmpty())
            .andExpect(jsonPath("$.inspectorName").isNotEmpty())
            .andExpect(jsonPath("$.criteria.length()").value(10))
            .andExpect(jsonPath("$.findings.length()").value(10))
            .andExpect(jsonPath("$.findings[?(@.rating == 'DEFICIENT')].note").isNotEmpty());
    }

    @Test
    void aFinalizedInspectionAppearsInTheHistory() throws Exception {
        var session = login("caretaker");
        Long id = inspections.findAll().stream()
            .filter(i -> i.getFlat().getLabel().equals("S-02") && i.getStatus() == InspectionEntity.Status.PLANNED)
            .findFirst().orElseThrow().getId();
        mvc.perform(post("/api/inspections/" + id + "/start").session(session).with(csrf()))
            .andExpect(status().isOk());
        expectTotal(session, "", 3);
        mvc.perform(post("/api/inspections/" + id + "/mark-ok").session(session).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(post("/api/inspections/" + id + "/finalize").session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"overallResult\":\"PASSED\"}"))
            .andExpect(status().isOk());

        mvc.perform(get(HISTORY).session(session))
            .andExpect(jsonPath("$.page.totalElements").value(4))
            .andExpect(jsonPath("$.content[0].id").value(id))
            .andExpect(jsonPath("$.content[0].deficiencies").value(0));
    }

    private void expectTotal(MockHttpSession session, String query, int total) throws Exception {
        mvc.perform(get(HISTORY + query).session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(total));
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
