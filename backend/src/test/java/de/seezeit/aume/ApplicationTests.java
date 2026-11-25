package de.seezeit.aume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.residence.StaffAssignmentRepository;
import de.seezeit.aume.user.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-dev-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ApplicationTests {
    private static final String APPOINTMENT_PATH = "/api/student/inspection-appointment";
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private StaffAssignmentRepository staffAssignments;
    @Autowired private RoleRepository roles;
    @Autowired private PrivilegeRepository privileges;
    @Autowired private SecurityData securityData;
    @Autowired private SampleData sampleData;
    @Autowired private StudentSampleData studentSampleData;
    @Autowired private EntityManager entities;

    @ParameterizedTest
    @CsvSource({"student,STUDENT,GREETING_READ", "caretaker,CARETAKER,INSPECTION_PERFORM",
        "clerk,CLERK,RESIDENCE_ASSIGN_STAFF"})
    void mockUsersReceiveTheirOwnRolesAndPrivileges(String key, String role, String privilege) throws Exception {
        var session = login(key);
        mvc.perform(get("/api/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userKey").value(key))
            .andExpect(jsonPath("$.roles[0]").value(role))
            .andExpect(jsonPath("$.privileges", hasItem(privilege)))
            .andExpect(jsonPath("$.identitySubject").doesNotExist());
    }

    @Test
    void anonymousRequestsAndSpoofedHeadersDoNotAuthenticate() throws Exception {
        for (String path : new String[]{"/api/me", "/api/greeting", "/api/residences", "/api/users"}) {
            mvc.perform(get(path).header("X-Dev-User", "student")).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void studentCanReadGreetingButCannotReadResidences() throws Exception {
        var session = login("student");
        mvc.perform(get("/api/greeting").session(session).header("X-Dev-User", "clerk"))
            .andExpect(status().isOk())
            .andExpect(content().string("Hallo Studierende, willkommen in Wohnanlage Sonne."));
        mvc.perform(get("/api/residences").session(session)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
        "en,en,'Hello Studierende, welcome to Wohnanlage Sonne.'",
        "en-US,en,'Hello Studierende, welcome to Wohnanlage Sonne.'",
        "de-CH,de,'Hallo Studierende, willkommen in Wohnanlage Sonne.'",
        "fr,de,'Hallo Studierende, willkommen in Wohnanlage Sonne.'"
    })
    void greetingUsesTheRequestedSupportedLanguage(String requested, String resolved, String greeting) throws Exception {
        mvc.perform(get("/api/greeting").session(login("student")).header("Accept-Language", requested))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Language", resolved))
            .andExpect(content().string(greeting));
    }

    @Test
    void caretakerCanOnlyReadTheirAssignedResidence() throws Exception {
        residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var session = login("caretaker");
        mvc.perform(get("/api/residences").session(session).param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.size").value(2))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Wohnanlage Sonne"));
        mvc.perform(get("/api/greeting").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/residences").session(session).param("page", "1").param("size", "1"))
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void clerkReadsOnlyAssignedResidencesSortedByNameWithFlatsAndStaff() throws Exception {
        residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var session = login("clerk");
        mvc.perform(get("/api/residences").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].name").value("Egg"))
            .andExpect(jsonPath("$.content[0].flats").value(org.hamcrest.Matchers.contains("E-12", "E-14")))
            .andExpect(jsonPath("$.content[0].caretaker.name").value("Objektbetreuung Egg"))
            .andExpect(jsonPath("$.content[0].deputy").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.content[1].name").value("Wohnanlage Sonne"))
            .andExpect(jsonPath("$.content[1].flats").value(org.hamcrest.Matchers.contains("S-01", "S-02")))
            .andExpect(jsonPath("$.content[1].caretaker.name").value("Objektbetreuung"))
            .andExpect(jsonPath("$.content[1].deputy.name").value("Objektbetreuung Petershausen"))
            .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.hasItem("Petershausen"))));
        mvc.perform(get("/api/residences").session(session).param("page", "1").param("size", "1"))
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Wohnanlage Sonne"));
    }

    @Test
    void loginAndLogoutRequireCsrfAndInvalidateTheSession() throws Exception {
        mvc.perform(post("/api/auth/dev-login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"userKey\":\"student\"}")).andExpect(status().isForbidden());
        var csrfResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String previousId = session.getId();
        String header = JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.headerName");
        String token = JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/auth/dev-login").session(session).with(request -> {
                request.setRequestedSessionId(previousId);
                request.setRequestedSessionIdValid(true);
                return request;
            }).header(header, token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"student\"}"))
            .andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(previousId);
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).header(header, token)).andExpect(status().isForbidden());
        var freshCsrf = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        String freshToken = JsonPath.read(freshCsrf.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/auth/logout").session(session).header(header, freshToken))
            .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void clientsCannotChooseRolesOrLogInAsAnArbitraryDatabaseUser() throws Exception {
        var session = (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userKey\":\"student\",\"roles\":[\"CLERK\"],\"privileges\":[\"RESIDENCE_READ\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("STUDENT"))
            .andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/residences").session(session)).andExpect(status().isForbidden());
        var student = users.findByUserKey("student").orElseThrow();
        users.save(new StudentEntity("another-user", "Other", student.getRoles(), student.getResidence()));
        mvc.perform(post("/api/auth/dev-login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userKey\":\"another-user\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/dev-login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void multipleRolesCombineAuthoritiesWithoutDuplicates() throws Exception {
        var student = users.findByUserKey("student").orElseThrow();
        student.getRoles().add(roles.findByName("CLERK").orElseThrow());
        student.getRoles().add(roles.findByName("CARETAKER").orElseThrow());
        var session = login("student");
        mvc.perform(get("/api/me").session(session))
            .andExpect(jsonPath("$.roles.length()").value(3))
            .andExpect(jsonPath("$.privileges.length()").value(10));
        mvc.perform(get("/api/greeting").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/residences").session(session)).andExpect(status().isOk());
    }

    @Test
    void repositoriesAndWritesAreNotExposed() throws Exception {
        var session = login("clerk");
        for (String path : new String[]{"/api/users", "/api/users/search/findByUserKey?userKey=student",
                "/api/users/1/roles", "/api/roles", "/api/privileges", "/api/residences/1"}) {
            mvc.perform(get(path).session(session)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/users").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/residences").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void seedersAreIdempotent() {
        securityData.run();
        sampleData.run();
        assertThat(users.count()).isEqualTo(6);
        assertThat(residences.count()).isEqualTo(3);
        assertThat(flats.count()).isEqualTo(9);
        assertThat(staffAssignments.count()).isEqualTo(8);
        assertThat(roles.count()).isEqualTo(3);
        assertThat(privileges.count()).isEqualTo(10);
    }

    @Test
    void sampleInspectionIsPersistedForTheStudentWithoutDuplicates() {
        var originalId = sampleInspection().getId();
        var inspectionCount = entities.createQuery("select count(i) from InspectionEntity i", Long.class)
            .getSingleResult();
        var periodCount = entities.createQuery("select count(p) from InspectionPeriodEntity p", Long.class)
            .getSingleResult();
        studentSampleData.run();
        studentSampleData.run();
        entities.flush();
        entities.clear();
        assertThat(entities.createQuery("select count(i) from InspectionEntity i", Long.class)
            .getSingleResult()).isEqualTo(inspectionCount);

        var memberships = entities.createQuery("""
            select m from MembershipEntity m where m.user.userKey = 'student' and m.validTo is null
            """, MembershipEntity.class).getResultList();
        assertThat(memberships).hasSize(1);
        var flat = memberships.getFirst().getFlat();
        assertThat(flat.getLabel()).isEqualTo("S-01");
        assertThat(flat.getResidence().getName()).isEqualTo("Wohnanlage Sonne");

        var inspections = entities.createQuery("""
            select i from InspectionEntity i where i.flat = :flat and i.period is not null and i.round = 0
            """, InspectionEntity.class).setParameter("flat", flat).getResultList();
        assertThat(inspections).hasSize(1);
        var inspection = inspections.getFirst();
        assertThat(inspection.getId()).isEqualTo(originalId);
        assertThat(inspection.getStatus()).isEqualTo(InspectionEntity.Status.PLANNED);
        assertThat(inspection.getInspectedAt()).isNull();
        var period = inspection.getPeriod();
        assertThat(period.getResidence().getId()).isEqualTo(flat.getResidence().getId());
        assertThat(period.getStartsOn()).isEqualTo(LocalDate.of(2026, 12, 7));
        assertThat(period.getEndsOn()).isEqualTo(LocalDate.of(2026, 12, 11));
        assertThat(period.getAnnouncedAt()).isNotNull();
        assertThat(period.getAnnouncedBy().getUserKey()).isEqualTo("clerk");
        assertThat(entities.createQuery("""
            select count(p) from InspectionPeriodEntity p
            where p.residence = :residence and p.startsOn = :startsOn and p.endsOn = :endsOn
            """, Long.class)
            .setParameter("residence", flat.getResidence())
            .setParameter("startsOn", period.getStartsOn())
            .setParameter("endsOn", period.getEndsOn())
            .getSingleResult()).isEqualTo(1L);
        assertThat(entities.createQuery("select count(p) from InspectionPeriodEntity p", Long.class)
            .getSingleResult()).isEqualTo(periodCount);
    }

    @Test
    void studentReadsTheNextAnnouncedPeriodOnlyFromTheirOwnFlat() throws Exception {
        var today = LocalDate.now(BERLIN);
        var own = sampleInspection();
        own.getPeriod().setStartsOn(today.plusDays(2));
        own.getPeriod().setEndsOn(today.plusDays(4));
        var otherFlat = flats.findByResidenceIdAndLabel(own.getFlat().getResidence().getId(), "S-02")
            .orElseThrow();
        var foreign = inspection(otherFlat, today, true);
        inspection(own.getFlat(), today, false);
        inspection(own.getFlat(), today.minusDays(7), true);
        inspection(own.getFlat(), today.plusDays(10), true);
        entities.flush();
        entities.clear();

        mvc.perform(get(APPOINTMENT_PATH).session(login("student")).param("flatId", foreign.getFlat().getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(own.getId().intValue()))
            .andExpect(jsonPath("$.startsOn").value(today.plusDays(2).toString()))
            .andExpect(jsonPath("$.endsOn").value(today.plusDays(4).toString()))
            .andExpect(jsonPath("$.appointmentChanged").value(false))
            .andExpect(jsonPath("$.exactDay").doesNotExist())
            .andExpect(jsonPath("$.changed").doesNotExist())
            .andExpect(jsonPath("$.rescheduledAt").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({"1, true", "0, false", "-1, false"})
    void changeNoticeIsVisibleOnlyBeforeTheUpdatedPeriodStarts(int daysUntilStart, boolean expectedNotice)
            throws Exception {
        var today = LocalDate.now(BERLIN);
        var period = sampleInspection().getPeriod();
        var startsOn = today.plusDays(daysUntilStart);
        period.setStartsOn(startsOn);
        period.setEndsOn(startsOn.plusDays(2));
        var announcedAt = startsOn.minusDays(2).atStartOfDay(BERLIN).toInstant();
        period.setAnnouncedAt(announcedAt);
        period.setRescheduledAt(announcedAt);
        entities.flush();

        mvc.perform(get(APPOINTMENT_PATH).session(login("student")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.startsOn").value(startsOn.toString()))
            .andExpect(jsonPath("$.endsOn").value(startsOn.plusDays(2).toString()))
            .andExpect(jsonPath("$.appointmentChanged").value(expectedNotice))
            .andExpect(jsonPath("$.rescheduledAt").doesNotExist());
    }

    @Test
    void expiredRescheduledPeriodDoesNotAppear() throws Exception {
        var today = LocalDate.now(BERLIN);
        var period = sampleInspection().getPeriod();
        period.setStartsOn(today.minusDays(3));
        period.setEndsOn(today.minusDays(1));
        period.setRescheduledAt(today.minusDays(4).atStartOfDay(BERLIN).toInstant());
        entities.flush();

        mvc.perform(get(APPOINTMENT_PATH).session(login("student")))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
    }

    @Test
    void studentWithFutureMoveOutCanReadTheAppointment() throws Exception {
        var today = LocalDate.now(BERLIN);
        var own = sampleInspection();
        var membership = entities.createQuery("""
            select m from MembershipEntity m where m.user.userKey = 'student' and m.validTo is null
            """, MembershipEntity.class).getSingleResult();
        membership.setValidTo(today.plusDays(1));
        own.getPeriod().setStartsOn(today.plusDays(2));
        own.getPeriod().setEndsOn(today.plusDays(4));
        entities.flush();

        mvc.perform(get(APPOINTMENT_PATH).session(login("student")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(own.getId().intValue()))
            .andExpect(jsonPath("$.appointmentChanged").value(false));
    }

    @ParameterizedTest
    @CsvSource({"ended", "endsToday", "future", "missing", "ambiguous", "ambiguousWithFutureMoveOut"})
    void studentWithoutOneActiveFlatReceivesNoAppointment(String membershipState) throws Exception {
        var today = LocalDate.now(BERLIN);
        var membership = entities.createQuery("""
            select m from MembershipEntity m where m.user.userKey = 'student' and m.validTo is null
            """, MembershipEntity.class).getSingleResult();
        var period = sampleInspection().getPeriod();
        period.setStartsOn(today.plusDays(1));
        period.setEndsOn(today.plusDays(2));
        switch (membershipState) {
            case "ended" -> membership.setValidTo(today.minusDays(1));
            case "endsToday" -> membership.setValidTo(today);
            case "future" -> membership.setValidFrom(today.plusDays(1));
            case "missing" -> entities.remove(membership);
            case "ambiguous", "ambiguousWithFutureMoveOut" -> {
                var otherFlat = flats.findByResidenceIdAndLabel(membership.getFlat().getResidence().getId(), "S-02")
                    .orElseThrow();
                var otherMembership = new MembershipEntity(otherFlat, membership.getUser(), today);
                if (membershipState.equals("ambiguousWithFutureMoveOut")) {
                    otherMembership.setValidTo(today.plusDays(1));
                }
                entities.persist(otherMembership);
            }
            default -> throw new IllegalArgumentException(membershipState);
        }
        entities.flush();
        entities.clear();

        mvc.perform(get(APPOINTMENT_PATH).session(login("student"))).andExpect(status().isNoContent());
    }

    @Test
    void equallyEarlyAppointmentsAreNotSelectedArbitrarily() throws Exception {
        var own = sampleInspection();
        var start = LocalDate.now(BERLIN).plusDays(1);
        own.getPeriod().setStartsOn(start);
        own.getPeriod().setEndsOn(start.plusDays(2));
        inspection(own.getFlat(), start, true);

        mvc.perform(get(APPOINTMENT_PATH).session(login("student"))).andExpect(status().isNoContent());
    }

    @Test
    void appointmentEndpointRejectsAnonymousAndStaffRequests() throws Exception {
        mvc.perform(get(APPOINTMENT_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(APPOINTMENT_PATH).session(login("clerk"))).andExpect(status().isForbidden());
    }

    private InspectionEntity sampleInspection() {
        return entities.createQuery("""
            select i from InspectionEntity i join i.period p
            where i.flat.label = 'S-01' and i.flat.residence.name = 'Wohnanlage Sonne'
              and i.round = 0 and p.startsOn = :startsOn and p.endsOn = :endsOn
            """, InspectionEntity.class)
            .setParameter("startsOn", LocalDate.of(2026, 12, 7))
            .setParameter("endsOn", LocalDate.of(2026, 12, 11))
            .getSingleResult();
    }

    private InspectionEntity inspection(FlatEntity flat, LocalDate start, boolean announced) {
        var clerk = users.findByUserKey("clerk").orElseThrow();
        var period = new InspectionPeriodEntity(flat.getResidence(), start, start.plusDays(2), clerk);
        if (announced) {
            period.setAnnouncedAt(Instant.now());
            period.setAnnouncedBy(clerk);
        }
        entities.persist(period);
        var inspection = new InspectionEntity(flat, period);
        entities.persist(inspection);
        return inspection;
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
