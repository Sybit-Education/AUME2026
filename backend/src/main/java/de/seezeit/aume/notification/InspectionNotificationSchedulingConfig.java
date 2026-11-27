package de.seezeit.aume.notification;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class InspectionNotificationSchedulingConfig {
    @Bean
    Clock inspectionNotificationClock() {
        return Clock.system(ZoneId.of("Europe/Berlin"));
    }
}
