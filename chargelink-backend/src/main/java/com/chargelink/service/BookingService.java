package com.chargelink.service;

import com.chargelink.dto.BookingDto;
import com.chargelink.dto.BusinessMapper;
import com.chargelink.dto.CreateBookingRequest;
import com.chargelink.dto.JoinWaitlistRequest;
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Charger charger = chargerRepository.findById(request.getChargerId())
                .orElseThrow(() -> new ResourceNotFoundException("Charger not found"));

        Vehicle vehicle = null;
        if (request.getVehicleId() != null) {
            vehicle = vehicleRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

            // Security: ensure the vehicle belongs to the requesting user
            if (!vehicle.getUser().getId().equals(userId)) {
                throw new SecurityException("Vehicle does not belong to this user");
            }
        }

        Booking booking = Booking.builder()
                .user(user)
                .charger(charger)
                .vehicle(vehicle)
                .slotStart(request.getSlotStart())
                .slotEnd(request.getSlotEnd())
                .estimatedKwh(request.getEstimatedKwh())
                .notes(request.getNotes())
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

        booking.setStatus("cancelled");
        booking.setCancelledAt(ZonedDateTime.now());
        // Since we didn't require a reason payload for simple delete, we leave it blank

        bookingRepository.save(booking);
        log.info("Booking {} successfully cancelled", bookingId);
    }

    @Transactional
    public void joinWaitlist(UUID userId, JoinWaitlistRequest request) {
        log.info("User {} joining waitlist for charger {}", userId, request.getChargerId());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Charger charger = chargerRepository.findById(request.getChargerId())
                .orElseThrow(() -> new ResourceNotFoundException("Charger not found"));

        Vehicle vehicle = null;
        if (request.getVehicleId() != null) {
            vehicle = vehicleRepository.getReferenceById(request.getVehicleId());
        }

        Waitlist waitlist = Waitlist.builder()
                .user(user)
                .charger(charger)
                .vehicle(vehicle)
                .wantedFrom(request.getWantedFrom())
                .wantedTo(request.getWantedTo())
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
}
