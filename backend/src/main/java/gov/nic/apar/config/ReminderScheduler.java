package gov.nic.apar.config;

import gov.nic.apar.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Generates automatic APAR reminders for employees who haven't submitted by the due window. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final NotificationService notificationService;

    // Every day at 09:00 server time
    @Scheduled(cron = "0 0 9 * * *")
    public void dailyReminderSweep() {
        int created = notificationService.generateAutoReminders();
        if (created > 0) log.info("APAR auto-reminders generated: {}", created);
    }
}
