package de.seezeit.aume.student;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.user.UserRepository;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-student-inspection-appointment-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class StudentInspectionAppointmentTests {
    private static final String APPOINTMENT_PATH = "/api/student/inspection-appointment";
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private FlatRepository flats;
    @Autowired private EntityManager entities;

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
            .andExpect(jsonPath("$.showInitialInspection").value(false))
            .andExpect(jsonPath("$.rescheduledAt").doesNotExist());
    }

    @Test
    void unchangedFollowUpDoesNotShowTheInitialInspectionLabel() throws Exception {
        var today = LocalDate.now(BERLIN);
        var initial = sampleInspection();
        var period = initial.getPeriod();
        period.setStartsOn(today.plusDays(1));
        period.setEndsOn(today.plusDays(3));
        initial.setStatus(InspectionEntity.Status.PUBLISHED);
        var followUp = new InspectionEntity(initial, today.plusDays(3));
        followUp.setPeriod(period);
        entities.persist(followUp);
        entities.flush();

        mvc.perform(get(APPOINTMENT_PATH).session(login("student")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.appointmentChanged").value(false))
            .andExpect(jsonPath("$.showInitialInspection").value(false));
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
