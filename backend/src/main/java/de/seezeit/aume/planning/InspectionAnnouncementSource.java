package de.seezeit.aume.planning;

import de.seezeit.aume.planning.InspectionAnnouncementEntity.Kind;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lesender Vertrag für die Zuordnung einer offiziellen Terminrevision zum Versandeintrag. */
@Service
@Transactional(readOnly = true)
public class InspectionAnnouncementSource {
    private static final Logger LOG = LoggerFactory.getLogger(InspectionAnnouncementSource.class);

    private final InspectionAnnouncementRepository announcements;

    InspectionAnnouncementSource(InspectionAnnouncementRepository announcements) {
        this.announcements = announcements;
    }

    public Optional<InspectionAnnouncementEntity> findForRevision(long periodId, Instant sentAt, Kind kind) {
        var matches = announcements.findTop2ByPeriodIdAndSentAtAndKind(periodId, sentAt, kind);
        if (matches.size() > 1) {
            LOG.warn("Mehrdeutiger Versandeintrag für Kontrollzeitraum {} und Terminrevision {} ({})",
                periodId, sentAt, kind);
        }
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }
}
