package gov.nic.apar.controller;

import gov.nic.apar.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Secured endpoint invoked by Vercel Cron; not protected by a user JWT. */
@RestController
@RequestMapping("/v1/cron")
@RequiredArgsConstructor
public class CronController {

    private final NotificationService notificationService;

    @Value("${app.cron.secret:}")
    private String cronSecret;

    @GetMapping("/reminders")
    public ResponseEntity<Map<String, Object>> generateReminders(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (cronSecret == null || cronSecret.isBlank()
                || authorization == null
                || !MessageDigest.isEqual(
                    ("Bearer " + cronSecret).getBytes(StandardCharsets.UTF_8),
                    authorization.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }

        int generated = notificationService.generateAutoReminders();
        return ResponseEntity.ok(Map.of("ok", true, "generated", generated));
    }
}
