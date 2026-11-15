package gov.nic.apar.config;

import gov.nic.apar.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Generates reminders in always-on deployments. Vercel uses its authenticated Cron route instead. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private final NotificationService notificationService;

    // Every day at 09:00 India Standard Time in always-on hosting environments.
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    public void dailyReminderSweep() {
        int created = notificationService.generateAutoReminders();
        if (created > 0) log.info("APAR auto-reminders generated: {}", created);
    }
}
