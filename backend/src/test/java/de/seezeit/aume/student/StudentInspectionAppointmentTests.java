package de.seezeit.aume.student;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.InspectionEntity;
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
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-student-inspection-appointment-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class StudentInspectionAppointmentTests {
    private static final String APPOINTMENT_PATH = "/api/student/inspection-appointment";
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    @Autowired private MockMvc mvc;
    @Autowired private AppUserService users;
    @Autowired private RoleRepository roles;
    @Autowired private EntityManager entities;

    @Test
    void studentReadsTheNextAnnouncedPeriodOnlyFromTheirOwnFlat() throws Exception {
        var today = LocalDate.now(BERLIN);
        var fixture = fixture(today);
        var own = inspection(fixture.flat(), today.plusDays(2), true, fixture.clerk());
        var foreign = inspection(fixture.otherFlat(), today, true, fixture.clerk());
        inspection(fixture.flat(), today, false, fixture.clerk());
        inspection(fixture.flat(), today.minusDays(7), true, fixture.clerk());
        inspection(fixture.flat(), today.plusDays(10), true, fixture.clerk());
        entities.flush();
        entities.clear();

        mvc.perform(get(APPOINTMENT_PATH).session(fixture.session())
                .param("flatId", foreign.getFlat().getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(own.getId().intValue()))
            .andExpect(jsonPath("$.startsOn").value(today.plusDays(2).toString()))
            .andExpect(jsonPath("$.endsOn").value(today.plusDays(4).toString()))
            .andExpect(jsonPath("$.appointmentChanged").value(false))
            .andExpect(jsonPath("$.showInitialInspection").value(true))
            .andExpect(jsonPath("$.exactDay").doesNotExist())
            .andExpect(jsonPath("$.changed").doesNotExist())
            .andExpect(jsonPath("$.rescheduledAt").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({"1, true", "0, false", "-1, false"})
    void changeNoticeIsVisibleOnlyBeforeTheUpdatedPeriodStarts(int daysUntilStart, boolean expectedNotice)
            throws Exception {
        var today = LocalDate.now(BERLIN);
        var fixture = fixture(today);
        var startsOn = today.plusDays(daysUntilStart);
        var period = inspection(fixture.flat(), startsOn, true, fixture.clerk()).getPeriod();
        var announcedAt = startsOn.minusDays(2).atStartOfDay(BERLIN).toInstant();
        period.setAnnouncedAt(announcedAt);
        period.setRescheduledAt(announcedAt);
        entities.flush();

        mvc.perform(get(APPOINTMENT_PATH).session(fixture.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.startsOn").value(startsOn.toString()))
            .andExpect(jsonPath("$.endsOn").value(startsOn.plusDays(2).toString()))
            .andExpect(jsonPath("$.appointmentChanged").value(expectedNotice))
            .andExpect(jsonPath("$.showInitialInspection").value(false))
            .andExpect(jsonPath("$.rescheduledAt").doesNotExist());
    }

    @Test
    void unchangedFollowUpDoesNotShowTheInitialInspectionLabel() throws Exception {
        var today = LocalDate.now(BERLIN);
        var fixture = fixture(today);
        var initial = inspection(fixture.flat(), today.plusDays(1), true, fixture.clerk());
        initial.setStatus(InspectionEntity.Status.PUBLISHED);
        var followUp = new InspectionEntity(initial, today.plusDays(3));
        followUp.setPeriod(initial.getPeriod());
        entities.persist(followUp);
        entities.flush();

        mvc.perform(get(APPOINTMENT_PATH).session(fixture.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.appointmentChanged").value(false))
            .andExpect(jsonPath("$.showInitialInspection").value(false));
    }

    @Test
    void expiredRescheduledPeriodDoesNotAppear() throws Exception {
        var today = LocalDate.now(BERLIN);
        var fixture = fixture(today);
        var period = inspection(fixture.flat(), today.minusDays(3), true, fixture.clerk()).getPeriod();
        period.setRescheduledAt(today.minusDays(4).atStartOfDay(BERLIN).toInstant());
        entities.flush();

        mvc.perform(get(APPOINTMENT_PATH).session(fixture.session()))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
    }

    @Test
    void studentWithFutureMoveOutCanReadTheAppointment() throws Exception {
        var today = LocalDate.now(BERLIN);
        var fixture = fixture(today);
        fixture.membership().setValidTo(today.plusDays(1));
        var own = inspection(fixture.flat(), today.plusDays(2), true, fixture.clerk());
        entities.flush();

        mvc.perform(get(APPOINTMENT_PATH).session(fixture.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(own.getId().intValue()))
            .andExpect(jsonPath("$.appointmentChanged").value(false));
    }

    @ParameterizedTest
    @CsvSource({"ended", "endsToday", "future", "missing", "ambiguous", "ambiguousWithFutureMoveOut"})
    void studentWithoutOneActiveFlatReceivesNoAppointment(String membershipState) throws Exception {
        var today = LocalDate.now(BERLIN);
        var fixture = fixture(today);
        var membership = fixture.membership();
        var appointment = inspection(fixture.flat(), today.plusDays(1), true, fixture.clerk());
        appointment.getPeriod().setEndsOn(today.plusDays(2));
        switch (membershipState) {
            case "ended" -> membership.setValidTo(today.minusDays(1));
            case "endsToday" -> membership.setValidTo(today);
            case "future" -> membership.setValidFrom(today.plusDays(1));
            case "missing" -> entities.remove(membership);
            case "ambiguous", "ambiguousWithFutureMoveOut" -> {
                var otherMembership = new MembershipEntity(fixture.otherFlat(), membership.getUser(), today);
                if (membershipState.equals("ambiguousWithFutureMoveOut")) {
                    otherMembership.setValidTo(today.plusDays(1));
                }
                entities.persist(otherMembership);
            }
            default -> throw new IllegalArgumentException(membershipState);
        }
        entities.flush();
        entities.clear();

        mvc.perform(get(APPOINTMENT_PATH).session(fixture.session())).andExpect(status().isNoContent());
    }

    @Test
    void equallyEarlyAppointmentsAreNotSelectedArbitrarily() throws Exception {
        var today = LocalDate.now(BERLIN);
        var fixture = fixture(today);
        var start = today.plusDays(1);
        inspection(fixture.flat(), start, true, fixture.clerk());
        inspection(fixture.flat(), start, true, fixture.clerk());

        mvc.perform(get(APPOINTMENT_PATH).session(fixture.session())).andExpect(status().isNoContent());
    }

    @Test
    void appointmentEndpointRejectsAnonymousAndStaffRequests() throws Exception {
        mvc.perform(get(APPOINTMENT_PATH)).andExpect(status().isUnauthorized());
        var residence = new ResidenceEntity("Termintest Wohnanlage");
        entities.persist(residence);
        mvc.perform(get(APPOINTMENT_PATH).session(session(clerk(residence)))).andExpect(status().isForbidden());
    }

    private Fixture fixture(LocalDate today) {
        var residence = new ResidenceEntity("Termintest Wohnanlage");
        entities.persist(residence);
        var flat = new FlatEntity(residence, "T-01", 2);
        var otherFlat = new FlatEntity(residence, "T-02", 2);
        entities.persist(flat);
        entities.persist(otherFlat);
        var role = roles.findByName("STUDENT").orElseThrow();
        var student = new StudentEntity("appointment-test-student", "Termintest Studierender", Set.of(role), residence);
        entities.persist(student);
        var membership = new MembershipEntity(flat, student, today.minusDays(2));
        entities.persist(membership);
        return new Fixture(flat, otherFlat, membership, clerk(residence), session(student));
    }

    private ClerkEntity clerk(ResidenceEntity residence) {
        var role = roles.findByName("CLERK").orElseThrow();
        var clerk = new ClerkEntity("appointment-test-clerk", "Termintest Sachbearbeitung", Set.of(role), residence);
        entities.persist(clerk);
        return clerk;
    }

    private InspectionEntity inspection(FlatEntity flat, LocalDate start, boolean announced, ClerkEntity clerk) {
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

    private MockHttpSession session(UserEntity user) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            user.getUserKey(), null, users.authorities(user)));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private record Fixture(FlatEntity flat, FlatEntity otherFlat, MembershipEntity membership,
            ClerkEntity clerk, MockHttpSession session) {}
}
