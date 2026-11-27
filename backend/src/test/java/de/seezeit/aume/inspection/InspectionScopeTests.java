package de.seezeit.aume.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.residence.StaffAssignmentRepository;
import de.seezeit.aume.residence.StaffKind;
import de.seezeit.aume.user.CaretakerEntity;
import de.seezeit.aume.user.RoleRepository;
import de.seezeit.aume.user.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * US-03: Die Objektbetreuung sieht nur Kontrollen der Anlagen, die sie laut {@code staff_assignments}
 * betreut oder vertritt. Startdaten: caretaker betreut Sonne; caretaker2 betreut Petershausen und
 * vertritt Sonne; caretaker3 betreut Egg und vertritt Petershausen.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-inspection-scope-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class InspectionScopeTests {
    @Autowired private MockMvc mvc;
    @Autowired private InspectionRepository inspections;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private InspectionService inspectionService;
    @Autowired private InspectionHistoryService history;
    @Autowired private StaffAssignmentRepository staff;

    @Test
    void deputyAlsoSeesTheResidenceSheDeputizesFor() throws Exception {
        Long sonne = plannedIn("Wohnanlage Sonne");
        Long petershausen = plannedIn("Petershausen");

        var deputy = login("caretaker2");
        List<Integer> listed = JsonPath.read(mvc.perform(get("/api/inspections").param("size", "100").session(deputy))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.content[*].id");
        assertThat(listed).contains(sonne.intValue(), petershausen.intValue());
        mvc.perform(get("/api/inspections/" + sonne).session(deputy))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.residenceName").value("Wohnanlage Sonne"));
        mvc.perform(post("/api/inspections/" + sonne + "/start").session(deputy).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(get("/api/inspections/" + petershausen + "/previous-findings").session(deputy))
            .andExpect(status().isOk());
    }

    @Test
    void residencesOutsideTheAssignmentStayInvisible() throws Exception {
        Long petershausen = plannedIn("Petershausen");

        var caretaker = login("caretaker");
        List<Integer> listed = JsonPath.read(mvc.perform(get("/api/inspections").param("size", "100").session(caretaker))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.content[*].id");
        assertThat(listed).doesNotContain(petershausen.intValue());
        mvc.perform(get("/api/inspections/" + petershausen).session(caretaker)).andExpect(status().isNotFound());
        mvc.perform(post("/api/inspections/" + petershausen + "/start").session(caretaker).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/inspections/" + petershausen + "/previous-findings").session(caretaker))
            .andExpect(status().isNotFound());
    }

    @Test
    void historyCoversEveryResidenceInCare() throws Exception {
        // Die abgeschlossenen Dev-Kontrollen liegen in Sonne: caretaker betreut, caretaker2 vertritt sie.
        String forDeputy = mvc.perform(get("/api/inspections/history").param("size", "100").session(login("caretaker2")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String forCaretaker = mvc.perform(get("/api/inspections/history").param("size", "100").session(login("caretaker")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> deputyIds = JsonPath.read(forDeputy, "$.content[*].id");
        List<Integer> caretakerIds = JsonPath.read(forCaretaker, "$.content[*].id");
        assertThat(caretakerIds).isNotEmpty();
        assertThat(deputyIds).containsAll(caretakerIds);
    }

    @Test
    void theOldResidenceFieldAloneNoLongerGrantsAccess() {
        // Nutzer mit Wohnanlage im alten Feld users.residence_id, aber ohne Eintrag in staff_assignments.
        var sonne = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
        users.save(new CaretakerEntity("caretaker-unassigned", "Ohne Zuständigkeit",
            Set.of(roles.findByName("CARETAKER").orElseThrow()), sonne));
        plannedIn("Wohnanlage Sonne");

        assertThat(inspectionService.listOpen("caretaker-unassigned", Pageable.ofSize(100))).isEmpty();
        assertThat(history.history("caretaker-unassigned", new InspectionHistoryService.HistoryFilter(
            null, null, null, null, null, null, null), new InspectionHistoryService.HistoryOrder(null, null),
            Pageable.ofSize(100))).isEmpty();
    }

    @Test
    void inspectorCanFinishHerDraftAfterLosingTheAssignment() throws Exception {
        Long draft = plannedIn("Wohnanlage Sonne");
        Long other = plannedIn("Wohnanlage Sonne");
        var caretaker = login("caretaker");
        mvc.perform(post("/api/inspections/" + draft + "/start").session(caretaker).with(csrf()))
            .andExpect(status().isOk());

        var caretakerId = users.findByUserKey("caretaker").orElseThrow().getId();
        staff.deleteAll(staff.findByUserIdAndKindIn(caretakerId, StaffKind.CARETAKING));
        staff.flush();

        mvc.perform(get("/api/inspections/" + draft).session(caretaker))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(get("/api/inspections/" + other).session(caretaker)).andExpect(status().isNotFound());
        mvc.perform(post("/api/inspections/" + other + "/start").session(caretaker).with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void residenceListFollowsTheAssignments() throws Exception {
        mvc.perform(get("/api/residences").session(login("caretaker2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].name").value("Petershausen"))
            .andExpect(jsonPath("$.content[1].name").value("Wohnanlage Sonne"));
    }

    @Test
    void deputyCanLogInAsMockUser() throws Exception {
        mvc.perform(get("/api/me").session(login("caretaker2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("CARETAKER"));
    }

    /** Neue geplante Kontrolle ohne Zeitraum in einer WG der Anlage; sie gilt sofort als durchführbar. */
    private Long plannedIn(String residenceName) {
        var residence = residences.findFirstByName(residenceName).orElseThrow();
        var flat = flats.save(new FlatEntity(residence, "T-" + residenceName.hashCode() + "-" + System.nanoTime(), 2));
        return inspections.save(new InspectionEntity(flat, null)).getId();
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
