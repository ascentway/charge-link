package com.chargelink.service;

import com.chargelink.dto.BookingDto;
import com.chargelink.dto.BusinessMapper;
import com.chargelink.dto.CreateBookingRequest;
import com.chargelink.dto.JoinWaitlistRequest;
import com.chargelink.dto.WaitlistDto;
import com.chargelink.entity.Booking;
import com.chargelink.entity.Charger;
import com.chargelink.entity.User;
import com.chargelink.entity.Vehicle;
import com.chargelink.entity.Waitlist;
import com.chargelink.exception.ResourceNotFoundException;
import com.chargelink.repository.BookingRepository;
import com.chargelink.repository.ChargerRepository;
import com.chargelink.repository.UserRepository;
import com.chargelink.repository.VehicleRepository;
import com.chargelink.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final WaitlistRepository waitlistRepository;
    private final ChargerRepository chargerRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final BusinessMapper businessMapper;

    @Transactional
    public BookingDto createBooking(UUID userId, CreateBookingRequest request) {
        log.info("User {} creating booking for charger {} between {} and {}",
                userId, request.getChargerId(), request.getSlotStart(), request.getSlotEnd());

        if (!request.getSlotEnd().isAfter(request.getSlotStart())) {
            throw new IllegalArgumentException("Slot end must be strictly after slot start.");
        }
        if (request.getSlotStart().isBefore(ZonedDateTime.now())) {
            throw new IllegalArgumentException("Cannot create a booking in the past.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Charger charger = chargerRepository.findById(request.getChargerId())
                .orElseThrow(() -> new ResourceNotFoundException("Charger not found"));

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        // Security: ensure the vehicle belongs to the requesting user
        if (!vehicle.getUser().getId().equals(userId)) {
            throw new SecurityException("Vehicle does not belong to this user");
        }

        // Connector compatibility: vehicle must support the charger's connector type
        String chargerConnector = charger.getConnectorType();
        String vehicleConnector = vehicle.getConnectorType().getValue();
        if (!chargerConnector.equalsIgnoreCase(vehicleConnector)) {
            throw new IllegalArgumentException(
                    "Connector mismatch: your vehicle uses '" + vehicleConnector +
                            "' but this charger is '" + chargerConnector + "'.");
        }

        Booking booking = Booking.builder()
                .user(user)
                .charger(charger)
                .vehicle(vehicle)
                .slotStart(request.getSlotStart())
                .slotEnd(request.getSlotEnd())
                .estimatedKwh(request.getEstimatedKwh())
                .notes(request.getNotes())
                .status("pending")
                .build();

        booking.setBufferExpiresAt(request.getSlotStart().plusMinutes(10));

        // Save attempts to persist. If there's an overlap, the gist 'no_overlap' constraint
        // built into PosgreSQL will throw a DataIntegrityViolationException.
        // It is safely handled by our GlobalExceptionHandler returning HTTP 409 Conflict.
        booking = bookingRepository.save(booking);

        return businessMapper.toBookingDto(booking);
    }

    @Transactional
    public void cancelBooking(UUID userId, UUID bookingId) {
        log.info("User {} cancelling booking {}", userId, bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!booking.getUser().getId().equals(userId)) {
            throw new SecurityException("You do not have permission to cancel this booking");
        }

        if ("cancelled".equals(booking.getStatus()) || "expired".equals(booking.getStatus()) || "completed".equals(booking.getStatus())) {
            throw new IllegalStateException("Booking is already " + booking.getStatus() + " and cannot be cancelled");
        }

        booking.setStatus("cancelled");
        booking.setCancelledAt(ZonedDateTime.now());
        // Since we didn't require a reason payload for simple delete, we leave it blank

        bookingRepository.save(booking);
        log.info("Booking {} successfully cancelled", bookingId);
    }

    @Transactional
    public void joinWaitlist(UUID userId, JoinWaitlistRequest request) {
        log.info("User {} joining waitlist for charger {} from {} to {}",
                userId, request.getChargerId(), request.getWantedFrom(), request.getWantedTo());

        if (!request.getWantedTo().isAfter(request.getWantedFrom())) {
            throw new IllegalArgumentException("Waitlist end time must be strictly after start time.");
        }
        if (request.getWantedFrom().isBefore(ZonedDateTime.now())) {
            throw new IllegalArgumentException("Cannot join waitlist for a time in the past.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Charger charger = chargerRepository.findById(request.getChargerId())
                .orElseThrow(() -> new ResourceNotFoundException("Charger not found"));

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        if (!vehicle.getUser().getId().equals(userId)) {
            throw new SecurityException("Vehicle does not belong to this user");
        }

        String chargerConnector = charger.getConnectorType();
        String vehicleConnector = vehicle.getConnectorType().getValue();
        if (!chargerConnector.equalsIgnoreCase(vehicleConnector)) {
            throw new IllegalArgumentException(
                    "Connector mismatch: your vehicle uses '" + vehicleConnector +
                            "' but this charger is '" + chargerConnector + "'.");
        }

        // Prevent double booking / double waitlisting for the same time slot
        if (bookingRepository.hasOverlappingBookingForUser(userId, request.getWantedFrom(), request.getWantedTo()) ||
                waitlistRepository.hasOverlappingWaitlistForUser(userId, request.getWantedFrom(), request.getWantedTo())) {
            throw new IllegalArgumentException("You already have a booking or waitlist entry for this time slot.");
        }

        Waitlist waitlist = Waitlist.builder()
                .user(user)
                .charger(charger)
                .vehicle(vehicle)
                .wantedFrom(request.getWantedFrom())
                .wantedTo(request.getWantedTo())
                .status("waiting")
                .build();

        waitlistRepository.save(waitlist);
    }

    @Transactional
    public BookingDto claimEarlySlot(UUID userId, UUID bookingId) {
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

            if (!booking.getUser().getId().equals(userId)) {
                throw new SecurityException("Not your booking");
            }
            if (!"pending".equals(booking.getStatus()) &&
                    !"confirmed".equals(booking.getStatus())) {
                throw new IllegalStateException("Booking is not in a claimable state");
            }

            booking.setSlotStart(ZonedDateTime.now());
            booking.setStatus("confirmed");
            return businessMapper.toBookingDto(bookingRepository.save(booking));

        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            throw new IllegalStateException("Slot was just claimed by another user");
        }
    }

    @Transactional(readOnly = true)
    public List<WaitlistDto> getMyWaitlist(UUID userId) {
        return waitlistRepository.findByUserIdOrderByJoinedAtDesc(userId).stream()
                .map(businessMapper::toWaitlistDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getMyBookings(UUID userId) {
        return bookingRepository.findByUserIdOrderBySlotStartDesc(userId).stream()
                .map(businessMapper::toBookingDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WaitlistDto> getChargerWaitlist(UUID chargerId) {
        return waitlistRepository.findByChargerIdAndStatusOrderByJoinedAtAsc(chargerId, "waiting").stream()
                .map(businessMapper::toWaitlistDto)
                .toList();
    }

    @Transactional
    public void leaveWaitlist(UUID userId, UUID waitlistId) {
        Waitlist waitlist = waitlistRepository.findById(waitlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry not found"));

        if (!waitlist.getUser().getId().equals(userId)) {
            throw new SecurityException("You do not have permission to leave this waitlist");
        }

        if ("cancelled".equals(waitlist.getStatus()) || "completed".equals(waitlist.getStatus())) {
            throw new IllegalStateException("Waitlist entry is already " + waitlist.getStatus() + " and cannot be left");
        }

        waitlist.setStatus("cancelled");
        waitlistRepository.save(waitlist);
    }
}
