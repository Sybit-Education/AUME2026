package de.seezeit.aume.notification;

import de.seezeit.aume.inspection.InspectionNotificationSource;
import de.seezeit.aume.notification.InspectionNotificationEntity.Kind;
import java.time.Clock;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class InspectionNotificationScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(InspectionNotificationScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final InspectionNotificationSource source;
    private final InspectionNotificationDeliveryService delivery;
    private final Clock clock;

    InspectionNotificationScheduler(InspectionNotificationSource source, InspectionNotificationDeliveryService delivery,
            @Qualifier("inspectionNotificationClock") Clock clock) {
        this.source = source;
        this.delivery = delivery;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    void run() {
        var now = ZonedDateTime.now(clock);
        long afterId = 0;
        while (true) {
            var candidates = source.findCandidates(now.toLocalDate(), afterId, BATCH_SIZE);
            for (var candidate : candidates) {
                for (var kind : Kind.values()) {
                    if (InspectionNotificationDeliveryService.isDue(kind, candidate.startsOn(), now)) {
                        deliver(candidate.inspectionId(), kind);
                    }
                }
                afterId = candidate.inspectionId();
            }
            if (candidates.size() < BATCH_SIZE) return;
        }
    }

    private void deliver(long inspectionId, Kind kind) {
        try {
            delivery.deliver(inspectionId, kind);
        } catch (RuntimeException failure) {
            LOG.warn("Zustellung für Kontrolle {} ({}) fehlgeschlagen", inspectionId, kind, failure);
        }
    }
}
