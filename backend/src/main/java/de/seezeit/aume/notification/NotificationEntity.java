package de.seezeit.aume.notification;

import java.time.Instant;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Hinweis innerhalb der App. */
@Entity
@Table(name = "notifications", indexes = @Index(columnList = "user_id, read_at"),
    uniqueConstraints = @UniqueConstraint(columnNames = {"inspection_notification_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_notification_id")
    @Setter(AccessLevel.NONE)
    private InspectionNotificationEntity inspectionNotification;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(length = 500)
    private String link;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant readAt;

    public NotificationEntity(UserEntity user, String type, String title, String link) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.link = link;
    }

    public NotificationEntity(UserEntity user, InspectionNotificationEntity inspectionNotification, String title,
            String link) {
        this(user, inspectionNotification.getKind().name(), title, link);
        this.inspectionNotification = inspectionNotification;
    }
}
