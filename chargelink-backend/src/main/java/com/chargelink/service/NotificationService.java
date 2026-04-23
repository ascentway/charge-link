package com.chargelink.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@Slf4j
public class NotificationService {

    public void sendEarlySlotAvailable(UUID userId, UUID chargerId,
                                       ZonedDateTime availableUntil) {
        // TODO: wire to Firebase FCM or Expo push
        // Payload: "Good news! Your charger is free early. Tap to start now"
        log.info("PUSH → user {} charger {} free until {}",
                userId, chargerId, availableUntil);
    }

    public void sendBookingNoShowNotification(UUID userId, UUID bookingId) {
        // Payload: "Your booking time has concluded and no session was started. Booking cancelled."
        log.info("PUSH → user {} booking {} cancelled due to no-show/expiry",
                userId, bookingId);
    }

    public void sendWaitlistExpiredNotification(UUID userId) {
        // Payload: "Your waitlist spot has expired as you didn't claim your turn in time."
        log.info("PUSH → user {} waitlist expired due to inactivity", userId);
    }
}
