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
        // Payload: "Good news! Your charger is free early.
        //           Tap to start now — available until HH:MM"
        log.info("PUSH → user {} charger {} free until {}",
                userId, chargerId, availableUntil);
    }
}
