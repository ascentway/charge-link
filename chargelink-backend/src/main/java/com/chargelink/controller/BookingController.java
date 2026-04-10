package com.chargelink.controller;

import com.chargelink.dto.BookingDto;
import com.chargelink.dto.CreateBookingRequest;
import com.chargelink.dto.JoinWaitlistRequest;
import com.chargelink.security.SupabaseUserDetails;
import com.chargelink.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/bookings")
    public ResponseEntity<BookingDto> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal SupabaseUserDetails user) {

        BookingDto booking = bookingService.createBooking(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable UUID id,
            @AuthenticationPrincipal SupabaseUserDetails user) {

        bookingService.cancelBooking(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/waitlist/join")
    public ResponseEntity<Void> joinWaitlist(
            @Valid @RequestBody JoinWaitlistRequest request,
            @AuthenticationPrincipal SupabaseUserDetails user) {

        bookingService.joinWaitlist(user.getId(), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bookings/{bookingId}/claim-early")
    public ResponseEntity<BookingDto> claimEarly(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal SupabaseUserDetails user) {
        BookingDto dto = bookingService.claimEarlySlot(user.getId(), bookingId);
        return ResponseEntity.ok(dto);
    }
}
