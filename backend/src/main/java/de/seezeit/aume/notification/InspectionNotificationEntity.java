package de.seezeit.aume.notification;

import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.planning.InspectionAnnouncementEntity;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Gemeinsamer Inhalt einer kontrollbezogenen In-App-Nachricht. */
@Entity
@Table(name = "inspection_notifications", uniqueConstraints = @UniqueConstraint(
    columnNames = {"inspection_id", "announcement_revision_at", "kind"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionNotificationEntity {
    public enum Kind { ANNOUNCEMENT, REMINDER_7_DAYS, REMINDER_1_DAY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false, updatable = false)
    private InspectionEntity inspection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private Kind kind;

    @Column(nullable = false, updatable = false)
    private Instant announcementRevisionAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_announcement_id", updatable = false)
    private InspectionAnnouncementEntity sourceAnnouncement;

    @Enumerated(EnumType.STRING)
    @Column(name = "announcement_kind", updatable = false, length = 20)
    private InspectionAnnouncementEntity.Kind announcementKind;

    @Column(nullable = false, updatable = false)
    private LocalDate startsOn;

    @Column(nullable = false, updatable = false)
    private LocalDate endsOn;

    @Column(updatable = false)
    private LocalTime windowStart;

    @Column(updatable = false)
    private LocalTime windowEnd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_version_id", updatable = false)
    private ChecklistVersionEntity checklistVersion;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public InspectionNotificationEntity(InspectionEntity inspection, Kind kind, Instant announcementRevisionAt,
            LocalDate startsOn, LocalDate endsOn, LocalTime windowStart, LocalTime windowEnd,
            ChecklistVersionEntity checklistVersion) {
        this(inspection, kind, announcementRevisionAt, startsOn, endsOn, windowStart, windowEnd, checklistVersion,
            null, null);
    }

    InspectionNotificationEntity(InspectionEntity inspection, Kind kind, Instant announcementRevisionAt,
            LocalDate startsOn, LocalDate endsOn, LocalTime windowStart, LocalTime windowEnd,
            ChecklistVersionEntity checklistVersion, InspectionAnnouncementEntity sourceAnnouncement,
            InspectionAnnouncementEntity.Kind announcementKind) {
        if (kind != Kind.ANNOUNCEMENT && checklistVersion != null) {
            throw new IllegalArgumentException("Only announcements can reference a checklist version");
        }
        if (kind != Kind.ANNOUNCEMENT && (sourceAnnouncement != null || announcementKind != null)) {
            throw new IllegalArgumentException("Only announcements can reference an official dispatch or its kind");
        }
        this.inspection = Objects.requireNonNull(inspection);
        this.kind = Objects.requireNonNull(kind);
        this.announcementRevisionAt = Objects.requireNonNull(announcementRevisionAt);
        this.sourceAnnouncement = sourceAnnouncement;
        this.announcementKind = announcementKind;
        this.startsOn = Objects.requireNonNull(startsOn);
        this.endsOn = Objects.requireNonNull(endsOn);
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.checklistVersion = checklistVersion;
    }
}
