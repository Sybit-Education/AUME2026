package de.seezeit.aume.overview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import de.seezeit.aume.OverviewSampleData;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.residence.StaffAssignmentEntity;
import de.seezeit.aume.residence.StaffAssignmentRepository;
import de.seezeit.aume.residence.StaffKind;
import de.seezeit.aume.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class OverviewTests {
    private static final String PATH = "/api/overview/caretaker";

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entities;
    @Autowired private ResidenceRepository residences;
    @Autowired private OverviewSampleData sampleData;
    @Autowired private OverviewService overview;
    @Autowired private StaffAssignmentRepository staff;
    @Autowired private UserRepository users;

    @BeforeEach
    void seedOverviewData() {
        sampleData.seed();
        // StudentSampleData kündigt S-01 für ein festes Datum an; relativ zu heute bleibt die Reihenfolge stabil.
        var studentPeriod = entities.createQuery("""
            select p from InspectionPeriodEntity p
            where p.residence.name = 'Wohnanlage Sonne' and p.startsOn = :startsOn
            """, InspectionPeriodEntity.class)
            .setParameter("startsOn", LocalDate.of(2026, 12, 7))
            .getSingleResult();
        studentPeriod.setStartsOn(LocalDate.now().plusDays(30));
        studentPeriod.setEndsOn(LocalDate.now().plusDays(34));
    }

    @Test
    void onlyCaretakersMayReadTheOverview() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        for (String key : new String[]{"student", "clerk"}) {
            mvc.perform(get(PATH).session(login(key))).andExpect(status().isForbidden());
        }
        mvc.perform(get(PATH).session(login("caretaker"))).andExpect(status().isOk());
    }

    @Test
    void countsAndListsOpenInspectionsOfTheOwnResidenceByDate() throws Exception {
        mvc.perform(get(PATH).session(login("caretaker")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposals").value(1))
            .andExpect(jsonPath("$.announced").value(2))
            .andExpect(jsonPath("$.followUps").value(1))
            .andExpect(jsonPath("$.openDamages").doesNotExist())
            .andExpect(jsonPath("$.upcoming.length()").value(5))
            .andExpect(jsonPath("$.upcoming[0].flatLabel").value("4.12"))
            .andExpect(jsonPath("$.upcoming[0].residenceName").value("Wohnanlage Sonne"))
            .andExpect(jsonPath("$.upcoming[0].kind").value("INITIAL"))
            .andExpect(jsonPath("$.upcoming[0].state").value("ANNOUNCED"))
            .andExpect(jsonPath("$.upcoming[0].startsOn").value(LocalDate.now().plusDays(1).toString()))
            .andExpect(jsonPath("$.upcoming[1].flatLabel").value("3.08"))
            .andExpect(jsonPath("$.upcoming[1].kind").value("FOLLOW_UP"))
            .andExpect(jsonPath("$.upcoming[1].round").value(1))
            .andExpect(jsonPath("$.upcoming[1].dueOn").value(LocalDate.now().plusDays(5).toString()))
            .andExpect(jsonPath("$.upcoming[0].canRespond").value(false))
            .andExpect(jsonPath("$.upcoming[2].flatLabel").value("3.10"))
            .andExpect(jsonPath("$.upcoming[2].state").value("PROPOSED"))
            .andExpect(jsonPath("$.upcoming[2].periodId").value(proposedPeriod().getId()))
            .andExpect(jsonPath("$.upcoming[2].canRespond").value(true))
            .andExpect(jsonPath("$.upcoming[3].flatLabel").value("S-01"))
            .andExpect(jsonPath("$.upcoming[3].state").value("ANNOUNCED"))
            // Erstkontrollen aus InspectionSampleData ohne Zeitraum stehen am Ende.
            .andExpect(jsonPath("$.upcoming[4].state").value("PLANNED"))
            .andExpect(jsonPath("$.upcoming[4].startsOn").doesNotExist());
    }

    @Test
    void inspectionsOfOtherResidencesStayHidden() throws Exception {
        var other = residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var flat = new FlatEntity(other, "1.01", 2);
        entities.persist(flat);
        entities.persist(new InspectionEntity(flat, null));
        mvc.perform(get(PATH).session(login("caretaker")))
            .andExpect(jsonPath("$.upcoming.length()").value(5))
            .andExpect(jsonPath("$.upcoming[?(@.flatLabel == '1.01')]").isEmpty());
    }

    @Test
    void deputiesSeeTheResidencesTheyStandInFor() {
        // caretaker2 betreut Petershausen und vertritt Sonne (SampleData).
        var upcoming = overview.caretakerOverview("caretaker2").upcoming();
        assertThat(upcoming).extracting(OverviewService.UpcomingInspection::residenceName)
            .containsOnly("Wohnanlage Sonne");
        assertThat(upcoming).hasSize(5);
        // Den Vorschlag beantwortet nur die eingetragene Objektbetreuung.
        assertThat(upcoming).noneMatch(OverviewService.UpcomingInspection::canRespond);
        assertThat(overview.caretakerOverview("caretaker2").proposals()).isZero();
    }

    @Test
    void answeringTheProposalUpdatesTheOverview() throws Exception {
        var session = login("caretaker");
        var period = proposedPeriod();
        mvc.perform(post("/api/inspection-periods/" + period.getId() + "/agree").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + period.getVersion() + "}"))
            .andExpect(status().isOk());
        mvc.perform(get(PATH).session(session))
            .andExpect(jsonPath("$.proposals").value(0))
            .andExpect(jsonPath("$.upcoming[2].state").value("AGREED"))
            .andExpect(jsonPath("$.upcoming[2].canRespond").value(false));
    }

    @Test
    void aCounterProposalWaitsForTheClerk() throws Exception {
        var session = login("caretaker");
        var period = proposedPeriod();
        var body = """
            {"startsOn":"%s","endsOn":"%s","reason":"Urlaub","version":%d}"""
            .formatted(LocalDate.now().plusDays(15), LocalDate.now().plusDays(16), period.getVersion());
        mvc.perform(post("/api/inspection-periods/" + period.getId() + "/counter").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mvc.perform(get(PATH).session(session))
            .andExpect(jsonPath("$.proposals").value(0))
            .andExpect(jsonPath("$.upcoming[2].state").value("COUNTER_PROPOSED"));
    }

    @Test
    void assignedResidencesCountNotTheProfileResidence() throws Exception {
        var other = residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var flat = new FlatEntity(other, "1.01", 2);
        entities.persist(flat);
        entities.persist(new InspectionEntity(flat, null));
        var caretaker = users.findByUserKey("caretaker").orElseThrow();
        staff.save(new StaffAssignmentEntity(caretaker, other, StaffKind.CARETAKER));
        mvc.perform(get(PATH).session(login("caretaker")))
            .andExpect(jsonPath("$.upcoming.length()").value(6))
            .andExpect(jsonPath("$.upcoming[?(@.flatLabel == '1.01')].residenceName").value("Andere Wohnanlage"));
        staff.findByResidenceIdAndKind(residences.findFirstByName("Wohnanlage Sonne").orElseThrow().getId(),
            StaffKind.CARETAKER).ifPresent(staff::delete);
        mvc.perform(get(PATH).session(login("caretaker")))
            .andExpect(jsonPath("$.upcoming.length()").value(1));
    }

    @Test
    void sampleDataYieldsToExistingInspections() {
        var count = entities.createQuery("select count(i) from InspectionEntity i", Long.class);
        var before = count.getSingleResult();
        sampleData.run();
        assertThat(count.getSingleResult()).isEqualTo(before);
    }

    private InspectionPeriodEntity proposedPeriod() {
        // Vorschlag aus OverviewSampleData für WG 3.10.
        return entities.createQuery("""
            select i.period from InspectionEntity i
            where i.flat.label = '3.10' and i.flat.residence.name = 'Wohnanlage Sonne'""",
            InspectionPeriodEntity.class).getSingleResult();
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
