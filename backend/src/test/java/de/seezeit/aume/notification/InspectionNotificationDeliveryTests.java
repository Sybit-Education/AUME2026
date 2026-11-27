package de.seezeit.aume.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.notification.InspectionNotificationEntity.Kind;
import de.seezeit.aume.planning.InspectionAnnouncementEntity;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.user.ClerkEntity;
import de.seezeit.aume.user.StudentEntity;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-notification-delivery")
@ActiveProfiles("dev")
@Transactional
class InspectionNotificationDeliveryTests {
    private static final LocalDate FIRST_START = LocalDate.of(2040, 12, 8);
    private static final Instant ANNOUNCED = Instant.parse("2040-12-01T08:00:00Z");
    private static final Instant RESCHEDULED = Instant.parse("2040-12-01T12:00:00Z");
    private static final PageRequest PAGE = PageRequest.ofSize(10);

    @MockitoBean private InspectionNotificationScheduler scheduler;
    @MockitoBean(name = "inspectionNotificationClock") private Clock clock;
    @Autowired private InspectionNotificationDeliveryService delivery;
    @Autowired private InspectionNotificationRepository messages;
    @Autowired private NotificationRepository notifications;
    @Autowired private EntityManager entities;
    @Autowired private PlatformTransactionManager transactions;

    private ResidenceEntity residence;
    private FlatEntity flat;
    private ClerkEntity clerk;
    private InspectionEntity inspection;
    private InspectionPeriodEntity period;
    private StudentEntity student;
    private MembershipEntity membership;

    @BeforeEach
    void setUpClock() {
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        now("2040-12-01T08:00:00Z");
    }

    private void fixture() {
        fixture("delivery");
    }

    private void fixture(String prefix) {
        residence = persist(new ResidenceEntity("Zustellung Testanlage"));
        clerk = persist(new ClerkEntity(prefix + "-clerk", "Sachbearbeitung", Set.of(), residence));
        flat = persist(new FlatEntity(residence, "N-01", 4));
        student = student(prefix + "-student");
        membership = persist(new MembershipEntity(flat, student, FIRST_START.minusDays(8)));
        period = persist(new InspectionPeriodEntity(residence, FIRST_START, FIRST_START.plusDays(2), clerk));
        period.setAnnouncedAt(ANNOUNCED);
        inspection = persist(new InspectionEntity(flat, period));
    }

    @Test
    void deliversCurrentRevisionOnceWithoutChangingTheEarlierAnnouncement() {
        fixture();
        var originalChecklist = checklist(1001);
        var flatmate = student("delivery-flatmate");
        persist(new MembershipEntity(flat, flatmate, FIRST_START.minusDays(7)));
        var outsider = student("delivery-outsider");
        var otherFlat = persist(new FlatEntity(residence, "N-02", 2));
        persist(new MembershipEntity(otherFlat, outsider, FIRST_START.minusDays(7)));

        delivery.deliver(inspection.getId(), Kind.ANNOUNCEMENT);
        delivery.deliver(inspection.getId(), Kind.ANNOUNCEMENT);
        var original = message(ANNOUNCED, Kind.ANNOUNCEMENT);

        var newChecklist = checklist(1002);
        now("2040-12-02T08:00:00Z");
        period.setStartsOn(FIRST_START.plusDays(1));
        period.setRescheduledAt(RESCHEDULED);
        delivery.deliver(inspection.getId(), Kind.ANNOUNCEMENT);
        delivery.deliver(inspection.getId(), Kind.ANNOUNCEMENT);

        var revision = message(RESCHEDULED, Kind.ANNOUNCEMENT);
        assertThat(original.getStartsOn()).isEqualTo(FIRST_START);
        assertThat(revision.getStartsOn()).isEqualTo(FIRST_START.plusDays(1));
        assertThat(original.getAnnouncementKind()).isEqualTo(InspectionAnnouncementEntity.Kind.ANNOUNCEMENT);
        assertThat(revision.getAnnouncementKind()).isEqualTo(InspectionAnnouncementEntity.Kind.RESCHEDULE);
        assertThat(original.getSourceAnnouncement()).isNull();
        assertThat(revision.getSourceAnnouncement()).isNull();
        assertThat(original.getChecklistVersion().getId()).isEqualTo(originalChecklist.getId());
        assertThat(revision.getChecklistVersion().getId()).isEqualTo(newChecklist.getId());
        assertThat(notifications.findVisibleInspectionNotifications(student.getId(), FIRST_START, PAGE)).hasSize(2);
        assertThat(notifications.findVisibleInspectionNotifications(flatmate.getId(), FIRST_START, PAGE)).hasSize(2);
        assertThat(notifications.findVisibleInspectionNotifications(outsider.getId(), FIRST_START, PAGE)).isEmpty();
    }

