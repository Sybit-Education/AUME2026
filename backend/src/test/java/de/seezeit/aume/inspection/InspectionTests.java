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
            .andExpect(jsonPath("$.content[0].flatLabel").value("4.12"))
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
            .andExpect(jsonPath("$.status").value("FINALIZED"))
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
    void sampleDataIsIdempotent() {
        sampleData.run();
        assertThat(versions.count()).isEqualTo(1);
        assertThat(inspections.count()).isEqualTo(2);
    }

    private Long firstInspection() {
        return inspections.findAll().stream().filter(i -> i.getFlat().getLabel().equals("4.12"))
            .findFirst().orElseThrow().getId();
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
