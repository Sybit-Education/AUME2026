package de.seezeit.aume.planning;

import de.seezeit.aume.planning.InspectionAnnouncementEntity.Kind;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionAnnouncementRepository extends JpaRepository<InspectionAnnouncementEntity, Long> {
    @EntityGraph(attributePaths = {"sentBy", "residence"})
    List<InspectionAnnouncementEntity> findByPeriodIdOrderBySentAtDescIdDesc(Long periodId);

    List<InspectionAnnouncementEntity> findTop2ByPeriodIdAndSentAtAndKind(Long periodId, Instant sentAt, Kind kind);
}
