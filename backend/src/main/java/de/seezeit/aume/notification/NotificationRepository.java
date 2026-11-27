package de.seezeit.aume.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
          and 1 = (
              select count(distinct active.flat.id) from MembershipEntity active
              where active.user = n.user and active.validFrom <= :today
                and (active.validTo is null or active.validTo > :today)
          )
        """;

    @Query("select n " + VISIBLE_INSPECTION_NOTIFICATIONS + "order by n.createdAt desc, n.id desc")
    List<NotificationEntity> findVisibleInspectionNotifications(
        @Param("userId") Long userId, @Param("today") LocalDate today, Pageable page);

    @EntityGraph(attributePaths = "inspectionNotification.inspection.flat")
    @Query(value = "select n " + VISIBLE_INSPECTION_NOTIFICATIONS + "order by n.createdAt desc, n.id desc",
        countQuery = "select count(n) " + VISIBLE_INSPECTION_NOTIFICATIONS)
    Page<NotificationEntity> pageVisibleInspectionNotifications(
        @Param("userId") Long userId, @Param("today") LocalDate today, Pageable page);

    @Query("select n " + VISIBLE_INSPECTION_NOTIFICATIONS + "and n.id = :id")
    Optional<NotificationEntity> findVisibleInspectionNotification(
        @Param("userId") Long userId, @Param("today") LocalDate today, @Param("id") Long id);

    @Query("select count(n) " + VISIBLE_INSPECTION_NOTIFICATIONS + "and n.readAt is null")
    long countUnreadInspectionNotifications(@Param("userId") Long userId, @Param("today") LocalDate today);

    @Modifying(flushAutomatically = true)
    @Query("update NotificationEntity delivery set delivery.readAt = :readAt "
        + "where delivery.id = :id and delivery.readAt is null and delivery.id in (select n.id "
        + VISIBLE_INSPECTION_NOTIFICATIONS + "and n.id = :id)")
    int markVisibleInspectionNotificationRead(@Param("userId") Long userId, @Param("today") LocalDate today,
        @Param("id") Long id, @Param("readAt") Instant readAt);
}
