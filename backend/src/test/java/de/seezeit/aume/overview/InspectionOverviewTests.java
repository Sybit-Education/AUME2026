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
import de.seezeit.aume.planning.InspectionPeriodRepository;
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
    @Autowired private InspectionPeriodRepository periods;
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
    void oneRowPerAssignedResidenceWithLastAndUpcomingInspection() throws Exception {
        mvc.perform(get(PATH).session(login("clerk")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].name").value(contains("Egg", "Wohnanlage Sonne")))
            .andExpect(jsonPath("$.content[0].state").value("COUNTER_PROPOSED"))
            .andExpect(jsonPath("$.content[0].lastInspection").value(nullValue()))
            .andExpect(jsonPath("$.content[0].upcoming.startsOn").value(day(14).toString()))
            .andExpect(jsonPath("$.content[0].upcoming.kind").value("REGULAR"))
            .andExpect(jsonPath("$.content[1].state").value("NO_UPCOMING"))
            .andExpect(jsonPath("$.content[1].lastInspection.on").value(day(-23).toString()))
            .andExpect(jsonPath("$.content[1].lastInspection.kind").value("FOLLOW_UP"))
            .andExpect(jsonPath("$.content[1].upcoming").value(nullValue()))
            .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void residenceWithoutInspectionsHasStateNone() throws Exception {
        assignClerk("Petershausen");
        mvc.perform(get(PATH).param("state", "NONE").session(login("clerk")))
            .andExpect(jsonPath("$.content[*].name").value(contains("Petershausen")));
    }

    @Test
    void stateFollowsPlanningAndAnnouncement() throws Exception {
        var period = periods.findAll().getFirst();
        mvc.perform(post("/api/inspection-periods/" + period.getId() + "/accept-counter").session(login("clerk"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + period.getVersion() + "}"))
            .andExpect(status().isOk());
        mvc.perform(get(PATH + "/counts").session(login("clerk")))
            .andExpect(jsonPath("$.counterProposed").value(0))
            .andExpect(jsonPath("$.agreedNotAnnounced").value(1));

        period.setAnnouncedAt(Instant.now());
        periods.flush();
        mvc.perform(get(PATH).param("state", "ANNOUNCED").session(login("clerk")))
            .andExpect(jsonPath("$.content[*].name").value(contains("Egg")));
        mvc.perform(get(PATH + "/counts").session(login("clerk")))
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
    void residenceDetailListsPeriodsAndSingleFlatInspections() throws Exception {
        var clerk = login("clerk");
        mvc.perform(get(PATH + "/" + residenceId("Egg")).session(clerk))
            .andExpect(jsonPath("$.content[0].flat").value(nullValue()))
            .andExpect(jsonPath("$.content[0].kind").value("REGULAR"))
            .andExpect(jsonPath("$.content[0].status").value("COUNTER_PROPOSED"));
        mvc.perform(get(PATH + "/" + residenceId("Wohnanlage Sonne")).param("size", "2").session(clerk))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[*].flat").value(hasItem("S-01")))
            .andExpect(jsonPath("$.page.totalElements").value(5));
        mvc.perform(get(PATH + "/" + residenceId("Wohnanlage Sonne")).session(clerk))
            .andExpect(jsonPath("$.content[*].kind").value(hasItem("FOLLOW_UP")));
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
