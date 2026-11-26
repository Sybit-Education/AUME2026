package de.seezeit.aume.inspection;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.user.CaretakerEntity;
import de.seezeit.aume.user.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/** Vorgeschichte der WG in der Kontrolle (US-51). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-previous-findings-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PreviousFindingsTests {
    @Autowired private MockMvc mvc;
    @Autowired private InspectionRepository inspections;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private EntityManager entities;
    @Autowired private AppUserService users;
    @Autowired private RoleRepository roles;

    private CaretakerEntity caretaker;
    private ChecklistVersionEntity checklist;
    private InspectionEntity initial;
    private InspectionEntity followUp;
    private InspectionEntity otherFlatInspection;
    private InspectionEntity planned;
    private InspectionEntity otherPlanned;

    @BeforeEach
    void createPreviousFindingsFixtures() {
        var residence = residences.save(new ResidenceEntity("Vorgeschichte-Testwohnanlage"));
        var flat = flats.save(new FlatEntity(residence, "S-01", 4));
        var otherFlat = flats.save(new FlatEntity(residence, "S-02", 3));
        caretaker = new CaretakerEntity("previous-findings-test-caretaker", "Vorgeschichte-Testperson",
            Set.of(roles.findByName("CARETAKER").orElseThrow()), residence);
        entities.persist(caretaker);
        checklist = new ChecklistVersionEntity(Integer.MAX_VALUE, caretaker);
        checklist.setStatus(ChecklistVersionEntity.Status.PUBLISHED);
        checklist.setPublishedAt(Instant.parse("2020-01-01T08:00:00Z"));
        entities.persist(checklist);

        initial = completed(new InspectionEntity(flat, null), Instant.parse("2020-01-02T09:00:00Z"),
            InspectionEntity.Status.PUBLISHED, InspectionEntity.Result.FAILED);
        followUp = completed(new InspectionEntity(initial, null), Instant.parse("2020-01-09T09:00:00Z"),
            InspectionEntity.Status.PUBLISHED, InspectionEntity.Result.PASSED);
        otherFlatInspection = completed(new InspectionEntity(otherFlat, null), Instant.parse("2020-01-04T09:00:00Z"),
            InspectionEntity.Status.PUBLISHED, InspectionEntity.Result.PASSED);
        planned = inspections.save(new InspectionEntity(flat, null));
        otherPlanned = inspections.save(new InspectionEntity(otherFlat, null));

        var oven = criterion("Küche", "Herd & Backofen", 1);
        var waste = criterion("Küche", "Mülltrennung", 2);
        var bathroom = criterion("Bad", "Dusche & WC", 3);
        var ok = criterion("Boden", "Flurboden", 4);
        finding(initial, oven, FindingEntity.Rating.DEFICIENT, FindingEntity.Severity.SEVERE, "Herd verschmutzt");
        finding(initial, waste, FindingEntity.Rating.DEFICIENT, FindingEntity.Severity.LIGHT, "Müll falsch getrennt");
        finding(initial, bathroom, FindingEntity.Rating.RESOLVED_ON_SITE, null, "Abfluss vor Ort gereinigt");
        finding(initial, ok, FindingEntity.Rating.OK, null, null);
        finding(followUp, oven, FindingEntity.Rating.OK, null, null);
        finding(otherFlatInspection, oven, FindingEntity.Rating.OK, null, null);
    }

    private InspectionEntity completed(InspectionEntity inspection, Instant date, InspectionEntity.Status status,
            InspectionEntity.Result result) {
        inspection.setChecklistVersion(checklist);
        inspection.setInspector(caretaker);
        inspection.setInspectedAt(date);
        inspection.setOverallResult(result);
        inspection.setStatus(status);
        if (status == InspectionEntity.Status.PUBLISHED) {
            inspection.setPublishedBy(caretaker);
            inspection.setPublishedAt(date);
        }
        return inspections.save(inspection);
    }

    private CriterionEntity criterion(String area, String title, int position) {
        var criterion = new CriterionEntity(checklist, area, title, "Testbeschreibung", position);
        entities.persist(criterion);
        return criterion;
    }

    private void finding(InspectionEntity inspection, CriterionEntity criterion, FindingEntity.Rating rating,
            FindingEntity.Severity severity, String note) {
        var finding = new FindingEntity(inspection, criterion, rating);
        finding.setSeverity(severity);
        finding.setNote(note);
        entities.persist(finding);
    }

    @Test
    void onlyCaretakersMaySeeThePreviousFindings() throws Exception {
        String path = previousOf(planned.getId());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String key : new String[]{"student", "clerk"}) {
            mvc.perform(get(path).session(login(key))).andExpect(status().isForbidden());
        }
    }

    @Test
    void plannedInspectionShowsTheEarlierInspectionsOfTheSameFlatNewestFirst() throws Exception {
        mvc.perform(get(previousOf(planned.getId())).session(caretakerSession()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$[1].id").value(initial.getId().intValue()))
            .andExpect(jsonPath("$[0].round").value(1))
            .andExpect(jsonPath("$[0].overallResult").value("PASSED"))
            .andExpect(jsonPath("$[0].defects.length()").value(0))
            .andExpect(jsonPath("$[1].round").value(0))
            .andExpect(jsonPath("$[1].overallResult").value("FAILED"))
            .andExpect(jsonPath("$[1].defects.length()").value(3))
            .andExpect(jsonPath("$[1].defects[0].area").value("Küche"))
            .andExpect(jsonPath("$[1].defects[0].criterionTitle").value("Herd & Backofen"))
            .andExpect(jsonPath("$[1].defects[0].severity").value("SEVERE"))
            .andExpect(jsonPath("$[1].defects[0].note").isNotEmpty())
            .andExpect(jsonPath("$[1].defects[2].rating").value("RESOLVED_ON_SITE"));

        mvc.perform(get(previousOf(otherPlanned.getId())).session(caretakerSession()))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(otherFlatInspection.getId().intValue()))
            .andExpect(jsonPath("$[0].defects.length()").value(0));
    }

    @Test
    void aFinishedInspectionOnlyShowsWhatCameBeforeIt() throws Exception {
        var session = caretakerSession();
        Long initialId = initial.getId();
        Long followUpId = followUp.getId();
        mvc.perform(get(previousOf(initialId)).session(session)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(previousOf(followUpId)).session(session))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(initialId));
    }

    @Test
    void atMostThreeEarlierInspectionsAreShown() throws Exception {
        var date = initial.getInspectedAt();
        var latestOlder = completed(new InspectionEntity(planned.getFlat(), null), date.minus(Duration.ofDays(1)),
            InspectionEntity.Status.FINALIZED, InspectionEntity.Result.PASSED);
        for (int days = 2; days <= 4; days++) {
            completed(new InspectionEntity(planned.getFlat(), null), date.minus(Duration.ofDays(days)),
                InspectionEntity.Status.FINALIZED, InspectionEntity.Result.PASSED);
        }
        mvc.perform(get(previousOf(planned.getId())).session(caretakerSession()))
            .andExpect(jsonPath("$.length()").value(PreviousFindingsService.LIMIT))
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$[1].id").value(initial.getId().intValue()))
            .andExpect(jsonPath("$[2].id").value(latestOlder.getId().intValue()))
            .andExpect(jsonPath("$[2].overallResult").value("PASSED"));
    }

    @Test
    void startedInspectionKeepsItsPreviousFindings() throws Exception {
        var session = caretakerSession();
        Long id = planned.getId();
        mvc.perform(post("/api/inspections/" + id + "/start").session(session).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(get(previousOf(id)).session(session))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$[1].id").value(initial.getId().intValue()));
    }

    @Test
    void inspectionsOfOtherResidencesAndUnknownIdsAreNotFound() throws Exception {
        var other = residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var foreign = inspections.save(new InspectionEntity(flats.save(new FlatEntity(other, "1.01", 2)), null));
        var session = caretakerSession();
        mvc.perform(get(previousOf(foreign.getId())).session(session)).andExpect(status().isNotFound());
        mvc.perform(get(previousOf(999_999L)).session(session)).andExpect(status().isNotFound());
    }

    private static String previousOf(Long id) {
        return "/api/inspections/" + id + "/previous-findings";
    }

    private MockHttpSession caretakerSession() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            caretaker.getUserKey(), null, users.authorities(caretaker)));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
