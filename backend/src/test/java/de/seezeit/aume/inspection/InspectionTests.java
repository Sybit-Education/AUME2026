package de.seezeit.aume.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import de.seezeit.aume.InspectionSampleData;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.standard.ChecklistVersionRepository;
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
        // 1 veröffentlichte Erstkontrolle aus StudentSampleData (US-38)
        assertThat(inspections.count()).isEqualTo(8);
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

    private Long firstInspection() {
        return inspections.findAll().stream()
            .filter(i -> i.getFlat().getLabel().equals("S-01") && i.getStatus() == InspectionEntity.Status.PLANNED)
            .findFirst().orElseThrow().getId();
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
