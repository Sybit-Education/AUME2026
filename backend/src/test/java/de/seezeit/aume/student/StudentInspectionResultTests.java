package de.seezeit.aume.student;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.FindingEntity;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.user.RoleRepository;
import de.seezeit.aume.user.StudentEntity;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** US-38: Ergebnis und Mängel der eigenen WG (GET /api/student/inspection-results/latest). */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:aume-us38-results",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class StudentInspectionResultTests {
    private static final String PATH = "/api/student/inspection-results/latest";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entities;
    @Autowired private RoleRepository roles;
    @Autowired private AppUserService users;

    private ResidenceEntity residence;
    private FlatEntity flat;
    private StudentEntity student;
    private UserEntity caretaker;
    private List<CriterionEntity> criteria;

    @BeforeEach
    void setUp() {
        residence = new ResidenceEntity("US-38 Testwohnanlage");
        entities.persist(residence);
        flat = new FlatEntity(residence, "T-01", 2);
        entities.persist(flat);
        student = student("us38-student");
        caretaker = users.requireUser("caretaker");

        var version = new ChecklistVersionEntity(99, users.requireUser("clerk"));
        version.setStatus(ChecklistVersionEntity.Status.PUBLISHED);
        version.setPublishedAt(Instant.now());
        entities.persist(version);
        criteria = List.of(
            new CriterionEntity(version, "Küche", "Herd", "Sauber.", 1),
            new CriterionEntity(version, "Küche", "Mülltrennung", "Getrennt.", 2),
            new CriterionEntity(version, "Bad", "Dusche", "Kalkfrei.", 3),
            new CriterionEntity(version, "Bad", "Spiegel", "Streifenfrei.", 4),
            new CriterionEntity(version, "Flur", "Boden", "Gewischt.", 5));
        criteria.forEach(entities::persist);
    }

    @Test
    void showsTheLatestResultWithDeficienciesResolvedAndPassedCriteria() throws Exception {
        entities.persist(new MembershipEntity(flat, student, LocalDate.now(ZONE).minusDays(60)));
        var initial = published(flat, null, Instant.now().minus(Duration.ofDays(20)), InspectionEntity.Result.FAILED);
        var herd = finding(initial, criteria.get(0), FindingEntity.Rating.DEFICIENT, "Fett im Ofen.");
        herd.setBuildingCauseSuspected(true);
        finding(initial, criteria.get(1), FindingEntity.Rating.DEFICIENT, "Restmüll im Gelben Sack.");
        finding(initial, criteria.get(2), FindingEntity.Rating.RESOLVED_ON_SITE, "Abfluss gereinigt.");
        finding(initial, criteria.get(3), FindingEntity.Rating.OK, null);
        // Kriterium 5 ohne Finding zählt als bestanden.
        entities.flush();

        mvc.perform(get(PATH).session(session(student)))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.inspectionId").value(initial.getId().intValue()))
            .andExpect(jsonPath("$.kind").value("INITIAL"))
            .andExpect(jsonPath("$.round").value(0))
            .andExpect(jsonPath("$.inspectedAt").exists())
            .andExpect(jsonPath("$.overallResult").value("FAILED"))
            .andExpect(jsonPath("$.deficiencies", hasSize(2)))
            .andExpect(jsonPath("$.deficiencies[0].area").value("Küche"))
            .andExpect(jsonPath("$.deficiencies[0].criterion").value("Herd"))
            .andExpect(jsonPath("$.deficiencies[0].note").value("Fett im Ofen."))
            .andExpect(jsonPath("$.deficiencies[0].buildingCauseSuspected").value(true))
            .andExpect(jsonPath("$.deficiencies[1].criterion").value("Mülltrennung"))
            .andExpect(jsonPath("$.deficiencies[1].buildingCauseSuspected").value(false))
            .andExpect(jsonPath("$.resolvedOnSite", hasSize(1)))
            .andExpect(jsonPath("$.resolvedOnSite[0].criterion").value("Dusche"))
            .andExpect(jsonPath("$.resolvedOnSite[0].note").value("Abfluss gereinigt."))
            .andExpect(jsonPath("$.passed[*].criterion").value(contains("Spiegel", "Boden")))
            .andExpect(jsonPath("$.inspector").doesNotExist())
            .andExpect(jsonPath("$.publishedBy").doesNotExist())
            .andExpect(jsonPath("$.flatId").doesNotExist());

        // Eine neuere Nachkontrolle ersetzt das Ergebnis.
        var followUp = published(flat, initial, Instant.now().minus(Duration.ofDays(5)), InspectionEntity.Result.PASSED);
        entities.flush();
        mvc.perform(get(PATH).session(session(student)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inspectionId").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.kind").value("FOLLOW_UP"))
            .andExpect(jsonPath("$.round").value(1))
            .andExpect(jsonPath("$.overallResult").value("PASSED"))
            .andExpect(jsonPath("$.deficiencies").isEmpty())
            .andExpect(jsonPath("$.passed", hasSize(5)));
    }

    @Test
    void returnsNoContentWithoutAPublishedResult() throws Exception {
        entities.persist(new MembershipEntity(flat, student, LocalDate.now(ZONE).minusDays(10)));
        var draft = published(flat, null, Instant.now().minus(Duration.ofDays(1)), InspectionEntity.Result.FAILED);
        draft.setStatus(InspectionEntity.Status.FINALIZED);
        entities.flush();

        mvc.perform(get(PATH).session(session(student)))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
    }

    @Test
    void returnsNotFoundWithoutActiveMembership() throws Exception {
        var movedOut = new MembershipEntity(flat, student, LocalDate.now(ZONE).minusDays(90));
        movedOut.setValidTo(LocalDate.now(ZONE).minusDays(1));
        entities.persist(movedOut);
        published(flat, null, Instant.now().minus(Duration.ofDays(30)), InspectionEntity.Result.FAILED);
        entities.flush();

        mvc.perform(get(PATH).session(session(student))).andExpect(status().isNotFound());
        mvc.perform(get(PATH).session(session(student("us38-without-flat")))).andExpect(status().isNotFound());
    }

    @Test
    void hidesResultsPublishedBeforeMovingIn() throws Exception {
        var movedIn = LocalDate.now(ZONE).minusDays(3);
        entities.persist(new MembershipEntity(flat, student, movedIn));
        published(flat, null, movedIn.atStartOfDay(ZONE).toInstant().minusSeconds(1), InspectionEntity.Result.FAILED);
        entities.flush();

        mvc.perform(get(PATH).session(session(student))).andExpect(status().isNoContent());

        var afterMoveIn = published(flat, null, movedIn.atStartOfDay(ZONE).toInstant(), InspectionEntity.Result.PASSED);
        entities.flush();
        mvc.perform(get(PATH).session(session(student)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inspectionId").value(afterMoveIn.getId().intValue()));
    }

    @Test
    void showsOnlyTheOwnFlatAndIsClosedToStaff() throws Exception {
        var otherFlat = new FlatEntity(residence, "T-02", 2);
        entities.persist(otherFlat);
        entities.persist(new MembershipEntity(flat, student, LocalDate.now(ZONE).minusDays(30)));
        published(otherFlat, null, Instant.now().minus(Duration.ofDays(1)), InspectionEntity.Result.FAILED);
        entities.flush();

        mvc.perform(get(PATH).session(session(student)).param("flatId", otherFlat.getId().toString()))
            .andExpect(status().isNoContent());
        mvc.perform(get(PATH).session(session(caretaker))).andExpect(status().isForbidden());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    private InspectionEntity published(FlatEntity target, InspectionEntity parent, Instant at,
            InspectionEntity.Result result) {
        var inspection = parent == null ? new InspectionEntity(target, null) : new InspectionEntity(parent, null);
        inspection.setChecklistVersion(criteria.getFirst().getChecklistVersion());
        inspection.setInspector(caretaker);
        inspection.setInspectedAt(at);
        inspection.setOverallResult(result);
        inspection.setStatus(InspectionEntity.Status.PUBLISHED);
        inspection.setPublishedBy(caretaker);
        inspection.setPublishedAt(at);
        entities.persist(inspection);
        return inspection;
    }

    private FindingEntity finding(InspectionEntity inspection, CriterionEntity criterion, FindingEntity.Rating rating,
            String note) {
        var finding = new FindingEntity(inspection, criterion, rating);
        finding.setNote(note);
        entities.persist(finding);
        return finding;
    }

    private StudentEntity student(String key) {
        var created = new StudentEntity(key, "Student " + key, Set.of(roles.findByName("STUDENT").orElseThrow()),
            residence);
        entities.persist(created);
        return created;
    }

    private MockHttpSession session(UserEntity user) {
        // Der Dev-Login kennt nur die festen Demo-Konten.
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            user.getUserKey(), null, users.authorities(user)));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
