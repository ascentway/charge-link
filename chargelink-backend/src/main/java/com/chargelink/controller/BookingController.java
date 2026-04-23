package com.chargelink.controller;

import com.chargelink.dto.BookingDto;
import com.chargelink.dto.CreateBookingRequest;
import com.chargelink.dto.JoinWaitlistRequest;
import com.chargelink.dto.WaitlistDto;
import com.chargelink.security.SupabaseUserDetails;
import com.chargelink.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @GetMapping("/bookings/me")
    public ResponseEntity<List<BookingDto>> getMyBookings(
            @AuthenticationPrincipal SupabaseUserDetails user) {
        return ResponseEntity.ok(bookingService.getMyBookings(user.getId()));
    }

    @GetMapping("/waitlist/me")
    public ResponseEntity<List<WaitlistDto>> getMyWaitlist(
            @AuthenticationPrincipal SupabaseUserDetails user) {
        return ResponseEntity.ok(bookingService.getMyWaitlist(user.getId()));
    }

    @GetMapping("/chargers/{chargerId}/waitlist")
    public ResponseEntity<List<WaitlistDto>> getChargerWaitlist(
            @PathVariable UUID chargerId) {
        return ResponseEntity.ok(bookingService.getChargerWaitlist(chargerId));
    }

    @DeleteMapping("/waitlist/{id}")
    public ResponseEntity<Void> leaveWaitlist(
            @PathVariable UUID id,
            @AuthenticationPrincipal SupabaseUserDetails user) {
        bookingService.leaveWaitlist(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
