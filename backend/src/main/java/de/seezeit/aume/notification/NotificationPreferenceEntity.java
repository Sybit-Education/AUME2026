package de.seezeit.aume.notification;

import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreferenceEntity {
    @Id
    private Long userId;

    @MapsId
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(nullable = false)
    private boolean emailEnabled = true;

    @Column(nullable = false)
    private boolean pushEnabled;

    @Column(nullable = false)
    private boolean taskReminders;

    public NotificationPreferenceEntity(UserEntity user) {
        this.user = user;
    }
}
