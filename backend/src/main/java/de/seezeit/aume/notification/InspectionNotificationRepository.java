package de.seezeit.aume.notification;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface InspectionNotificationRepository extends JpaRepository<InspectionNotificationEntity, Long> {
    Optional<InspectionNotificationEntity> findByInspectionIdAndAnnouncementRevisionAtAndKind(
        Long inspectionId, Instant announcementRevisionAt, InspectionNotificationEntity.Kind kind);
}
