package de.seezeit.aume.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.inspection.InspectionNotificationSource;
import de.seezeit.aume.notification.InspectionNotificationEntity.Kind;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.user.ClerkEntity;
import de.seezeit.aume.user.StudentEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-notification-persistence;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
@Transactional
class InspectionNotificationPersistenceTests {
    private static final LocalDate TODAY = LocalDate.of(2040, 12, 3);
    private static final Instant ANNOUNCED_AT = Instant.parse("2040-12-01T08:00:00Z");
    private static final Instant RESCHEDULED_AT = Instant.parse("2040-12-02T08:00:00Z");
    private static final PageRequest ALL = PageRequest.ofSize(10);

    @Autowired private EntityManager entities;
    @Autowired private InspectionNotificationSource source;
    @Autowired private InspectionNotificationRepository messages;
    @Autowired private NotificationRepository notifications;

    private ResidenceEntity residence;
    private FlatEntity flat;
    private FlatEntity otherFlat;
    private ClerkEntity clerk;
    private StudentEntity student;

    @BeforeEach
    void createFixtures() {
        residence = persist(new ResidenceEntity("US-16 Testwohnanlage"));
        flat = persist(new FlatEntity(residence, "N-01", 4));
        otherFlat = persist(new FlatEntity(residence, "N-02", 4));
        clerk = persist(new ClerkEntity("notification-clerk", "Sachbearbeitung", Set.of(), residence));
        student = student("notification-student");
    }

    @Test
    void scansAnnouncedOpenControlsByIdAndKeepsTheSourceRevisionData() {
        var first = control(flat, announcedPeriod(TODAY.plusDays(7), TODAY.plusDays(9)));
        first.getPeriod().setWindowStart(LocalTime.of(9, 0));
        first.getPeriod().setWindowEnd(LocalTime.of(16, 0));
        first.getPeriod().setRescheduledAt(RESCHEDULED_AT);
        control(flat, announcedPeriod(TODAY.minusDays(2), TODAY.minusDays(1)));
        var notAnnounced = announcedPeriod(TODAY, TODAY);
        notAnnounced.setAnnouncedAt(null);
        notAnnounced.setStatus(InspectionPeriodEntity.Status.AGREED);
        control(flat, notAnnounced);
        control(flat, null);
        control(flat, first.getPeriod()).setStatus(InspectionEntity.Status.PUBLISHED);
        control(flat, first.getPeriod()).setStatus(InspectionEntity.Status.FINALIZED);
        var otherResidence = persist(new ResidenceEntity("Andere Anlage"));
        var foreignFlat = persist(new FlatEntity(otherResidence, "N-03", 2));
        control(foreignFlat, first.getPeriod());
        var second = control(otherFlat, announcedPeriod(TODAY.minusDays(1), TODAY));
        second.setStatus(InspectionEntity.Status.DRAFT);
        entities.flush();
        entities.clear();

        var batch = source.findCandidates(TODAY, first.getId() - 1, 1);
        assertThat(batch).containsExactly(new InspectionNotificationSource.Candidate(
            first.getId(), flat.getId(), first.getPeriod().getId(), TODAY.plusDays(7), TODAY.plusDays(9),
            LocalTime.of(9, 0), LocalTime.of(16, 0), ANNOUNCED_AT, RESCHEDULED_AT));
        assertThat(source.findCandidates(TODAY, first.getId(), 10))
            .extracting(InspectionNotificationSource.Candidate::inspectionId).containsExactly(second.getId());
    }

