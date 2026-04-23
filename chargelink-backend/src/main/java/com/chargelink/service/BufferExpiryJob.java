package com.chargelink.service;

import com.chargelink.entity.Booking;
import com.chargelink.repository.BookingRepository;
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
public class BufferExpiryJob {

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 60_000) // runs every 60 seconds
    @Transactional
    public void expireBufferedBookings() {
        ZonedDateTime now = ZonedDateTime.now();

        List<Booking> expired = bookingRepository.findExpiredBufferBookings(now);

        for (Booking b : expired) {
            b.setStatus("no_show");
            b.setCancelledAt(now);
            b.setCancelReason("Buffer expired — user did not arrive");
            log.info("Booking {} marked no_show after buffer expiry", b.getId());   
        }
        bookingRepository.saveAll(expired);
    }

    @Scheduled(fixedDelay = 300_000) // sweeps every 5 minutes
    @Transactional
    public void expirePastDueBookings() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Booking> pastDue = bookingRepository.findPastDueBookings(now);

        for (Booking b : pastDue) {
            b.setStatus("expired");
            b.setCancelledAt(now);
            b.setCancelReason("Time block concluded without session activation.");
            log.info("Booking {} marked expired because slotEnd passed without use", b.getId());
            notificationService.sendBookingNoShowNotification(b.getUser().getId(), b.getId());
        }
        bookingRepository.saveAll(pastDue);
    }
}
