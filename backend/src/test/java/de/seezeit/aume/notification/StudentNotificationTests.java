package de.seezeit.aume.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.notification.InspectionNotificationEntity.Kind;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.user.ClerkEntity;
import de.seezeit.aume.user.StudentEntity;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-student-notifications;MODE=PostgreSQL")
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class StudentNotificationTests {
    private static final String PATH = "/api/student/notifications";
    private static final LocalDate TODAY = LocalDate.of(2040, 12, 3);
    private static final Instant ANNOUNCED_AT = Instant.parse("2040-12-01T08:00:00Z");
    private static final Instant NOW = Instant.parse("2040-12-02T23:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2040-12-02T08:00:00Z");

    @MockitoBean private InspectionNotificationScheduler scheduler;
    @MockitoBean(name = "inspectionNotificationClock") private Clock clock;
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entities;
    @Autowired private NotificationRepository notifications;
    @Autowired private PlatformTransactionManager transactions;

    @BeforeEach
    void setUpClock() {
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void showsStructuredMessagesWithStableOffsetPaginationWithoutMarkingThemRead() throws Exception {
        var data = fixture("pagination");
        var older = deliver(data.student(), message(data.inspection(), Kind.ANNOUNCEMENT));
        var oneDay = deliver(data.student(), message(data.inspection(), Kind.REMINDER_1_DAY));
        var sevenDays = deliver(data.student(), message(data.inspection(), Kind.REMINDER_7_DAYS));
        entities.persist(new NotificationEntity(data.student(), "OTHER", "Allgemeiner Hinweis", null));
        setCreatedAt(older.getId(), CREATED_AT.minusSeconds(1));
        setCreatedAt(oneDay.getId(), CREATED_AT);
        setCreatedAt(sevenDays.getId(), CREATED_AT);
        entities.clear();

        mvc.perform(get(PATH).session(data.session()).param("page", "0").param("size", "2").param("sort", "id,asc"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].id").value(sevenDays.getId().intValue()))
            .andExpect(jsonPath("$.content[0].type").value("REMINDER_7_DAYS"))
            .andExpect(jsonPath("$.content[0].createdAt").value(CREATED_AT.toString()))
            .andExpect(jsonPath("$.content[0].occurredAt").value(CREATED_AT.toString()))
            .andExpect(jsonPath("$.content[0].readAt").value(nullValue()))
            .andExpect(jsonPath("$.content[1].id").value(oneDay.getId().intValue()))
            .andExpect(jsonPath("$.content[1].type").value("REMINDER_1_DAY"))
            .andExpect(jsonPath("$.page.number").value(0))
            .andExpect(jsonPath("$.page.totalElements").value(3))
            .andExpect(jsonPath("$.page.totalPages").value(2));
        mvc.perform(get(PATH).session(data.session()).param("page", "1").param("size", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(older.getId().intValue()))
            .andExpect(jsonPath("$.content[0].type").value("ANNOUNCEMENT"))
            .andExpect(jsonPath("$.content[0].occurredAt").value(ANNOUNCED_AT.toString()))
            .andExpect(jsonPath("$.content[0].flatLabel").value("N-01"))
            .andExpect(jsonPath("$.content[0].startsOn").value(TODAY.toString()))
            .andExpect(jsonPath("$.content[0].endsOn").value(TODAY.plusDays(2).toString()))
            .andExpect(jsonPath("$.content[0].windowStart").value("09:00"))
            .andExpect(jsonPath("$.content[0].windowEnd").value("16:00"));

        unread(data.session()).andExpect(jsonPath("$.count").value(3));
        list(data.session()).andExpect(jsonPath("$.page.size").value(20));
        mvc.perform(get(PATH).session(data.session()).param("size", "100")).andExpect(status().isOk());
        for (var invalidSize : List.of("0", "101", "invalid")) {
            mvc.perform(get(PATH).session(data.session()).param("size", invalidSize)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(PATH).session(data.session()).param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void preservesTheFirstReadTimeAndDoesNotModifyTheFlatmatesDelivery() throws Exception {
        var data = fixture("read");
        var shared = message(data.inspection(), Kind.ANNOUNCEMENT);
        var own = deliver(data.student(), shared);
        var flatmate = student("read-flatmate", data.flat().getResidence());
        persist(new MembershipEntity(data.flat(), flatmate, TODAY));
        var other = deliver(flatmate, shared);
        var otherSession = session(flatmate.getUserKey(), "STUDENT");

        list(data.session()).andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(own.getId().intValue()));
        mark(data.session(), other.getId()).andExpect(status().isNotFound());
        mark(data.session(), own.getId()).andExpect(status().isNoContent())
            .andExpect(header().string("Cache-Control", "no-store"));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        mark(data.session(), own.getId()).andExpect(status().isNoContent());
        entities.clear();

        list(data.session()).andExpect(jsonPath("$.content[0].readAt").value(NOW.toString()));
        unread(data.session()).andExpect(jsonPath("$.count").value(0));
        list(otherSession).andExpect(jsonPath("$.content[0].id").value(other.getId().intValue()))
            .andExpect(jsonPath("$.content[0].readAt").value(nullValue()));
        unread(otherSession).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void hidesDeliveriesAfterLeavingOrSwitchingAndRestoresThemAfterReturning() throws Exception {
        var data = fixture("transfer");
        var own = deliver(data.student(), message(data.inspection(), Kind.ANNOUNCEMENT));
        var secondFlat = persist(new FlatEntity(data.flat().getResidence(), "N-02", 2));
        var otherInspection = new InspectionEntity(secondFlat, data.inspection().getPeriod());
        persist(otherInspection);
        var wrongFlat = deliver(data.student(), message(otherInspection, Kind.ANNOUNCEMENT));

        list(data.session()).andExpect(jsonPath("$.content[0].id").value(own.getId().intValue()));
        unread(data.session()).andExpect(jsonPath("$.count").value(1));
        mark(data.session(), wrongFlat.getId()).andExpect(status().isNotFound());
        data.membership().setValidTo(TODAY);
        assertInvisible(data.session(), own.getId());
        var otherMembership = persist(new MembershipEntity(secondFlat, data.student(), TODAY));
        assertInvisible(data.session(), own.getId(), 1);
        otherMembership.setValidTo(TODAY.plusDays(1));
        persist(new MembershipEntity(data.flat(), data.student(), TODAY.plusDays(1)));
        when(clock.instant()).thenReturn(NOW.plusSeconds(86400));
        list(data.session()).andExpect(jsonPath("$.content[0].id").value(own.getId().intValue()))
            .andExpect(jsonPath("$.content[0].readAt").value(nullValue()));
    }

    @Test
    void rejectsAmbiguousMembershipsAndProtectsAllEndpoints() throws Exception {
        var data = fixture("security");
        var own = deliver(data.student(), message(data.inspection(), Kind.ANNOUNCEMENT));
        var secondFlat = persist(new FlatEntity(data.flat().getResidence(), "N-02", 2));
        persist(new MembershipEntity(secondFlat, data.student(), TODAY));
        assertInvisible(data.session(), own.getId());
        mark(data.session(), Long.MAX_VALUE).andExpect(status().isNotFound());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(put(PATH + "/" + own.getId() + "/read").with(csrf())).andExpect(status().isUnauthorized());
        for (var role : List.of("CLERK", "CARETAKER")) {
            var staff = session(data.student().getUserKey(), role);
            mvc.perform(get(PATH).session(staff)).andExpect(status().isForbidden());
            mvc.perform(get(PATH + "/unread-count").session(staff)).andExpect(status().isForbidden());
            mark(staff, own.getId()).andExpect(status().isForbidden());
        }
        mvc.perform(put(PATH + "/" + own.getId() + "/read").session(data.session()))
            .andExpect(status().isForbidden());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void keepsOneReadTimeAcrossParallelRequests() throws Exception {
        var data = new TransactionTemplate(transactions).execute(status -> fixture("parallel"));
        long id = new TransactionTemplate(transactions).execute(status -> {
            var inspection = entities.find(InspectionEntity.class, data.inspection().getId());
            var student = entities.find(StudentEntity.class, data.student().getId());
            return deliver(student, message(inspection, Kind.ANNOUNCEMENT)).getId();
        });
        var barrier = new CyclicBarrier(2);
        var later = NOW.plusSeconds(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> send = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                mark(session(data.student().getUserKey(), "STUDENT"), id).andExpect(status().isNoContent());
                return null;
            };
            when(clock.instant()).thenReturn(NOW, later);
            for (var result : pool.invokeAll(List.of(send, send), 10, TimeUnit.SECONDS)) {
                result.get();
            }
        }
        var firstRead = notifications.findById(id).orElseThrow().getReadAt();
        assertThat(firstRead).isIn(NOW, later);
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        mark(data.session(), id).andExpect(status().isNoContent());
        assertThat(notifications.findById(id).orElseThrow().getReadAt()).isEqualTo(firstRead);
    }

    private void assertInvisible(MockHttpSession session, long id) throws Exception {
        assertInvisible(session, id, 0);
    }

    private void assertInvisible(MockHttpSession session, long id, int visibleCount) throws Exception {
        list(session).andExpect(jsonPath("$.content.length()").value(visibleCount));
        unread(session).andExpect(jsonPath("$.count").value(visibleCount));
        mark(session, id).andExpect(status().isNotFound());
    }

    private ResultActions list(MockHttpSession session) throws Exception {
        return mvc.perform(get(PATH).session(session)).andExpect(status().isOk());
    }

    private ResultActions unread(MockHttpSession session) throws Exception {
        return mvc.perform(get(PATH + "/unread-count").session(session))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }

    private ResultActions mark(MockHttpSession session, long id) throws Exception {
        return mvc.perform(put(PATH + "/" + id + "/read").session(session).with(csrf()));
    }

    private Fixture fixture(String prefix) {
        var residence = persist(new ResidenceEntity("Benachrichtigungen " + prefix));
        var flat = persist(new FlatEntity(residence, "N-01", 4));
        var clerk = persist(new ClerkEntity(prefix + "-clerk", "Sachbearbeitung", Set.of(), residence));
        var student = student(prefix + "-student", residence);
        var membership = persist(new MembershipEntity(flat, student, TODAY.minusDays(1)));
        var period = new InspectionPeriodEntity(residence, TODAY, TODAY.plusDays(2), clerk);
        period.setAnnouncedAt(ANNOUNCED_AT);
        var inspection = persist(new InspectionEntity(flat, persist(period)));
        return new Fixture(flat, student, membership, inspection, session(student.getUserKey(), "STUDENT"));
    }

    private InspectionNotificationEntity message(InspectionEntity inspection, Kind kind) {
        return persist(new InspectionNotificationEntity(inspection, kind, ANNOUNCED_AT, TODAY, TODAY.plusDays(2),
            LocalTime.of(9, 0), LocalTime.of(16, 0), null));
    }

    private NotificationEntity deliver(StudentEntity student, InspectionNotificationEntity message) {
        return persist(new NotificationEntity(student, message, "Kontrollnachricht", null));
    }

    private void setCreatedAt(long id, Instant instant) {
        entities.createQuery("update NotificationEntity n set n.createdAt = :instant where n.id = :id")
            .setParameter("instant", instant).setParameter("id", id).executeUpdate();
    }

    private StudentEntity student(String key, ResidenceEntity residence) {
        return persist(new StudentEntity(key, key, Set.of(), residence));
    }

    private <T> T persist(T entity) {
        entities.persist(entity);
        return entity;
    }

    private static MockHttpSession session(String userKey, String role) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            userKey, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private record Fixture(FlatEntity flat, StudentEntity student, MembershipEntity membership,
                           InspectionEntity inspection, MockHttpSession session) {}
}
