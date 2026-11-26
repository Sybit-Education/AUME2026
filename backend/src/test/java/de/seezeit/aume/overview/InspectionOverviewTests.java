package de.seezeit.aume.overview;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.inspection.InspectionEntity.Result;
import de.seezeit.aume.inspection.InspectionEntity.Status;
import de.seezeit.aume.inspection.InspectionRepository;
import de.seezeit.aume.planning.InspectionPeriodRepository;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.residence.StaffAssignmentEntity;
import de.seezeit.aume.residence.StaffAssignmentRepository;
import de.seezeit.aume.residence.StaffKind;
import de.seezeit.aume.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-overview-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class InspectionOverviewTests {
    private static final String PATH = "/api/inspection-overview";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private InspectionPeriodRepository periods;
    @Autowired private InspectionRepository inspections;
    @Autowired private StaffAssignmentRepository staff;

    @Test
    void onlyClerksReadTheOverview() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        for (var key : new String[]{"student", "caretaker"}) {
            mvc.perform(get(PATH).session(login(key))).andExpect(status().isForbidden());
            mvc.perform(get(PATH + "/counts").session(login(key))).andExpect(status().isForbidden());
        }
        mvc.perform(post(PATH).session(login("clerk")).with(csrf())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void oneRowPerAssignedResidenceWithLastCompletedAndCurrentRound() throws Exception {
        mvc.perform(get(PATH).session(login("clerk")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].name").value(contains("Egg", "Wohnanlage Sonne")))
            .andExpect(jsonPath("$.content[0].state").value("COUNTER_PROPOSED"))
            .andExpect(jsonPath("$.content[0].lastCompleted").value(nullValue()))
            .andExpect(jsonPath("$.content[0].current.startsOn").value(day(14).toString()))
            .andExpect(jsonPath("$.content[0].current.kind").value("REGULAR"))
            .andExpect(jsonPath("$.content[1].state").value("NO_UPCOMING"))
            .andExpect(jsonPath("$.content[1].lastCompleted.passed").value(1))
            .andExpect(jsonPath("$.content[1].lastCompleted.failed").value(1))
            .andExpect(jsonPath("$.content[1].lastCompleted.state").value("COMPLETED"))
            .andExpect(jsonPath("$.content[1].current").value(nullValue()))
            .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void residenceWithoutRoundsHasStateNone() throws Exception {
        assignClerk("Petershausen");
        mvc.perform(get(PATH).param("state", "NONE").session(login("clerk")))
            .andExpect(jsonPath("$.content[*].name").value(contains("Petershausen")));
    }

    @Test
    void announcedRoundBecomesRunningThenCompleted() throws Exception {
        var period = periods.findAll().stream().filter(p -> p.getResidence().getName().equals("Egg")).findFirst().orElseThrow();
        mvc.perform(post("/api/inspection-periods/" + period.getId() + "/accept-counter").session(login("clerk"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + period.getVersion() + "}"))
            .andExpect(status().isOk());
        period.setAnnouncedAt(Instant.now());
        periods.flush();

        mvc.perform(get(PATH).param("state", "ANNOUNCED").session(login("clerk")))
            .andExpect(jsonPath("$.content[*].name").value(contains("Egg")))
            .andExpect(jsonPath("$.content[0].current.controlled").value(0))
            .andExpect(jsonPath("$.content[0].current.total").value(2));

        var eggInspections = inspections.findAll().stream()
            .filter(i -> i.getPeriod() != null && i.getPeriod().getId().equals(period.getId())).toList();
        eggInspections.getFirst().setStatus(Status.FINALIZED);
        eggInspections.getFirst().setOverallResult(Result.PASSED);
        eggInspections.getFirst().setInspectedAt(Instant.now());
        inspections.flush();
        mvc.perform(get(PATH).param("state", "RUNNING").session(login("clerk")))
            .andExpect(jsonPath("$.content[0].current.controlled").value(1))
            .andExpect(jsonPath("$.content[0].current.passed").value(1));

        eggInspections.get(1).setStatus(Status.FINALIZED);
        eggInspections.get(1).setOverallResult(Result.FAILED);
        eggInspections.get(1).setInspectedAt(Instant.now());
        inspections.flush();
        mvc.perform(get(PATH).session(login("clerk")))
            .andExpect(jsonPath("$.content[0].name").value("Egg"))
            .andExpect(jsonPath("$.content[0].state").value("NO_UPCOMING"))
            .andExpect(jsonPath("$.content[0].lastCompleted.failed").value(1))
            .andExpect(jsonPath("$.content[0].current").value(nullValue()));
        mvc.perform(get(PATH + "/counts").session(login("clerk")))
            .andExpect(jsonPath("$.counterProposed").value(0))
            .andExpect(jsonPath("$.agreedNotAnnounced").value(0));
    }

    @Test
    void filtersByStateAndKindWhileCountsStayUnfiltered() throws Exception {
        var clerk = login("clerk");
        mvc.perform(get(PATH).param("state", "COUNTER_PROPOSED").session(clerk))
            .andExpect(jsonPath("$.content[*].name").value(contains("Egg")));
        mvc.perform(get(PATH).param("kind", "REGULAR").session(clerk))
            .andExpect(jsonPath("$.content[*].name").value(contains("Egg")));
        mvc.perform(get(PATH + "/counts").session(clerk))
            .andExpect(jsonPath("$.counterProposed").value(1))
            .andExpect(jsonPath("$.agreedNotAnnounced").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"state=DONE", "kind=SPECIAL"})
    void unknownFilterValuesAreRejected(String query) throws Exception {
        mvc.perform(get(PATH + "?" + query).session(login("clerk"))).andExpect(status().isBadRequest());
    }

    @Test
    void detailListsRoundsAndOnlyOpenSingleFlatInspections() throws Exception {
        var clerk = login("clerk");
        var flat = flats.findByResidenceIdAndLabel(residenceId("Wohnanlage Sonne"), "S-01").orElseThrow();
        var openFollowUp = new InspectionEntity(inspections.findAll().stream()
            .filter(i -> i.getFlat().getId().equals(flat.getId()) && i.getRound() == 0).findFirst().orElseThrow(),
            day(2));
        inspections.save(openFollowUp);

        mvc.perform(get(PATH + "/" + residenceId("Egg")).session(clerk))
            .andExpect(jsonPath("$.name").value("Egg"))
            .andExpect(jsonPath("$.rounds[0].state").value("COUNTER_PROPOSED"))
            .andExpect(jsonPath("$.rounds[0].kind").value("REGULAR"))
            .andExpect(jsonPath("$.openInspections").isEmpty());
        mvc.perform(get(PATH + "/" + residenceId("Wohnanlage Sonne")).session(clerk))
            .andExpect(jsonPath("$.rounds[0].state").value("COMPLETED"))
            .andExpect(jsonPath("$.rounds[0].passed").value(1))
            .andExpect(jsonPath("$.rounds[0].failed").value(1))
            .andExpect(jsonPath("$.openInspections[*].flat").value(hasItem("S-01")))
            .andExpect(jsonPath("$.openInspections[*].kind").value(hasItem("FOLLOW_UP")))
            .andExpect(jsonPath("$.openInspections[*].outcome").value(hasItem("OPEN")));
    }

    @Test
    void followUpDoesNotChangeLastCompletedRound() throws Exception {
        var flat = flats.findByResidenceIdAndLabel(residenceId("Wohnanlage Sonne"), "S-02").orElseThrow();
        var followUp = new InspectionEntity(inspections.findAll().stream()
            .filter(i -> i.getFlat().getId().equals(flat.getId()) && i.getRound() == 0
                && (i.getStatus() == Status.FINALIZED || i.getStatus() == Status.PUBLISHED))
            .findFirst().orElseThrow(), day(-1));
        followUp.setStatus(Status.FINALIZED);
        followUp.setOverallResult(Result.PASSED);
        followUp.setInspectedAt(Instant.now());
        inspections.save(followUp);

        mvc.perform(get(PATH).session(login("clerk")))
            .andExpect(jsonPath("$.content[1].lastCompleted.passed").value(1))
            .andExpect(jsonPath("$.content[1].lastCompleted.failed").value(1));
    }

    @Test
    void residencesOfOtherClerksAreNotFound() throws Exception {
        mvc.perform(get(PATH + "/" + residenceId("Petershausen")).session(login("clerk")))
            .andExpect(status().isNotFound());
    }

    private void assignClerk(String residence) {
        staff.save(new StaffAssignmentEntity(users.findByUserKey("clerk").orElseThrow(),
            residences.findFirstByName(residence).orElseThrow(), StaffKind.CLERK));
    }

    private Long residenceId(String name) {
        return residences.findFirstByName(name).orElseThrow().getId();
    }

    private static LocalDate day(int offset) {
        return LocalDate.now(ZoneId.of("Europe/Berlin")).plusDays(offset);
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
