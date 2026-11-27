package de.seezeit.aume.planning;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionAnnouncementRepository extends JpaRepository<InspectionAnnouncementEntity, Long> {
    @EntityGraph(attributePaths = {"sentBy", "residence"})
    List<InspectionAnnouncementEntity> findByPeriodIdOrderBySentAtDescIdDesc(Long periodId);
}
