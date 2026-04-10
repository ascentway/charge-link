package com.chargelink.repository;

import com.chargelink.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByUserIdOrderBySlotStartDesc(UUID userId);

    @Query("SELECT b FROM Booking b WHERE b.status = 'pending' " +
            "AND b.bufferExpiresAt < :now")
    List<Booking> findExpiredBufferBookings(@Param("now") ZonedDateTime now);

    @Query("SELECT b FROM Booking b WHERE b.charger.id = :chargerId " +
            "AND b.status IN ('pending','confirmed') " +
            "AND b.slotStart > :after " +
            "ORDER BY b.slotStart ASC")
    List<Booking> findNextConfirmedBooking(
            @Param("chargerId") UUID chargerId,
            @Param("after") ZonedDateTime after,
            Pageable pageable);
}
