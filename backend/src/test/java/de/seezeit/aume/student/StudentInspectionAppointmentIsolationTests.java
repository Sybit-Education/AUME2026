package de.seezeit.aume.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.inspection.StudentAppointmentDetailsService;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.user.ClerkEntity;
import de.seezeit.aume.user.RoleRepository;
import de.seezeit.aume.user.StudentEntity;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:aume-us15-isolation",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class StudentInspectionAppointmentIsolationTests {
    private static final String STUDENT_INSPECTION_APPOINTMENT_PATH = "/api/student/inspection-appointment";
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entities;
    @Autowired private RoleRepository roles;
    @Autowired private AppUserService users;
    @Autowired private StudentAppointmentDetailsService appointmentDetails;

    @Test
    void studentInspectionAppointmentsStayIsolatedBetweenTwoStudentSessions() throws Exception {
        var today = LocalDate.now(ZoneId.of("Europe/Berlin"));
        var residence = new ResidenceEntity("US-15 Testwohnanlage");
        entities.persist(residence);
        var flatA = new FlatEntity(residence, "S-01", 1);
        var flatB = new FlatEntity(residence, "S-02", 1);
        entities.persist(flatA);
        entities.persist(flatB);

        var studentRole = roles.findByName("STUDENT").orElseThrow();
        var studentA = new StudentEntity("us15-student-a", "Student A", Set.of(studentRole), residence);
        var studentB = new StudentEntity("us15-student-b", "Student B", Set.of(studentRole), residence);
        entities.persist(studentA);
        entities.persist(studentB);
        entities.persist(new MembershipEntity(flatA, studentA, today));
        entities.persist(new MembershipEntity(flatB, studentB, today));
        var sessionA = studentSession(studentA);
        var sessionB = studentSession(studentB);
        assertThat(sessionA.getId()).isNotEqualTo(sessionB.getId());

        var clerk = users.requireUser("clerk");
        var appointmentA = announcedAppointment(flatA, clerk, today.plusDays(1));
        appointmentA.getPeriod().setRescheduledAt(appointmentA.getPeriod().getAnnouncedAt());
        entities.flush();

        mvc.perform(get("/api/me").session(sessionA))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userKey").value(studentA.getUserKey()));
        mvc.perform(get("/api/me").session(sessionB))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userKey").value(studentB.getUserKey()));

        expectAppointment(sessionA, appointmentA, true);
        mvc.perform(get(STUDENT_INSPECTION_APPOINTMENT_PATH).session(sessionB))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get(STUDENT_INSPECTION_APPOINTMENT_PATH).session(sessionB)
                .param("flatId", flatA.getId().toString()))
            .andExpect(status().isNoContent()).andExpect(content().string(""));

        var appointmentB = announcedAppointment(flatB, clerk, today.plusDays(5));
        entities.flush();

        expectAppointment(sessionB, appointmentB, false);
        expectAppointment(sessionA, appointmentA, true);
        expectAppointment(sessionB, appointmentB, false, flatA.getId().toString());
        expectAppointment(sessionA, appointmentA, true, flatB.getId().toString());
        mvc.perform(get(STUDENT_INSPECTION_APPOINTMENT_PATH).param("view", "details").session(sessionB)
                .param("flatId", flatA.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(appointmentB.getId().intValue()));
    }

    @Test
    void detailsFollowTheSameInspectionThroughDraftAndPublication() throws Exception {
        var today = LocalDate.now(BERLIN);
        var fixture = fixture(today.minusDays(5));
        var older = announcedAppointment(fixture.flat(), fixture.clerk(), today.minusDays(4));
        publish(older, today.minusDays(2).atStartOfDay(BERLIN).toInstant());
        var current = announcedAppointment(fixture.flat(), fixture.clerk(), today);
        current.getPeriod().setWindowStart(LocalTime.of(9, 0));
        current.getPeriod().setWindowEnd(LocalTime.of(16, 0));

        mvc.perform(details(fixture))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.id").value(current.getId().intValue()))
            .andExpect(jsonPath("$.startsOn").value(today.toString()))
            .andExpect(jsonPath("$.endsOn").value(today.plusDays(2).toString()))
            .andExpect(jsonPath("$.status").value("ANNOUNCED"))
            .andExpect(jsonPath("$.display").value("UPCOMING"))
            .andExpect(jsonPath("$.windowStart").value("09:00"))
            .andExpect(jsonPath("$.windowEnd").value("16:00"))
            .andExpect(jsonPath("$.kind").value("INITIAL"))
            .andExpect(jsonPath("$.showInitialInspection").value(true));
        mvc.perform(get(STUDENT_INSPECTION_APPOINTMENT_PATH).session(fixture.session()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.windowStart").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.kind").doesNotHaveJsonPath());

        current.setStatus(InspectionEntity.Status.DRAFT);
        mvc.perform(details(fixture))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(current.getId().intValue()))
            .andExpect(jsonPath("$.status").value("ANNOUNCED"))
            .andExpect(jsonPath("$.display").value("CURRENT"));
        mvc.perform(get(STUDENT_INSPECTION_APPOINTMENT_PATH).session(fixture.session()))
            .andExpect(status().isNoContent());

        current.setStatus(InspectionEntity.Status.FINALIZED);
        mvc.perform(details(fixture)).andExpect(status().isNoContent());

        publish(current, Instant.now());
        mvc.perform(details(fixture))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(current.getId().intValue()))
            .andExpect(jsonPath("$.status").value("RESULT_AVAILABLE"))
            .andExpect(jsonPath("$.display").value("PAST"));
    }

    @Test
    void expiredDraftKeepsItsOwnPeriodWithoutAnAnnouncementOrOlderResult() throws Exception {
        var today = LocalDate.now(BERLIN);
        var fixture = fixture(today.minusDays(20));
        var older = announcedAppointment(fixture.flat(), fixture.clerk(), today.minusDays(12));
        publish(older, Instant.now());
        var draft = announcedAppointment(fixture.flat(), fixture.clerk(), today.minusDays(3));
        draft.setStatus(InspectionEntity.Status.DRAFT);
        draft.getPeriod().setWindowStart(LocalTime.of(9, 0));

        mvc.perform(details(fixture))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(draft.getId().intValue()))
            .andExpect(jsonPath("$.status").value(nullValue()))
            .andExpect(jsonPath("$.display").value("PAST"))
            .andExpect(jsonPath("$.windowEnd").value(nullValue()));
        draft.setStatus(InspectionEntity.Status.PLANNED);
        mvc.perform(details(fixture)).andExpect(status().isNoContent());
        draft.setStatus(InspectionEntity.Status.FINALIZED);
        mvc.perform(details(fixture)).andExpect(status().isNoContent());
    }

    @Test
    void detailPublicationUsesTheInclusiveBerlinMoveInBoundary() throws Exception {
        var today = LocalDate.now(BERLIN);
        var fixture = fixture(today);
        var published = announcedAppointment(fixture.flat(), fixture.clerk(), today.minusDays(4));
        var movedIn = today.atStartOfDay(BERLIN).toInstant();
        publish(published, movedIn.minusSeconds(1));
        mvc.perform(details(fixture)).andExpect(status().isNoContent());
        published.setPublishedAt(null);
        mvc.perform(details(fixture)).andExpect(status().isNoContent());
        published.setPublishedAt(movedIn);
        mvc.perform(details(fixture)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESULT_AVAILABLE"));
    }

    @Test
    void ambiguousOpenAppointmentsDoNotFallBackAndFollowUpUsesItsOwnId() throws Exception {
        var today = LocalDate.now(BERLIN);
        var fixture = fixture(today.minusDays(10));
        var older = announcedAppointment(fixture.flat(), fixture.clerk(), today.minusDays(5));
        publish(older, Instant.now());
        var competing = announcedAppointment(fixture.flat(), fixture.clerk(), today.plusDays(1));
        var followUp = new InspectionEntity(older, today.plusDays(5));
        followUp.setPeriod(competing.getPeriod());
        entities.persist(followUp);
        mvc.perform(details(fixture)).andExpect(status().isNoContent());

        competing.setPeriod(null);
        mvc.perform(details(fixture)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.status").value("ANNOUNCED"))
            .andExpect(jsonPath("$.kind").value("FOLLOW_UP"))
            .andExpect(jsonPath("$.showInitialInspection").value(false));
        publish(followUp, Instant.now());
        mvc.perform(details(fixture)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.kind").value("FOLLOW_UP"))
            .andExpect(jsonPath("$.status").value("RESULT_AVAILABLE"));

        followUp.getPeriod().setAnnouncedAt(null);
        older.setPeriod(null);
        mvc.perform(details(fixture)).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void detailsKeepTheControlKindWhenTheRescheduleNoticeEnds(int round) throws Exception {
        var today = LocalDate.now(BERLIN);
        var newStart = today.plusDays(3);
        var fixture = fixture(today.minusDays(10));
        var selected = new InspectionEntity(fixture.flat(), null);
        entities.persist(selected);
        for (int step = 0; step < round; step++) {
            publish(selected, today.minusDays(2).atStartOfDay(BERLIN).toInstant());
            selected = new InspectionEntity(selected, newStart);
            entities.persist(selected);
        }
        var period = announcedPeriod(fixture.flat(), fixture.clerk(), newStart);
        selected.setPeriod(period);
        assertThat(selected.getRound()).isEqualTo(round);
        var expectedKind = round == 0 ? StudentAppointmentDetailsService.Kind.INITIAL
            : StudentAppointmentDetailsService.Kind.FOLLOW_UP;
        mvc.perform(details(fixture))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(selected.getId().intValue()))
            .andExpect(jsonPath("$.kind").value(expectedKind.name()))
            .andExpect(jsonPath("$.appointmentChanged").value(false))
            .andExpect(jsonPath("$.showInitialInspection").value(round == 0));

        period.setRescheduledAt(period.getAnnouncedAt().plusSeconds(1));
        mvc.perform(details(fixture))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(selected.getId().intValue()))
            .andExpect(jsonPath("$.kind").value(expectedKind.name()))
            .andExpect(jsonPath("$.appointmentChanged").value(true))
            .andExpect(jsonPath("$.showInitialInspection").value(false));

        var onStart = appointmentDetails.find(fixture.student().getUserKey(), newStart);
        assertThat(onStart).isNotNull();
        assertThat(onStart.id()).isEqualTo(selected.getId());
        assertThat(onStart.kind()).isEqualTo(expectedKind);
        assertThat(onStart.appointmentChanged()).isFalse();
        assertThat(onStart.showInitialInspection()).isFalse();
    }

    @Test
    void detailsRequireOneActiveFlatAndAValidView() throws Exception {
        var today = LocalDate.now(BERLIN);
        var fixture = fixture(today);
        announcedAppointment(fixture.flat(), fixture.clerk(), today);
        var other = new FlatEntity(fixture.flat().getResidence(), "Other", 2);
        entities.persist(other);
        var otherMembership = new MembershipEntity(other, fixture.student(), today.plusDays(1));
        entities.persist(otherMembership);
        mvc.perform(details(fixture)).andExpect(status().isOk());
        otherMembership.setValidFrom(today);
        mvc.perform(details(fixture)).andExpect(status().isNoContent());
        otherMembership.setValidTo(today);
        mvc.perform(details(fixture)).andExpect(status().isOk());
        fixture.membership().setValidTo(today);
        mvc.perform(details(fixture)).andExpect(status().isNoContent());
        otherMembership.setValidTo(null);
        var newAppointment = announcedAppointment(other, fixture.clerk(), today.plusDays(1));
        mvc.perform(details(fixture)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(newAppointment.getId().intValue()));
        var foreignResidence = new ResidenceEntity("Fremde Wohnanlage");
        entities.persist(foreignResidence);
        newAppointment.getPeriod().setResidence(foreignResidence);
        mvc.perform(details(fixture)).andExpect(status().isNoContent());

        mvc.perform(get(STUDENT_INSPECTION_APPOINTMENT_PATH).param("view", "unknown").session(fixture.session()))
            .andExpect(status().isBadRequest());
        mvc.perform(get(STUDENT_INSPECTION_APPOINTMENT_PATH).param("view", "details"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(STUDENT_INSPECTION_APPOINTMENT_PATH).param("view", "details")
                .session(studentSession(fixture.clerk())))
            .andExpect(status().isForbidden());
    }

    private Fixture fixture(LocalDate validFrom) {
        var residence = new ResidenceEntity("US-67 Testwohnanlage");
        entities.persist(residence);
        var flat = new FlatEntity(residence, "S-01", 2);
        entities.persist(flat);
        var role = roles.findByName("STUDENT").orElseThrow();
        var student = new StudentEntity("us67-student", "Student", Set.of(role), residence);
        entities.persist(student);
        var membership = new MembershipEntity(flat, student, validFrom);
        entities.persist(membership);
        var clerkRole = roles.findByName("CLERK").orElseThrow();
        var clerk = new ClerkEntity("us67-clerk", "Sachbearbeitung", Set.of(clerkRole), residence);
        entities.persist(clerk);
        return new Fixture(flat, student, membership, studentSession(student), clerk);
    }

    private MockHttpServletRequestBuilder details(Fixture fixture) {
        return get(STUDENT_INSPECTION_APPOINTMENT_PATH).param("view", "details").session(fixture.session());
    }

    private static void publish(InspectionEntity inspection, Instant publishedAt) {
        inspection.setStatus(InspectionEntity.Status.PUBLISHED);
        inspection.setOverallResult(InspectionEntity.Result.PASSED);
        inspection.setPublishedAt(publishedAt);
    }

    private record Fixture(FlatEntity flat, StudentEntity student, MembershipEntity membership,
            MockHttpSession session, UserEntity clerk) {}

    private MockHttpSession studentSession(UserEntity student) {
        // The dev login only supports the fixed demo accounts.
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            student.getUserKey(), null, users.authorities(student)));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private InspectionEntity announcedAppointment(FlatEntity flat, UserEntity clerk, LocalDate start) {
        var appointment = new InspectionEntity(flat, announcedPeriod(flat, clerk, start));
        entities.persist(appointment);
        return appointment;
    }

    private InspectionPeriodEntity announcedPeriod(FlatEntity flat, UserEntity clerk, LocalDate start) {
        var period = new InspectionPeriodEntity(flat.getResidence(), start, start.plusDays(2), clerk);
        period.setAnnouncedBy(clerk);
        period.setAnnouncedAt(Instant.now());
        entities.persist(period);
        return period;
    }

    private void expectAppointment(MockHttpSession session, InspectionEntity appointment, boolean appointmentChanged,
            String... foreignFlatIds) throws Exception {
        var request = get(STUDENT_INSPECTION_APPOINTMENT_PATH).session(session);
        if (foreignFlatIds.length > 0) request.param("flatId", foreignFlatIds);
        mvc.perform(request)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(appointment.getId().intValue()))
            .andExpect(jsonPath("$.startsOn").value(appointment.getPeriod().getStartsOn().toString()))
            .andExpect(jsonPath("$.endsOn").value(appointment.getPeriod().getEndsOn().toString()))
            .andExpect(jsonPath("$.appointmentChanged").value(appointmentChanged));
    }
}
