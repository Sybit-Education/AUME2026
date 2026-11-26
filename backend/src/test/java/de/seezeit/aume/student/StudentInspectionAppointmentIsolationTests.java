package de.seezeit.aume.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.user.RoleRepository;
import de.seezeit.aume.user.StudentEntity;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
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

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:aume-us15-isolation",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class StudentInspectionAppointmentIsolationTests {
    private static final String STUDENT_INSPECTION_APPOINTMENT_PATH = "/api/student/inspection-appointment";

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entities;
    @Autowired private RoleRepository roles;
    @Autowired private AppUserService users;

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
    }

    private MockHttpSession studentSession(StudentEntity student) {
        // The dev login only supports the fixed demo accounts.
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            student.getUserKey(), null, users.authorities(student)));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private InspectionEntity announcedAppointment(FlatEntity flat, UserEntity clerk, LocalDate start) {
        var period = new InspectionPeriodEntity(flat.getResidence(), start, start.plusDays(2), clerk);
        period.setAnnouncedBy(clerk);
        period.setAnnouncedAt(Instant.now());
        entities.persist(period);
        var appointment = new InspectionEntity(flat, period);
        entities.persist(appointment);
        return appointment;
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
