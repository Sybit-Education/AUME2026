package de.seezeit.aume.overview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.InspectionEntity.Result;
import de.seezeit.aume.inspection.InspectionEntity.Status;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.inspection.InspectionRepository;
import de.seezeit.aume.InspectionOverviewSampleData;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.planning.InspectionPeriodRepository;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.residence.StaffAssignmentEntity;
import de.seezeit.aume.residence.StaffAssignmentRepository;
import de.seezeit.aume.residence.StaffKind;
import de.seezeit.aume.user.ClerkEntity;
import de.seezeit.aume.user.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private InspectionPeriodRepository periods;
    @Autowired private InspectionRepository inspections;
    @Autowired private StaffAssignmentRepository staff;
    @Autowired private EntityManager entities;
    @Autowired private AppUserService appUsers;
    @Autowired private RoleRepository roles;
    @Autowired private InspectionOverviewSampleData overviewSampleData;

    private ClerkEntity clerk;
    private LocalDate today;
    private ResidenceEntity egg;
    private ResidenceEntity sonne;
    private ResidenceEntity petershausen;
    private InspectionPeriodEntity eggPeriod;
    private InspectionPeriodEntity historicalPeriod;
    private InspectionEntity eggFirstInspection;
    private InspectionEntity eggSecondInspection;
    private InspectionEntity sonneFirstInspection;
    private InspectionEntity sonneSecondInspection;

    @BeforeEach
    void createOverviewFixtures() {
        today = LocalDate.now(ZoneId.of("Europe/Berlin"));
        egg = residences.save(new ResidenceEntity("Übersicht Egg"));
        sonne = residences.save(new ResidenceEntity("Übersicht Sonne"));
        petershausen = residences.save(new ResidenceEntity("Übersicht Petershausen"));
        clerk = new ClerkEntity("overview-test-clerk", "Übersicht Testperson",
            Set.of(roles.findByName("CLERK").orElseThrow()), egg);
        entities.persist(clerk);
        staff.save(new StaffAssignmentEntity(clerk, egg, StaffKind.CLERK));
        staff.save(new StaffAssignmentEntity(clerk, sonne, StaffKind.CLERK));

        eggPeriod = new InspectionPeriodEntity(egg, day(14), day(16), clerk);
        eggPeriod.setStatus(InspectionPeriodEntity.Status.COUNTER_PROPOSED);
        eggPeriod.setCounterStartsOn(day(21));
        eggPeriod.setCounterEndsOn(day(23));
        eggPeriod.setCounterReason("Test-Gegenvorschlag");
        periods.save(eggPeriod);
        eggFirstInspection = new InspectionEntity(flats.save(new FlatEntity(egg, "E-01", 2)), eggPeriod);
        eggSecondInspection = new InspectionEntity(flats.save(new FlatEntity(egg, "E-02", 2)), eggPeriod);
        inspections.save(eggFirstInspection);
        inspections.save(eggSecondInspection);

        historicalPeriod = new InspectionPeriodEntity(sonne, day(-30), day(-28), clerk);
        historicalPeriod.setStatus(InspectionPeriodEntity.Status.AGREED);
        historicalPeriod.setAnnouncedBy(clerk);
        historicalPeriod.setAnnouncedAt(day(-31).atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant());
        periods.save(historicalPeriod);
        sonneFirstInspection = completedInspection("S-01", Result.FAILED);
        sonneSecondInspection = completedInspection("S-02", Result.PASSED);
    }

    private InspectionEntity completedInspection(String label, Result result) {
        var inspection = new InspectionEntity(flats.save(new FlatEntity(sonne, label, 2)), historicalPeriod);
        inspection.setStatus(Status.PUBLISHED);
        inspection.setOverallResult(result);
        inspection.setInspectedAt(historicalPeriod.getStartsOn().atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant());
        inspection.setPublishedAt(inspection.getInspectedAt());
        inspection.setPublishedBy(clerk);
        return inspections.save(inspection);
    }

    private MockHttpSession overviewSession() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            clerk.getUserKey(), null, appUsers.authorities(clerk)));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    @Test
    void onlyClerksReadTheOverview() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        for (var key : new String[]{"student", "caretaker"}) {
            mvc.perform(get(PATH).session(login(key))).andExpect(status().isForbidden());
            mvc.perform(get(PATH + "/counts").session(login(key))).andExpect(status().isForbidden());
        }
        mvc.perform(post(PATH).session(overviewSession()).with(csrf())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void oneRowPerAssignedResidenceWithLastCompletedAndCurrentRound() throws Exception {
        mvc.perform(get(PATH).session(overviewSession()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].name").value(contains(egg.getName(), sonne.getName())))
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
        staff.save(new StaffAssignmentEntity(clerk, petershausen, StaffKind.CLERK));
        mvc.perform(get(PATH).param("state", "NONE").session(overviewSession()))
            .andExpect(jsonPath("$.content[*].name").value(contains(petershausen.getName())));
    }

    @Test
    void announcedRoundBecomesRunningThenCompleted() throws Exception {
        var period = eggPeriod;
        mvc.perform(post("/api/inspection-periods/" + period.getId() + "/accept-counter").session(overviewSession())
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + period.getVersion() + "}"))
            .andExpect(status().isOk());
        period.setAnnouncedAt(Instant.now());
        periods.flush();

        mvc.perform(get(PATH).param("state", "ANNOUNCED").session(overviewSession()))
            .andExpect(jsonPath("$.content[*].name").value(contains(egg.getName())))
            .andExpect(jsonPath("$.content[0].current.controlled").value(0))
            .andExpect(jsonPath("$.content[0].current.total").value(2));

        eggFirstInspection.setStatus(Status.FINALIZED);
        eggFirstInspection.setOverallResult(Result.PASSED);
        eggFirstInspection.setInspectedAt(Instant.now());
        inspections.flush();
        mvc.perform(get(PATH).param("state", "RUNNING").session(overviewSession()))
            .andExpect(jsonPath("$.content[0].current.controlled").value(1))
            .andExpect(jsonPath("$.content[0].current.passed").value(1));

        eggSecondInspection.setStatus(Status.FINALIZED);
        eggSecondInspection.setOverallResult(Result.FAILED);
        eggSecondInspection.setInspectedAt(Instant.now());
        inspections.flush();
        mvc.perform(get(PATH).session(overviewSession()))
            .andExpect(jsonPath("$.content[0].name").value(egg.getName()))
            .andExpect(jsonPath("$.content[0].state").value("NO_UPCOMING"))
            .andExpect(jsonPath("$.content[0].lastCompleted.failed").value(1))
            .andExpect(jsonPath("$.content[0].current").value(nullValue()));
        mvc.perform(get(PATH + "/counts").session(overviewSession()))
            .andExpect(jsonPath("$.counterProposed").value(0))
            .andExpect(jsonPath("$.agreedNotAnnounced").value(0));
    }

    @Test
    void filtersByStateAndKindWhileCountsStayUnfiltered() throws Exception {
        var clerk = overviewSession();
        mvc.perform(get(PATH).param("state", "COUNTER_PROPOSED").session(clerk))
            .andExpect(jsonPath("$.content[*].name").value(contains(egg.getName())));
        mvc.perform(get(PATH).param("kind", "REGULAR").session(clerk))
            .andExpect(jsonPath("$.content[*].name").value(contains(egg.getName())));
        mvc.perform(get(PATH + "/counts").session(clerk))
            .andExpect(jsonPath("$.counterProposed").value(1))
            .andExpect(jsonPath("$.agreedNotAnnounced").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"state=DONE", "kind=SPECIAL"})
    void unknownFilterValuesAreRejected(String query) throws Exception {
        mvc.perform(get(PATH + "?" + query).session(overviewSession())).andExpect(status().isBadRequest());
    }

    @Test
    void detailListsRoundsAndOnlyOpenSingleFlatInspections() throws Exception {
        var clerk = overviewSession();
        var openFollowUp = new InspectionEntity(sonneFirstInspection, day(2));
        inspections.save(openFollowUp);

        mvc.perform(get(PATH + "/" + egg.getId()).session(clerk))
            .andExpect(jsonPath("$.name").value(egg.getName()))
            .andExpect(jsonPath("$.rounds[0].state").value("COUNTER_PROPOSED"))
            .andExpect(jsonPath("$.rounds[0].kind").value("REGULAR"))
            .andExpect(jsonPath("$.openInspections").isEmpty());
        mvc.perform(get(PATH + "/" + sonne.getId()).session(clerk))
            .andExpect(jsonPath("$.rounds[0].state").value("COMPLETED"))
            .andExpect(jsonPath("$.rounds[0].passed").value(1))
            .andExpect(jsonPath("$.rounds[0].failed").value(1))
            .andExpect(jsonPath("$.openInspections[*].flat").value(hasItem("S-01")))
            .andExpect(jsonPath("$.openInspections[*].kind").value(hasItem("FOLLOW_UP")))
            .andExpect(jsonPath("$.openInspections[*].outcome").value(hasItem("OPEN")));
    }

    @Test
    void followUpDoesNotChangeLastCompletedRound() throws Exception {
        var followUp = new InspectionEntity(sonneSecondInspection, day(-1));
        followUp.setStatus(Status.FINALIZED);
        followUp.setOverallResult(Result.PASSED);
        followUp.setInspectedAt(Instant.now());
        inspections.save(followUp);

        mvc.perform(get(PATH).session(overviewSession()))
            .andExpect(jsonPath("$.content[1].lastCompleted.passed").value(1))
            .andExpect(jsonPath("$.content[1].lastCompleted.failed").value(1));
    }

    @Test
    void residencesOfOtherClerksAreNotFound() throws Exception {
        var otherClerk = new ClerkEntity("overview-test-other-clerk", "Andere Übersicht Testperson",
            clerk.getRoles(), petershausen);
        entities.persist(otherClerk);
        staff.save(new StaffAssignmentEntity(otherClerk, petershausen, StaffKind.CLERK));
        mvc.perform(get(PATH + "/" + petershausen.getId()).session(overviewSession()))
            .andExpect(status().isNotFound());
    }

    @Test
    void historicalDevRoundCoexistsWithTheStudentDemosAndIsSeededOnlyOnce() throws Exception {
        var demoSonne = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
        var demoInspections = inspections.findAll().stream()
            .filter(i -> i.getFlat().getResidence().getId().equals(demoSonne.getId())).toList();
        var planned = demoInspections.stream()
            .filter(i -> i.getFlat().getLabel().equals("S-01") && i.getStatus() == Status.PLANNED)
            .filter(i -> i.getPeriod() != null && i.getPeriod().getAnnouncedAt() != null)
            .toList();
        assertThat(planned).hasSize(1);
        var studentPeriod = planned.getFirst().getPeriod();
        assertThat(studentPeriod.getStartsOn()).isEqualTo(LocalDate.of(2026, 12, 7));
        assertThat(studentPeriod.getEndsOn()).isEqualTo(LocalDate.of(2026, 12, 11));

        var previous = demoInspections.stream().map(InspectionEntity::getParentInspection)
            .filter(i -> i != null && i.getFlat().getLabel().equals("S-01")).toList();
        assertThat(previous).hasSize(1);
        var historical = previous.getFirst().getPeriod();
        assertThat(historical).isNotNull();
        assertThat(historical.getId()).isNotEqualTo(studentPeriod.getId());
        assertThat(demoInspections.stream()
            .filter(i -> i.getPeriod() != null && historical.getId().equals(i.getPeriod().getId())).toList())
            .extracting(i -> i.getFlat().getLabel()).containsExactlyInAnyOrder("S-01", "S-02");

        var studentResults = demoInspections.stream()
            .filter(i -> i.getFlat().getLabel().equals("S-01") && i.getRound() == 0)
            .filter(i -> i.getStatus() == Status.PUBLISHED && i.getPeriod() == null).toList();
        assertThat(studentResults).hasSize(1);
        var studentResult = studentResults.getFirst();
        assertThat(studentResult.getOverallResult()).isEqualTo(Result.FAILED);

        var periodCount = periods.count();
        var inspectionCount = inspections.count();
        overviewSampleData.run();
        overviewSampleData.run();
        entities.flush();
        entities.clear();

        assertThat(periods.count()).isEqualTo(periodCount);
        assertThat(inspections.count()).isEqualTo(inspectionCount);
        assertThat(inspections.findById(studentResult.getId()).orElseThrow().getPeriod()).isNull();
        assertThat(inspections.findById(planned.getFirst().getId()).orElseThrow().getPeriod().getId())
            .isEqualTo(studentPeriod.getId());
        mvc.perform(get(PATH + "/" + demoSonne.getId()).session(login("clerk")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rounds[?(@.id == " + historical.getId() + ")].state").value(contains("COMPLETED")))
            .andExpect(jsonPath("$.rounds[?(@.id == " + studentPeriod.getId() + ")].state").value(contains("ANNOUNCED")));
    }

    private LocalDate day(int offset) {
        return today.plusDays(offset);
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
