package de.seezeit.aume.planning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import de.seezeit.aume.attachment.AttachmentEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Versandeintrag der Sachbearbeitung (US-18). Hält fest, was wann an wie viele WGs ging. Die Zustellung an die
 * Bewohnenden übernimmt das Notification-Modul anhand von {@code announcedAt}/{@code rescheduledAt}.
 */
@Entity
@Table(name = "inspection_announcements", indexes = @Index(columnList = "period_id, sent_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionAnnouncementEntity {
    public enum Kind { ANNOUNCEMENT, RESCHEDULE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false, updatable = false)
    private InspectionPeriodEntity period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Kind kind;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by", nullable = false, updatable = false)
    private UserEntity sentBy;

    /** Gleich dem gesetzten {@code announcedAt} bzw. {@code rescheduledAt} des Zeitraums. */
    @Column(nullable = false, updatable = false)
    private Instant sentAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id", nullable = false, updatable = false)
    private ResidenceEntity residence;

    @Column(nullable = false, updatable = false)
    private int flatCount;

    /** Kommagetrennt. Aktuell nur {@code IN_APP}. */
    @Column(nullable = false, updatable = false, length = 100)
    private String channels;

    @Column(nullable = false, updatable = false, length = InspectionAnnouncementService.MAX_MESSAGE_LENGTH)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", updatable = false)
    private AttachmentEntity attachment;

    @Column(updatable = false)
    private String attachmentName;

    @Column(nullable = false, updatable = false)
    private LocalDate startsOn;

    @Column(nullable = false, updatable = false)
    private LocalDate endsOn;

    @Column(updatable = false)
    private LocalTime windowStart;

    @Column(updatable = false)
    private LocalTime windowEnd;

    InspectionAnnouncementEntity(InspectionPeriodEntity period, Kind kind, UserEntity sentBy, Instant sentAt,
            int flatCount, String channels, String message, AttachmentEntity attachment, String attachmentName) {
        this.period = period;
        this.kind = kind;
        this.sentBy = sentBy;
        this.sentAt = sentAt;
        this.residence = period.getResidence();
        this.flatCount = flatCount;
        this.channels = channels;
        this.message = message;
        this.attachment = attachment;
        this.attachmentName = attachment == null ? null : attachmentName;
        this.startsOn = period.getStartsOn();
        this.endsOn = period.getEndsOn();
        this.windowStart = period.getWindowStart();
        this.windowEnd = period.getWindowEnd();
    }
}
