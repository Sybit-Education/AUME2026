package de.seezeit.aume.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-result-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class InspectionResultTests {
    @Autowired private MockMvc mvc;
    @Autowired private InspectionRepository inspections;
    @Autowired private de.seezeit.aume.planning.InspectionPeriodRepository periods;
    @Autowired private InspectionResultService results;

    @Test
    void finishingAnInspectionPublishesItForTheFlat() throws Exception {
        var inspection = inspections.findAll().stream()
            .filter(i -> i.getFlat().getLabel().equals("S-01") && i.getStatus() == InspectionEntity.Status.PLANNED)
            .findFirst().orElseThrow();
        Long id = inspection.getId();
        Long flatId = inspection.getFlat().getId();
        assertThat(results.resultsForFlat(flatId)).noneMatch(r -> r.inspectionId().equals(id));
        // Die Studierenden-Testdaten kündigen S-01 für Dezember an; starten geht erst ab Beginn des Zeitraums.
        var period = inspection.getPeriod();
        if (period != null && period.getStartsOn().isAfter(java.time.LocalDate.now().minusDays(1))) {
            period.setStartsOn(java.time.LocalDate.now().minusDays(1));
            periods.saveAndFlush(period);
        }

        var session = login("caretaker");
        String started = mvc.perform(post("/api/inspections/" + id + "/start").session(session).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(results.resultsForFlat(flatId)).as("drafts stay hidden").noneMatch(r -> r.inspectionId().equals(id));

        List<Integer> criteria = JsonPath.read(started, "$.criteria[*].id");
        mvc.perform(put("/api/inspections/" + id + "/findings/" + criteria.get(1)).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"DEFICIENT\",\"severity\":\"SEVERE\",\"note\":\"Fettschicht am Herd\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/inspections/" + id + "/mark-ok").session(session).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(post("/api/inspections/" + id + "/finalize").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"overallResult\":\"FAILED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(post("/api/inspections/" + id + "/finalize").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"overallResult\":\"PASSED\"}"))
            .andExpect(status().isConflict());

        var stored = inspections.findById(id).orElseThrow();
        assertThat(stored.getPublishedBy().getUserKey()).isEqualTo("caretaker");
        assertThat(stored.getPublishedAt()).isNotNull();

        var result = results.resultsForFlat(flatId).getFirst();
        assertThat(result.inspectionId()).isEqualTo(id);
        assertThat(result.flatLabel()).isEqualTo("S-01");
        assertThat(result.overallResult()).isEqualTo(InspectionEntity.Result.FAILED);
        assertThat(result.criteria()).hasSize(10);
        assertThat(result.findings()).hasSize(10).anySatisfy(finding -> {
            assertThat(finding.rating()).isEqualTo(FindingEntity.Rating.DEFICIENT);
            assertThat(finding.note()).isEqualTo("Fettschicht am Herd");
        });
    }

    @Test
    void resultsAreLimitedToTheFlatAndTheRequestedPeriod() {
        var flat = inspections.findAll().stream()
            .filter(i -> i.getFlat().getLabel().equals("S-01") && i.getStatus() == InspectionEntity.Status.PUBLISHED)
            .findFirst().orElseThrow().getFlat();
        var forFlat = results.resultsForFlat(flat.getId());
        assertThat(forFlat).isNotEmpty().allMatch(r -> r.flatId().equals(flat.getId()));
        assertThat(forFlat).extracting(InspectionResultService.ResidentResult::publishedAt)
            .isSortedAccordingTo((a, b) -> b.compareTo(a));
        assertThat(forFlat).extracting(InspectionResultService.ResidentResult::round)
            .as("Erst- und Nachkontrolle").contains(0, 1);
        assertThat(results.resultsForFlat(-1L)).isEmpty();
    }

    @Test
    void notBeforeIncludesResultsPublishedExactlyAtThatTime() {
        var flat = inspections.findAll().stream()
            .filter(i -> i.getFlat().getLabel().equals("S-01") && i.getStatus() == InspectionEntity.Status.PUBLISHED)
            .findFirst().orElseThrow().getFlat();
        var newest = results.resultsForFlat(flat.getId()).getFirst();
        assertThat(results.resultsForFlat(flat.getId(), newest.publishedAt()))
            .extracting(InspectionResultService.ResidentResult::inspectionId).containsExactly(newest.inspectionId());
        assertThat(results.resultsForFlat(flat.getId(), newest.publishedAt().plusMillis(1))).isEmpty();
        assertThat(results.resultsForFlat(flat.getId(), Instant.now().plus(1, ChronoUnit.DAYS))).isEmpty();
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