    @Test
    void readsTheSharedMessageAndKeepsReadStatusPerActiveRecipient() {
        var inspection = control(flat, announcedPeriod(TODAY, TODAY.plusDays(2)));
        var checklist = persist(new ChecklistVersionEntity(9999, clerk));
        checklist.setStatus(ChecklistVersionEntity.Status.PUBLISHED);
        var message = messages.save(message(inspection, Kind.ANNOUNCEMENT, checklist));
        var flatmate = student("notification-flatmate");
        var nonRecipient = student("notification-non-recipient");
        var outsider = student("notification-outsider");
        var membership = persist(new MembershipEntity(flat, student, TODAY));
        persist(new MembershipEntity(flat, flatmate, TODAY.minusDays(1)));
        persist(new MembershipEntity(flat, nonRecipient, TODAY));
        persist(new MembershipEntity(otherFlat, outsider, TODAY));
        var personal = notifications.save(new NotificationEntity(student, message, "Ankündigung", null));
        var otherPersonal = notifications.save(new NotificationEntity(flatmate, message, "Ankündigung", null));
        notifications.save(new NotificationEntity(outsider, message, "Ankündigung", null));
        var generic = notifications.save(new NotificationEntity(student, "OTHER", "Allgemeiner Hinweis", null));
        entities.flush();
        entities.clear();

        var saved = messages.findByInspectionIdAndAnnouncementRevisionAtAndKind(
            inspection.getId(), ANNOUNCED_AT, Kind.ANNOUNCEMENT).orElseThrow();
        assertThat(saved.getId()).isEqualTo(message.getId());
        assertThat(saved.getChecklistVersion().getId()).isEqualTo(checklist.getId());
        assertThat(notifications.findById(generic.getId()).orElseThrow().getInspectionNotification()).isNull();
        assertThat(notifications.findVisibleInspectionNotifications(student.getId(), TODAY, ALL))
            .extracting(NotificationEntity::getId).containsExactly(personal.getId());
        assertThat(notifications.findVisibleInspectionNotification(student.getId(), TODAY, otherPersonal.getId()))
            .isEmpty();
        assertThat(notifications.findVisibleInspectionNotifications(nonRecipient.getId(), TODAY, ALL)).isEmpty();
        assertThat(notifications.countUnreadInspectionNotifications(outsider.getId(), TODAY)).isZero();
        assertThat(notifications.countUnreadInspectionNotifications(student.getId(), TODAY.minusDays(1))).isZero();
        assertThat(notifications.countUnreadInspectionNotifications(student.getId(), TODAY)).isEqualTo(1);

        notifications.findVisibleInspectionNotification(student.getId(), TODAY, personal.getId()).orElseThrow()
            .setReadAt(ANNOUNCED_AT.plusSeconds(60));
        assertThat(notifications.countUnreadInspectionNotifications(student.getId(), TODAY)).isZero();
        assertThat(notifications.countUnreadInspectionNotifications(flatmate.getId(), TODAY)).isEqualTo(1);

        entities.find(MembershipEntity.class, membership.getId()).setValidTo(TODAY.plusDays(1));
        assertThat(notifications.findVisibleInspectionNotification(student.getId(), TODAY.plusDays(1), personal.getId()))
            .isEmpty();
    }

    @Test
    void rejectsADuplicateMessageForTheSameInspectionRevisionAndKind() {
        var inspection = control(flat, announcedPeriod(TODAY, TODAY));
        messages.saveAndFlush(message(inspection, Kind.ANNOUNCEMENT, null));
        messages.saveAndFlush(message(inspection, Kind.REMINDER_7_DAYS, null));

        assertThatThrownBy(() -> messages.saveAndFlush(message(inspection, Kind.ANNOUNCEMENT, null)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsADuplicatePersonalDelivery() {
        var inspection = control(flat, announcedPeriod(TODAY, TODAY));
        var message = messages.save(message(inspection, Kind.REMINDER_1_DAY, null));
        notifications.saveAndFlush(new NotificationEntity(student, message, "Erinnerung", null));

        assertThatThrownBy(() -> notifications.saveAndFlush(new NotificationEntity(student, message, "Erinnerung", null)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private InspectionPeriodEntity announcedPeriod(LocalDate start, LocalDate end) {
        var period = new InspectionPeriodEntity(residence, start, end, clerk);
        period.setAnnouncedAt(ANNOUNCED_AT);
        period.setAnnouncedBy(clerk);
        return persist(period);
    }

    private InspectionEntity control(FlatEntity target, InspectionPeriodEntity period) {
        return persist(new InspectionEntity(target, period));
    }

    private InspectionNotificationEntity message(InspectionEntity inspection, Kind kind,
            ChecklistVersionEntity checklist) {
        var period = inspection.getPeriod();
        return new InspectionNotificationEntity(inspection, kind, ANNOUNCED_AT,
            period.getStartsOn(), period.getEndsOn(), period.getWindowStart(), period.getWindowEnd(), checklist);
    }

    private StudentEntity student(String key) {
        return persist(new StudentEntity(key, key, Set.of(), residence));
    }

    private <T> T persist(T entity) {
        entities.persist(entity);
        return entity;
    }
}
