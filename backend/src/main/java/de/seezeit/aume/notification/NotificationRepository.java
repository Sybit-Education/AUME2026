package de.seezeit.aume.notification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    String VISIBLE_INSPECTION_NOTIFICATIONS = """
        from NotificationEntity n join n.inspectionNotification message
        where n.user.id = :userId
          and exists (
              select m.id from MembershipEntity m
              where m.user = n.user and m.flat = message.inspection.flat
                and m.validFrom <= :today and (m.validTo is null or m.validTo > :today)
          )
        """;

    @Query("select n " + VISIBLE_INSPECTION_NOTIFICATIONS + "order by n.createdAt desc, n.id desc")
    List<NotificationEntity> findVisibleInspectionNotifications(
        @Param("userId") Long userId, @Param("today") LocalDate today, Pageable page);

    @Query("select n " + VISIBLE_INSPECTION_NOTIFICATIONS + "and n.id = :id")
    Optional<NotificationEntity> findVisibleInspectionNotification(
        @Param("userId") Long userId, @Param("today") LocalDate today, @Param("id") Long id);

    @Query("select count(n) " + VISIBLE_INSPECTION_NOTIFICATIONS + "and n.readAt is null")
    long countUnreadInspectionNotifications(@Param("userId") Long userId, @Param("today") LocalDate today);
}
