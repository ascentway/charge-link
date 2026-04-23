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
import com.chargelink.entity.enums.ConnectorType;
import com.chargelink.exception.ResourceNotFoundException;
import com.chargelink.repository.BookingRepository;
import com.chargelink.repository.ChargerRepository;
import com.chargelink.repository.UserRepository;
import com.chargelink.repository.VehicleRepository;
import com.chargelink.repository.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Unit Tests")
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private WaitlistRepository waitlistRepository;
    @Mock
    private ChargerRepository chargerRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BusinessMapper businessMapper;

    @InjectMocks
    private BookingService bookingService;

    private UUID userId;
    private UUID chargerId;
    private UUID vehicleId;
    private UUID bookingId;
    private UUID waitlistId;
    private User user;
    private Charger charger;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        chargerId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        waitlistId = UUID.randomUUID();

        user = User.builder().id(userId).build();

        charger = Charger.builder()
                .id(chargerId)
                .connectorType("CCS2")
                .build();

        vehicle = Vehicle.builder()
                .id(vehicleId)
                .user(user)
                .connectorType(ConnectorType.CCS2)
                .build();
    }

    // ─── createBooking ────────────────────────────────────────────────────────

    @Test
    @DisplayName("createBooking: should throw IllegalArgumentException when slot end is before slot start")
    void createBooking_Throws_WhenSlotEndBeforeSlotStart() {
        CreateBookingRequest request = buildBookingRequest(vehicleId);
        request.setSlotStart(ZonedDateTime.now().plusHours(2));
        request.setSlotEnd(ZonedDateTime.now().plusHours(1));

        assertThatThrownBy(() -> bookingService.createBooking(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly after slot start");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBooking: should throw IllegalArgumentException when slot start is in the past")
    void createBooking_Throws_WhenSlotStartInPast() {
        CreateBookingRequest request = buildBookingRequest(vehicleId);
        request.setSlotStart(ZonedDateTime.now().minusHours(1));
        request.setSlotEnd(ZonedDateTime.now().plusHours(1));

        assertThatThrownBy(() -> bookingService.createBooking(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot create a booking in the past");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBooking: should pass connector compatibility check and save booking")
    void createBooking_Success_WithMatchingConnector() {
        CreateBookingRequest request = buildBookingRequest(vehicleId);
        Booking savedBooking = buildBooking();
        BookingDto expectedDto = new BookingDto();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chargerRepository.findById(chargerId)).thenReturn(Optional.of(charger));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);
        when(businessMapper.toBookingDto(savedBooking)).thenReturn(expectedDto);

        BookingDto result = bookingService.createBooking(userId, request);

        assertThat(result).isEqualTo(expectedDto);
    }

    @Test
    @DisplayName("createBooking: should throw IllegalArgumentException when connector type mismatches")
    void createBooking_Throws_WhenConnectorMismatch() {
        Vehicle incompatibleVehicle = Vehicle.builder()
                .id(vehicleId)
                .user(user)
                .connectorType(ConnectorType.TYPE2) // charger is CCS2
                .build();
        CreateBookingRequest request = buildBookingRequest(vehicleId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chargerRepository.findById(chargerId)).thenReturn(Optional.of(charger));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(incompatibleVehicle));

        assertThatThrownBy(() -> bookingService.createBooking(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Connector mismatch");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBooking: should throw SecurityException when vehicle does not belong to user")
    void createBooking_Throws_WhenVehicleNotOwnedByUser() {
        UUID otherUserId = UUID.randomUUID();
        User otherUser = User.builder().id(otherUserId).build();
        Vehicle foreignVehicle = Vehicle.builder()
                .id(vehicleId)
                .user(otherUser)
                .connectorType(ConnectorType.CCS2)
                .build();
        CreateBookingRequest request = buildBookingRequest(vehicleId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chargerRepository.findById(chargerId)).thenReturn(Optional.of(charger));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(foreignVehicle));

        assertThatThrownBy(() -> bookingService.createBooking(userId, request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not belong to this user");
    }

    @Test
    @DisplayName("createBooking: should throw ResourceNotFoundException when user not found")
    void createBooking_Throws_WhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(userId, buildBookingRequest(vehicleId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("createBooking: should throw ResourceNotFoundException when charger not found")
    void createBooking_Throws_WhenChargerNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chargerRepository.findById(chargerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(userId, buildBookingRequest(vehicleId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Charger not found");
    }

    @Test
    @DisplayName("createBooking: should set bufferExpiresAt to slotStart + 10 minutes")
    void createBooking_SetsBufferExpiresAt_ToSlotStartPlusTenMinutes() {
        CreateBookingRequest request = buildBookingRequest(vehicleId);
        Booking savedBooking = buildBooking();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chargerRepository.findById(chargerId)).thenReturn(Optional.of(charger));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);
        when(businessMapper.toBookingDto(any())).thenReturn(new BookingDto());

        bookingService.createBooking(userId, request);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking captured = captor.getValue();
        assertThat(captured.getBufferExpiresAt())
                .isEqualTo(request.getSlotStart().plusMinutes(10));
    }

    // ─── cancelBooking ────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelBooking: should set status to cancelled")
    void cancelBooking_Success() {
        Booking booking = buildBooking();
        booking.setUser(user);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenReturn(booking);

        bookingService.cancelBooking(userId, bookingId);

        assertThat(booking.getStatus()).isEqualTo("cancelled");
        assertThat(booking.getCancelledAt()).isNotNull();
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("cancelBooking: should throw SecurityException when user does not own booking")
    void cancelBooking_Throws_WhenNotOwner() {
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        Booking booking = buildBooking();
        booking.setUser(otherUser);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(userId, bookingId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("permission");
    }

    @Test
    @DisplayName("cancelBooking: should throw ResourceNotFoundException when booking not found")
    void cancelBooking_Throws_WhenBookingNotFound() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(userId, bookingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("cancelBooking: should throw IllegalStateException when booking is already cancelled")
    void cancelBooking_Throws_WhenAlreadyCancelled() {
        Booking booking = buildBooking();
        booking.setUser(user);
        booking.setStatus("cancelled");

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(userId, bookingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already cancelled");

        verify(bookingRepository, never()).save(any());
    }

    // ─── claimEarlySlot ───────────────────────────────────────────────────────

    @Test
    @DisplayName("claimEarlySlot: should confirm booking and return DTO for pending status")
    void claimEarlySlot_Success_WhenPending() {
        Booking booking = buildBooking();
        booking.setUser(user);
        booking.setStatus("pending");
        BookingDto expectedDto = new BookingDto();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenReturn(booking);
        when(businessMapper.toBookingDto(booking)).thenReturn(expectedDto);

        BookingDto result = bookingService.claimEarlySlot(userId, bookingId);

        assertThat(result).isEqualTo(expectedDto);
        assertThat(booking.getStatus()).isEqualTo("confirmed");
    }

    @Test
    @DisplayName("claimEarlySlot: should throw IllegalStateException for non-claimable status")
    void claimEarlySlot_Throws_WhenStatusIsCompleted() {
        Booking booking = buildBooking();
        booking.setUser(user);
        booking.setStatus("completed");

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.claimEarlySlot(userId, bookingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not in a claimable state");
    }

    @Test
    @DisplayName("claimEarlySlot: should throw SecurityException when not the booking owner")
    void claimEarlySlot_Throws_WhenNotOwner() {
        Booking booking = buildBooking();
        booking.setUser(User.builder().id(UUID.randomUUID()).build());
        booking.setStatus("pending");

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.claimEarlySlot(userId, bookingId))
                .isInstanceOf(SecurityException.class);
    }

    // ─── joinWaitlist ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("joinWaitlist: should save waitlist entry successfully")
    void joinWaitlist_Success() {
        JoinWaitlistRequest request = new JoinWaitlistRequest();
        request.setChargerId(chargerId);
        request.setVehicleId(vehicleId);
        request.setWantedFrom(ZonedDateTime.now().plusHours(1));
        request.setWantedTo(ZonedDateTime.now().plusHours(3));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chargerRepository.findById(chargerId)).thenReturn(Optional.of(charger));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(bookingRepository.hasOverlappingBookingForUser(userId, request.getWantedFrom(), request.getWantedTo())).thenReturn(false);
        when(waitlistRepository.hasOverlappingWaitlistForUser(userId, request.getWantedFrom(), request.getWantedTo())).thenReturn(false);
        when(waitlistRepository.save(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.joinWaitlist(userId, request);

        verify(waitlistRepository).save(any(Waitlist.class));
    }

    @Test
    @DisplayName("joinWaitlist: should fail if user has overlapping booking")
    void joinWaitlist_Throws_WhenOverlappingBooking() {
        JoinWaitlistRequest request = new JoinWaitlistRequest();
        request.setChargerId(chargerId);
        request.setVehicleId(vehicleId);
        request.setWantedFrom(ZonedDateTime.now().plusHours(1));
        request.setWantedTo(ZonedDateTime.now().plusHours(3));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chargerRepository.findById(chargerId)).thenReturn(Optional.of(charger));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(bookingRepository.hasOverlappingBookingForUser(userId, request.getWantedFrom(), request.getWantedTo())).thenReturn(true);

        assertThatThrownBy(() -> bookingService.joinWaitlist(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already have a booking or waitlist entry");

        verify(waitlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("joinWaitlist: should throw IllegalArgumentException when wantedTo is before wantedFrom")
    void joinWaitlist_Throws_WhenWantedToBeforeWantedFrom() {
        JoinWaitlistRequest request = new JoinWaitlistRequest();
        request.setChargerId(chargerId);
        request.setVehicleId(vehicleId);
        request.setWantedFrom(ZonedDateTime.now().plusHours(3));
        request.setWantedTo(ZonedDateTime.now().plusHours(1));

        assertThatThrownBy(() -> bookingService.joinWaitlist(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly after start time");

        verify(waitlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("joinWaitlist: should throw IllegalArgumentException when wantedFrom is in the past")
    void joinWaitlist_Throws_WhenWantedFromInPast() {
        JoinWaitlistRequest request = new JoinWaitlistRequest();
        request.setChargerId(chargerId);
        request.setVehicleId(vehicleId);
        request.setWantedFrom(ZonedDateTime.now().minusHours(1));
        request.setWantedTo(ZonedDateTime.now().plusHours(1));

        assertThatThrownBy(() -> bookingService.joinWaitlist(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("time in the past");

        verify(waitlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("joinWaitlist: should throw ResourceNotFoundException when charger not found")
    void joinWaitlist_Throws_WhenChargerNotFound() {
        JoinWaitlistRequest request = new JoinWaitlistRequest();
        request.setChargerId(UUID.randomUUID());
        request.setVehicleId(vehicleId);
        request.setWantedFrom(ZonedDateTime.now().plusHours(1));
        request.setWantedTo(ZonedDateTime.now().plusHours(3));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> bookingService.joinWaitlist(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Charger not found");
    }

    // ─── getMyWaitlist ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMyWaitlist: should return mapped DTOs for user's waitlist entries")
    void getMyWaitlist_ReturnsListOfDtos() {
        Waitlist w1 = Waitlist.builder().id(UUID.randomUUID()).build();
        WaitlistDto dto1 = new WaitlistDto();

        when(waitlistRepository.findByUserIdOrderByJoinedAtDesc(userId)).thenReturn(List.of(w1));
        when(businessMapper.toWaitlistDto(w1)).thenReturn(dto1);

        List<WaitlistDto> result = bookingService.getMyWaitlist(userId);

        assertThat(result).hasSize(1).contains(dto1);
    }

    @Test
    @DisplayName("getMyWaitlist: should return empty list when no waitlist entries")
    void getMyWaitlist_ReturnsEmptyList_WhenNoneFound() {
        when(waitlistRepository.findByUserIdOrderByJoinedAtDesc(userId)).thenReturn(List.of());

        List<WaitlistDto> result = bookingService.getMyWaitlist(userId);

        assertThat(result).isEmpty();
    }

    // ─── getMyBookings ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMyBookings: should return mapped DTOs for user's bookings")
    void getMyBookings_ReturnsListOfDtos() {
        Booking b1 = Booking.builder().id(UUID.randomUUID()).build();
        BookingDto dto1 = new BookingDto();

        when(bookingRepository.findByUserIdOrderBySlotStartDesc(userId)).thenReturn(List.of(b1));
        when(businessMapper.toBookingDto(b1)).thenReturn(dto1);

        List<BookingDto> result = bookingService.getMyBookings(userId);

        assertThat(result).hasSize(1).contains(dto1);
    }

    // ─── leaveWaitlist ────────────────────────────────────────────────────────

    @Test
    @DisplayName("leaveWaitlist: should set status to cancelled")
    void leaveWaitlist_Success() {
        Waitlist waitlist = Waitlist.builder()
                .id(waitlistId)
                .user(user)
                .status("waiting")
                .build();

        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(waitlist));
        when(waitlistRepository.save(any())).thenReturn(waitlist);

        bookingService.leaveWaitlist(userId, waitlistId);

        assertThat(waitlist.getStatus()).isEqualTo("cancelled");
        verify(waitlistRepository).save(waitlist);
    }

    @Test
    @DisplayName("leaveWaitlist: should throw SecurityException when user is not the owner")
    void leaveWaitlist_Throws_WhenNotOwner() {
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        Waitlist waitlist = Waitlist.builder()
                .id(waitlistId)
                .user(otherUser)
                .status("waiting")
                .build();

        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(waitlist));

        assertThatThrownBy(() -> bookingService.leaveWaitlist(userId, waitlistId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("permission");
    }

    @Test
    @DisplayName("leaveWaitlist: should throw ResourceNotFoundException when entry not found")
    void leaveWaitlist_Throws_WhenNotFound() {
        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.leaveWaitlist(userId, waitlistId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("leaveWaitlist: should throw IllegalStateException when waitlist entry is already cancelled")
    void leaveWaitlist_Throws_WhenAlreadyCancelled() {
        Waitlist waitlist = Waitlist.builder()
                .id(waitlistId)
                .user(user)
                .status("cancelled")
                .build();

        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(waitlist));

        assertThatThrownBy(() -> bookingService.leaveWaitlist(userId, waitlistId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already cancelled");

        verify(waitlistRepository, never()).save(any());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private CreateBookingRequest buildBookingRequest(UUID vehicleIdParam) {
        CreateBookingRequest r = new CreateBookingRequest();
        r.setChargerId(chargerId);
        r.setVehicleId(vehicleIdParam);
        r.setSlotStart(ZonedDateTime.now().plusHours(1));
        r.setSlotEnd(ZonedDateTime.now().plusHours(2));
        return r;
    }

    private Booking buildBooking() {
        return Booking.builder()
                .id(bookingId)
                .user(user)
                .charger(charger)
                .slotStart(ZonedDateTime.now().plusHours(1))
                .slotEnd(ZonedDateTime.now().plusHours(2))
                .status("pending")
                .build();
    }
}