    @Test
    void reschedulesBothRemindersAndUsesTheMembershipAtDelivery() {
        fixture();
        now("2040-12-01T13:00:00Z");
        period.setStartsOn(FIRST_START.plusDays(1));
        period.setRescheduledAt(RESCHEDULED);
        delivery.deliver(inspection.getId(), Kind.REMINDER_7_DAYS);
        assertMissing(RESCHEDULED, Kind.REMINDER_7_DAYS);

        now("2040-12-01T22:59:59Z");
        delivery.deliver(inspection.getId(), Kind.REMINDER_7_DAYS);
        assertMissing(RESCHEDULED, Kind.REMINDER_7_DAYS);
        now("2040-12-01T23:00:00Z");
        delivery.deliver(inspection.getId(), Kind.REMINDER_7_DAYS);
        delivery.deliver(inspection.getId(), Kind.REMINDER_7_DAYS);
        var weekReminder = message(RESCHEDULED, Kind.REMINDER_7_DAYS);
        assertThat(weekReminder.getChecklistVersion()).isNull();
        assertThat(weekReminder.getAnnouncementKind()).isNull();
        assertThat(weekReminder.getSourceAnnouncement()).isNull();
        assertMissing(ANNOUNCED, Kind.REMINDER_7_DAYS);
        assertThat(notifications.findVisibleInspectionNotifications(student.getId(), FIRST_START.minusDays(6), PAGE))
            .hasSize(1);

        membership.setValidTo(FIRST_START);
        var newMember = student("delivery-new-member");
        persist(new MembershipEntity(flat, newMember, FIRST_START));
        now("2040-12-08T11:00:00Z");
        delivery.deliver(inspection.getId(), Kind.REMINDER_1_DAY);
        var dayReminder = message(RESCHEDULED, Kind.REMINDER_1_DAY);
        assertThat(dayReminder.getChecklistVersion()).isNull();
        assertThat(dayReminder.getAnnouncementKind()).isNull();
        assertThat(dayReminder.getSourceAnnouncement()).isNull();
        assertThat(notifications.findVisibleInspectionNotifications(newMember.getId(), FIRST_START, PAGE)).hasSize(1);
        assertThat(notifications.findVisibleInspectionNotifications(student.getId(), FIRST_START, PAGE)).isEmpty();
    }

    @Test
    void deliversOnlyTheCurrentRevisionWhenTheEarlierTermWasNeverScanned() {
        fixture();
        period.setStartsOn(FIRST_START.plusDays(1));
        period.setRescheduledAt(RESCHEDULED);
        now("2040-12-02T08:00:00Z");

        delivery.deliver(inspection.getId(), Kind.ANNOUNCEMENT);

        assertMissing(ANNOUNCED, Kind.ANNOUNCEMENT);
        assertThat(message(RESCHEDULED, Kind.ANNOUNCEMENT).getStartsOn()).isEqualTo(FIRST_START.plusDays(1));
        assertThat(notifications.findVisibleInspectionNotifications(student.getId(), FIRST_START, PAGE)).hasSize(1);
    }

    @Test
    void skipsMissedDaysAndRechecksTheControlBeforeDelivery() {
        fixture();
        now("2040-12-02T08:00:00Z");
        delivery.deliver(inspection.getId(), Kind.REMINDER_7_DAYS);
        assertMissing(ANNOUNCED, Kind.REMINDER_7_DAYS);

        now("2040-12-07T08:00:00Z");
        for (var status : Set.of(InspectionEntity.Status.FINALIZED, InspectionEntity.Status.PUBLISHED)) {
            inspection.setStatus(status);
            delivery.deliver(inspection.getId(), Kind.REMINDER_1_DAY);
            assertMissing(ANNOUNCED, Kind.REMINDER_1_DAY);
        }

        inspection.setStatus(InspectionEntity.Status.PLANNED);
        now("2040-12-11T08:00:00Z");
        delivery.deliver(inspection.getId(), Kind.ANNOUNCEMENT);
        assertMissing(ANNOUNCED, Kind.ANNOUNCEMENT);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentTransactionsDeliverOneAnnouncement() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> fixture("concurrent-delivery"));
        long inspectionId = inspection.getId();
        long recipientId = student.getId();
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> send = () -> {
                delivery.deliver(inspectionId, Kind.ANNOUNCEMENT);
                return null;
            };
            for (var result : pool.invokeAll(List.of(send, send), 10, TimeUnit.SECONDS)) {
                result.get();
            }
        }

        assertThat(notifications.findVisibleInspectionNotifications(recipientId, FIRST_START, PAGE)).hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackTheMessageAndItsDeliveriesTogether() {
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> fixture("rollback-delivery"));

        transaction.executeWithoutResult(status -> {
            delivery.deliver(inspection.getId(), Kind.ANNOUNCEMENT);
            entities.flush();
            assertThat(notifications.findVisibleInspectionNotifications(student.getId(), FIRST_START, PAGE)).hasSize(1);
            status.setRollbackOnly();
        });

        assertMissing(ANNOUNCED, Kind.ANNOUNCEMENT);
        assertThat(notifications.findVisibleInspectionNotifications(student.getId(), FIRST_START, PAGE)).isEmpty();
    }

    private void now(String instant) {
        when(clock.instant()).thenReturn(Instant.parse(instant));
    }

    private InspectionNotificationEntity message(Instant revision, Kind kind) {
        return messages.findByInspectionIdAndAnnouncementRevisionAtAndKind(inspection.getId(), revision, kind)
            .orElseThrow();
    }

    private void assertMissing(Instant revision, Kind kind) {
        assertThat(messages.findByInspectionIdAndAnnouncementRevisionAtAndKind(inspection.getId(), revision, kind))
            .isEmpty();
    }

    private ChecklistVersionEntity checklist(int version) {
        var checklist = new ChecklistVersionEntity(version, clerk);
        checklist.setStatus(ChecklistVersionEntity.Status.PUBLISHED);
        return persist(checklist);
    }

    private StudentEntity student(String key) {
        return persist(new StudentEntity(key, key, Set.of(), residence));
    }

    private <T> T persist(T entity) {
        entities.persist(entity);
        return entity;
    }
}
