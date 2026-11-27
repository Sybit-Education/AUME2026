package de.seezeit.aume.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.seezeit.aume.inspection.InspectionNotificationSource;
import de.seezeit.aume.inspection.InspectionNotificationSource.Candidate;
import de.seezeit.aume.notification.InspectionNotificationEntity.Kind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InspectionNotificationSchedulerTests {
    private static final LocalDate TODAY = LocalDate.of(2040, 12, 1);
    private static final Instant NOW = Instant.parse("2040-12-01T08:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void restartsTheScanAndContinuesAfterOneFailedDelivery() {
        var source = mock(InspectionNotificationSource.class);
        var delivery = mock(InspectionNotificationDeliveryService.class);
        when(source.findCandidates(TODAY, 0, 100))
            .thenReturn(List.of(), List.of(candidate(1, 7), candidate(2, 1)));
        doThrow(new IllegalStateException("Testfehler")).when(delivery).deliver(1, Kind.ANNOUNCEMENT);
        var scheduler = new InspectionNotificationScheduler(source, delivery, Clock.fixed(NOW, BERLIN));

        scheduler.run();
        verifyNoInteractions(delivery);
        scheduler.run();

        verify(source, times(2)).findCandidates(TODAY, 0, 100);
        verify(delivery).deliver(1, Kind.ANNOUNCEMENT);
        verify(delivery).deliver(1, Kind.REMINDER_7_DAYS);
        verify(delivery).deliver(2, Kind.ANNOUNCEMENT);
        verify(delivery).deliver(2, Kind.REMINDER_1_DAY);
        verifyNoMoreInteractions(delivery);
    }

    @Test
    void continuesTheScanUsingTheLastIdOfTheBatch() {
        var source = mock(InspectionNotificationSource.class);
        var delivery = mock(InspectionNotificationDeliveryService.class);
        when(source.findCandidates(TODAY, 0, 100))
            .thenReturn(IntStream.rangeClosed(1, 100).mapToObj(id -> candidate(id, 3)).toList());
        when(source.findCandidates(TODAY, 100, 100)).thenReturn(List.of(candidate(101, 3)));

        new InspectionNotificationScheduler(source, delivery, Clock.fixed(NOW, BERLIN)).run();

        verify(source).findCandidates(TODAY, 100, 100);
        verify(delivery).deliver(101, Kind.ANNOUNCEMENT);
    }

    @Test
    void usesTheBerlinCalendarDateAcrossTheDaylightSavingChange() {
        var dueOn = LocalDate.of(2040, 3, 25);
        var startsOn = dueOn.plusDays(1);
        var midnight = dueOn.atStartOfDay(BERLIN);
        assertThat(InspectionNotificationDeliveryService.isDue(Kind.REMINDER_1_DAY, startsOn,
            ZonedDateTime.ofInstant(midnight.toInstant().minusSeconds(1), BERLIN))).isFalse();
        assertThat(InspectionNotificationDeliveryService.isDue(Kind.REMINDER_1_DAY, startsOn, midnight)).isTrue();
        assertThat(InspectionNotificationDeliveryService.isDue(Kind.REMINDER_1_DAY, startsOn, midnight.plusDays(1)))
            .isFalse();
    }

    private Candidate candidate(long id, int daysUntilStart) {
        var start = TODAY.plusDays(daysUntilStart);
        return new Candidate(id, id, id, start, start.plusDays(2), null, null, NOW, null);
    }
}
