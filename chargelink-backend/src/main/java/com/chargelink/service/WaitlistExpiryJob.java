package com.chargelink.service;

import com.chargelink.entity.Waitlist;
import com.chargelink.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WaitlistExpiryJob {

    private final WaitlistRepository waitlistRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 60_000) // sweep every 60 seconds
    @Transactional
    public void expireStaleWaitlists() {
        ZonedDateTime now = ZonedDateTime.now();

        // 1. Sweep completely expired wantedTo windows
        List<Waitlist> expiredWaitlists = waitlistRepository.findExpiredWaitlists(now);
        for (Waitlist w : expiredWaitlists) {
            w.setStatus("expired");
            log.info("Waitlist {} expired because the requested time window passed.", w.getId());
        }
        waitlistRepository.saveAll(expiredWaitlists);

        // 2. Sweep users who were notified but didn't respond within 15 minutes
        ZonedDateTime threshold = now.minusMinutes(15);
        List<Waitlist> unresponsiveWaitlists = waitlistRepository.findUnresponsiveWaitlists(threshold);
        for (Waitlist w : unresponsiveWaitlists) {
            w.setStatus("skipped");
            log.info("Waitlist {} marked skipped due to unresponsive user.", w.getId());
            // Send push notification indicating they missed their spot
            notificationService.sendWaitlistExpiredNotification(w.getUser().getId());
        }
        waitlistRepository.saveAll(unresponsiveWaitlists);
    }
}
