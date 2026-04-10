package com.chargelink.service;

import com.chargelink.dto.BusinessMapper;
import com.chargelink.dto.SessionDto;
import com.chargelink.dto.StartSessionRequest;
import com.chargelink.dto.StopSessionRequest;
import com.chargelink.entity.Booking;
import com.chargelink.entity.Charger;
import com.chargelink.entity.Session;
import com.chargelink.entity.User;
import com.chargelink.exception.ResourceNotFoundException;
import com.chargelink.repository.BookingRepository;
import com.chargelink.repository.ChargerRepository;
import com.chargelink.repository.SessionRepository;
import com.chargelink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final BookingRepository bookingRepository;
    private final ChargerRepository chargerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final BusinessMapper businessMapper;

    @Transactional
    public SessionDto startSession(UUID userId, StartSessionRequest request) {
        log.info("User {} starting charging session at charger {}", userId, request.getChargerId());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Charger charger = chargerRepository.findById(request.getChargerId())
                .orElseThrow(() -> new ResourceNotFoundException("Charger not found"));

        Booking booking = null;
        if (request.getBookingId() != null) {
            booking = bookingRepository.findById(request.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
            // Transition booking to active
            booking.setStatus("active");
            bookingRepository.save(booking);
        }

        Session session = Session.builder()
                .user(user)
                .charger(charger)
                .booking(booking)
                .startedAt(ZonedDateTime.now())
                .build();

        session = sessionRepository.save(session);
        return businessMapper.toSessionDto(session);
    }

    @Transactional
    public SessionDto stopSession(UUID userId, UUID sessionId, StopSessionRequest request) {
        log.info("User {} stopping charging session {}", userId, sessionId);

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getUser().getId().equals(userId)) {
            throw new SecurityException("You do not have permission to stop this session");
        }

        ZonedDateTime endedAt = ZonedDateTime.now();
        session.setEndedAt(endedAt);
        session.setEnergyDeliveredKwh(request.getEnergyDeliveredKwh());
        session.setDurationMinutes(request.getDurationMinutes());
        session.setAmountCharged(request.getAmountCharged());

        // Update linked booking status to completed
        if (session.getBooking() != null) {
            Booking booking = session.getBooking();
            booking.setStatus("completed");
            bookingRepository.save(booking);

            // Check if there's time left in the slot → expose for walk-ins
            if (endedAt.isBefore(booking.getSlotEnd())) {
                notifyNextWaitingUser(booking.getCharger().getId(), booking.getSlotEnd());
            }
        }

        sessionRepository.save(session);
        return businessMapper.toSessionDto(session);
    }

    @Transactional(readOnly = true)
    public List<SessionDto> getUserSessions(UUID userId) {
        return sessionRepository.findByUserIdOrderByStartedAtDesc(userId)
                .stream()
                .map(businessMapper::toSessionDto)
                .collect(Collectors.toList());
    }

    private void notifyNextWaitingUser(UUID chargerId, ZonedDateTime availableUntil) {
        // Find soonest upcoming booking for this charger
        List<Booking> nextBookings = bookingRepository.findNextConfirmedBooking(chargerId, ZonedDateTime.now(), org.springframework.data.domain.PageRequest.of(0, 1));
        if (!nextBookings.isEmpty()) {
            Booking nextBooking = nextBookings.get(0);
            // Send push notification to next booked user
            notificationService.sendEarlySlotAvailable(nextBooking.getUser().getId(), chargerId, availableUntil);
        }
    }
}
