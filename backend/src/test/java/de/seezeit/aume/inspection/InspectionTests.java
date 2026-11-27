package de.seezeit.aume.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import de.seezeit.aume.InspectionSampleData;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.planning.InspectionPeriodRepository;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.standard.ChecklistVersionRepository;
import de.seezeit.aume.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-inspection-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class InspectionTests {
    @Autowired private MockMvc mvc;
    @Autowired private InspectionRepository inspections;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private ChecklistVersionRepository versions;
    @Autowired private InspectionSampleData sampleData;
    @Autowired private InspectionPeriodRepository periods;
    @Autowired private UserRepository users;

    @Test
    void onlyCaretakersMayPerformInspections() throws Exception {
        mvc.perform(get("/api/inspections")).andExpect(status().isUnauthorized());
        for (String key : new String[]{"student", "clerk"}) {
            mvc.perform(get("/api/inspections").session(login(key))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/inspections").session(login("caretaker")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].flatLabel").value("S-01"))
            .andExpect(jsonPath("$.content[0].status").value("PLANNED"));
    }

    @Test
    void caretakerWorksThroughTheChecklistAndFinalizes() throws Exception {
        var session = login("caretaker");
        Long id = firstInspection();
        mvc.perform(get("/api/inspections/" + id).session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PLANNED"))
            .andExpect(jsonPath("$.capacity").value(4))
            .andExpect(jsonPath("$.criteria.length()").value(10))
            .andExpect(jsonPath("$.criteria[0].area").value("Küche"));
        mvc.perform(put("/api/inspections/" + id + "/findings/1").session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"OK\"}"))
            .andExpect(status().isConflict());

        String started = mvc.perform(post("/api/inspections/" + id + "/start").session(session).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn().getResponse().getContentAsString();
        List<Integer> criteria = JsonPath.read(started, "$.criteria[*].id");
        Integer stove = criteria.get(1);

        mvc.perform(put("/api/inspections/" + id + "/findings/" + stove).session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"DEFICIENT\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/inspections/" + id + "/findings/" + stove).session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"rating\":\"DEFICIENT\",\"severity\":\"SEVERE\",\"note\":\"Fettschicht\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.findings.length()").value(1))
            .andExpect(jsonPath("$.findings[0].severity").value("SEVERE"));

        mvc.perform(post("/api/inspections/" + id + "/mark-ok").session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"area\":\"Küche\"}"))
            .andExpect(jsonPath("$.findings.length()").value(4));
        mvc.perform(post("/api/inspections/" + id + "/finalize").session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"overallResult\":\"FAILED\"}"))
            .andExpect(status().isConflict());

        mvc.perform(post("/api/inspections/" + id + "/mark-ok").session(session).with(csrf()))
            .andExpect(jsonPath("$.findings.length()").value(10))
            .andExpect(jsonPath("$.findings[1].rating").value("DEFICIENT"));
        mvc.perform(post("/api/inspections/" + id + "/finalize").session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"overallResult\":\"FAILED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.overallResult").value("FAILED"));
        mvc.perform(delete("/api/inspections/" + id + "/findings/" + stove).session(session).with(csrf()))
            .andExpect(status().isConflict());
    }

    @Test
    void openListShowsOnlyAnnouncedPeriodsAndInspectionsWithoutPeriod() throws Exception {
        var sonne = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
        var clerk = users.findByUserKey("clerk").orElseThrow();
        var proposed = periods.save(new InspectionPeriodEntity(sonne, LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 12), clerk));
        var announced = new InspectionPeriodEntity(sonne, LocalDate.of(2027, 1, 3), LocalDate.of(2027, 1, 5), clerk);
        announced.setWindowStart(LocalTime.of(9, 0));
        announced.setWindowEnd(LocalTime.of(16, 0));
        announced.setAnnouncedAt(Instant.now());
        periods.save(announced);
        var waiting = inspections.save(new InspectionEntity(flats.save(new FlatEntity(sonne, "S-03", 2)), proposed));
        inspections.save(new InspectionEntity(flats.save(new FlatEntity(sonne, "S-04", 2)), announced));

        var session = login("caretaker");
        // S-01 und S-02 haben keinen Zeitraum und bleiben sichtbar, S-03 wartet noch auf die Ankündigung.
        mvc.perform(get("/api/inspections").session(session))
            .andExpect(jsonPath("$.page.totalElements").value(3))
            .andExpect(jsonPath("$.content[?(@.flatLabel == 'S-03')]").isEmpty())
            .andExpect(jsonPath("$.content[?(@.flatLabel == 'S-04')].startsOn").value("2027-01-03"))
            .andExpect(jsonPath("$.content[?(@.flatLabel == 'S-04')].endsOn").value("2027-01-05"))
            .andExpect(jsonPath("$.content[?(@.flatLabel == 'S-04')].residenceName").value("Wohnanlage Sonne"))
            .andExpect(jsonPath("$.content[?(@.flatLabel == 'S-04')].windowStart").value("09:00:00"))
            // Ohne Zeitraum ganz unten
            .andExpect(jsonPath("$.content[2].flatLabel").value("S-02"))
            .andExpect(jsonPath("$.content[2].startsOn").isEmpty());

        // Vor Beginn des Zeitraums lässt sich die angekündigte Kontrolle noch nicht starten.
        var future = inspections.findAll().stream().filter(i -> i.getFlat().getLabel().equals("S-04")).findFirst().orElseThrow();
        mvc.perform(post("/api/inspections/" + future.getId() + "/start").session(session).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("period_not_started"));

        // Eine schon begonnene Kontrolle bleibt sichtbar, auch wenn ihr Zeitraum nicht angekündigt ist.
        waiting.setStatus(InspectionEntity.Status.DRAFT);
        inspections.saveAndFlush(waiting);
        mvc.perform(get("/api/inspections").session(session))
            .andExpect(jsonPath("$.page.totalElements").value(4))
            .andExpect(jsonPath("$.content[?(@.flatLabel == 'S-03')].status").value("DRAFT"));
    }

    @Test
    void inspectionsOfOtherResidencesAreInvisible() throws Exception {
        var other = residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var foreign = inspections.save(new InspectionEntity(flats.save(new FlatEntity(other, "1.01", 2)), null));
        var session = login("caretaker");
        mvc.perform(get("/api/inspections/" + foreign.getId()).session(session)).andExpect(status().isNotFound());
        mvc.perform(post("/api/inspections/" + foreign.getId() + "/start").session(session).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/inspections").session(session)).andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void noteLimitAppliesToTheStoredText() throws Exception {
        var session = login("caretaker");
        Long id = firstInspection();
        Integer criterion = startAndFirstCriterion(session, id);
        String padded = "a".repeat(1990) + " ".repeat(20);
        mvc.perform(rate(session, id, criterion,
                "{\"rating\":\"DEFICIENT\",\"severity\":\"LIGHT\",\"note\":\"" + padded + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.findings[0].note").value("a".repeat(1990)));
        mvc.perform(rate(session, id, criterion,
                "{\"rating\":\"DEFICIENT\",\"severity\":\"LIGHT\",\"note\":\"" + "a".repeat(2001) + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void buildingCauseIsOnlyKeptForDefects() throws Exception {
        var session = login("caretaker");
        Long id = firstInspection();
        Integer criterion = startAndFirstCriterion(session, id);
        mvc.perform(rate(session, id, criterion, "{\"rating\":\"OK\",\"buildingCauseSuspected\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.findings[0].buildingCauseSuspected").value(false));
        mvc.perform(rate(session, id, criterion,
                "{\"rating\":\"DEFICIENT\",\"severity\":\"LIGHT\",\"note\":\"Riss\",\"buildingCauseSuspected\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.findings[0].buildingCauseSuspected").value(true));
    }

    @Test
    void emptyChecklistCanNeitherBeStartedNorFinalized() throws Exception {
        var session = login("caretaker");
        Long id = firstInspection();
        var current = versions.findFirstByStatusOrderByVersionNoDesc(ChecklistVersionEntity.Status.PUBLISHED)
            .orElseThrow();
        var empty = new ChecklistVersionEntity(current.getVersionNo() + 1, current.getCreatedBy());
        empty.setStatus(ChecklistVersionEntity.Status.PUBLISHED);
        versions.save(empty);

        mvc.perform(post("/api/inspections/" + id + "/start").session(session).with(csrf()))
            .andExpect(status().isConflict());

        // Auch eine bereits an eine leere Fassung gebundene Kontrolle darf nicht ohne Bewertung abschließen.
        var inspection = inspections.findById(id).orElseThrow();
        inspection.setChecklistVersion(empty);
        inspection.setStatus(InspectionEntity.Status.DRAFT);
        inspections.flush();
        mvc.perform(post("/api/inspections/" + id + "/finalize").session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"overallResult\":\"PASSED\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void sampleDataIsIdempotent() {
        sampleData.run();
        assertThat(versions.count()).isEqualTo(1);
        // 5 aus InspectionSampleData, 2 aus dem Beispiel-Gegenvorschlag in InspectionPlanningSampleData,
        // 1 veröffentlichte Erstkontrolle aus StudentSampleData (US-38), 1 geplante in Petershausen (US-03)
        assertThat(inspections.count()).isEqualTo(9);
    }

    private Integer startAndFirstCriterion(MockHttpSession session, Long id) throws Exception {
        String started = mvc.perform(post("/api/inspections/" + id + "/start").session(session).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> criteria = JsonPath.read(started, "$.criteria[*].id");
        return criteria.get(0);
    }

    private RequestBuilder rate(MockHttpSession session, Long id, Integer criterion, String body) {
        return put("/api/inspections/" + id + "/findings/" + criterion).session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /**
     * Geplante Kontrolle von S-01, startbereit: Die Studierenden-Testdaten hängen sie an einen angekündigten
     * Zeitraum in der Zukunft; vor dessen Beginn ließe sie sich nicht starten (period_not_started).
     */
    private Long firstInspection() {
        var inspection = inspections.findAll().stream()
            .filter(i -> i.getFlat().getLabel().equals("S-01") && i.getStatus() == InspectionEntity.Status.PLANNED)
            .findFirst().orElseThrow();
        var period = inspection.getPeriod();
        if (period != null && period.getStartsOn().isAfter(LocalDate.now().minusDays(1))) {
            period.setStartsOn(LocalDate.now().minusDays(1));
            periods.saveAndFlush(period);
        }
        return inspection.getId();
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
