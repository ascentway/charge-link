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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
@DisplayName("SessionService Unit Tests")
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ChargerRepository chargerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private BusinessMapper businessMapper;

    @InjectMocks
    private SessionService sessionService;

    private UUID userId;
    private UUID sessionId;
    private UUID chargerId;
    private UUID bookingId;
    private User user;
    private Charger charger;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        chargerId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        user    = User.builder().id(userId).build();
        charger = Charger.builder().id(chargerId).build();
    }

    // ─── startSession ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("startSession: should create session without booking when bookingId is null")
    void startSession_Success_WithoutBooking() {
        StartSessionRequest request = new StartSessionRequest();
        request.setChargerId(chargerId);

        Session savedSession = buildSession(null);
        SessionDto expectedDto = new SessionDto();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chargerRepository.findById(chargerId)).thenReturn(Optional.of(charger));
        when(sessionRepository.save(any(Session.class))).thenReturn(savedSession);
        when(businessMapper.toSessionDto(savedSession)).thenReturn(expectedDto);

        SessionDto result = sessionService.startSession(userId, request);

        assertThat(result).isEqualTo(expectedDto);
        verify(bookingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("startSession: should transition booking to active when bookingId provided")
    void startSession_TransitionsBookingToActive_WhenBookingProvided() {
        StartSessionRequest request = new StartSessionRequest();
        request.setChargerId(chargerId);
        request.setBookingId(bookingId);

        Booking booking = buildBooking();
        Session savedSession = buildSession(booking);
        SessionDto expectedDto = new SessionDto();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chargerRepository.findById(chargerId)).thenReturn(Optional.of(charger));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);
        when(sessionRepository.save(any(Session.class))).thenReturn(savedSession);
        when(businessMapper.toSessionDto(savedSession)).thenReturn(expectedDto);

        sessionService.startSession(userId, request);

        assertThat(booking.getStatus()).isEqualTo("active");
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("startSession: should throw ResourceNotFoundException when user not found")
    void startSession_Throws_WhenUserNotFound() {
        StartSessionRequest request = new StartSessionRequest();
        request.setChargerId(chargerId);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.startSession(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("startSession: should throw ResourceNotFoundException when charger not found")
    void startSession_Throws_WhenChargerNotFound() {
        StartSessionRequest request = new StartSessionRequest();
        request.setChargerId(chargerId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chargerRepository.findById(chargerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.startSession(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Charger not found");
    }

    @Test
    @DisplayName("startSession: should throw ResourceNotFoundException when booking not found")
    void startSession_Throws_WhenBookingNotFound() {
        StartSessionRequest request = new StartSessionRequest();
        request.setChargerId(chargerId);
        request.setBookingId(bookingId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chargerRepository.findById(chargerId)).thenReturn(Optional.of(charger));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.startSession(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Booking not found");
    }

    // ─── stopSession ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("stopSession: should set endedAt and billing fields on the session")
    void stopSession_Success_SetsSessionFields() {
        Session session = buildSession(null);
        session.setUser(user);
        StopSessionRequest request = buildStopRequest();
        SessionDto expectedDto = new SessionDto();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenReturn(session);
        when(businessMapper.toSessionDto(session)).thenReturn(expectedDto);

        SessionDto result = sessionService.stopSession(userId, sessionId, request);

        assertThat(result).isEqualTo(expectedDto);
        assertThat(session.getEndedAt()).isNotNull();
        assertThat(session.getEnergyDeliveredKwh()).isEqualTo(request.getEnergyDeliveredKwh());
        assertThat(session.getDurationMinutes()).isEqualTo(request.getDurationMinutes());
        assertThat(session.getAmountCharged()).isEqualTo(request.getAmountCharged());
    }

    @Test
    @DisplayName("stopSession: should mark linked booking as completed")
    void stopSession_MarksBookingCompleted_WhenBookingLinked() {
        Booking booking = buildBooking();
        booking.setSlotEnd(ZonedDateTime.now().plusHours(2)); // far future → no early-finish notification
        charger.setId(chargerId);
        booking.setCharger(charger);

        Session session = buildSession(booking);
        session.setUser(user);
        StopSessionRequest request = buildStopRequest();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);
        when(sessionRepository.save(any(Session.class))).thenReturn(session);
        when(businessMapper.toSessionDto(session)).thenReturn(new SessionDto());

        sessionService.stopSession(userId, sessionId, request);

        assertThat(booking.getStatus()).isEqualTo("completed");
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("stopSession: should notify next user when session ends before slot end")
    void stopSession_NotifiesNextUser_WhenSessionEndsEarly() {
        // slot ends 2 hours from now, but we're stopping the session right now → early finish
        Booking booking = buildBooking();
        booking.setSlotEnd(ZonedDateTime.now().plusHours(2));
        booking.setCharger(charger);

        Session session = buildSession(booking);
        session.setUser(user);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(bookingRepository.save(any())).thenReturn(booking);
        when(sessionRepository.save(any())).thenReturn(session);
        when(businessMapper.toSessionDto(session)).thenReturn(new SessionDto());
        when(bookingRepository.findNextConfirmedBooking(any(), any(), any())).thenReturn(List.of());

        sessionService.stopSession(userId, sessionId, buildStopRequest());

        verify(bookingRepository).findNextConfirmedBooking(any(), any(), any());
    }

    @Test
    @DisplayName("stopSession: should throw SecurityException when session does not belong to user")
    void stopSession_Throws_WhenNotSessionOwner() {
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        Session session = buildSession(null);
        session.setUser(otherUser);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.stopSession(userId, sessionId, buildStopRequest()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("permission");
    }

    @Test
    @DisplayName("stopSession: should throw ResourceNotFoundException when session not found")
    void stopSession_Throws_WhenSessionNotFound() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.stopSession(userId, sessionId, buildStopRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Session not found");
    }

    // ─── getUserSessions ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserSessions: should return mapped list of session DTOs")
    void getUserSessions_ReturnsMappedList() {
        Session s1 = buildSession(null);
        SessionDto dto1 = new SessionDto();

        when(sessionRepository.findByUserIdOrderByStartedAtDesc(userId)).thenReturn(List.of(s1));
        when(businessMapper.toSessionDto(s1)).thenReturn(dto1);

        List<SessionDto> result = sessionService.getUserSessions(userId);

        assertThat(result).hasSize(1).contains(dto1);
    }

    @Test
    @DisplayName("getUserSessions: should return empty list when user has no sessions")
    void getUserSessions_ReturnsEmptyList_WhenNoSessions() {
        when(sessionRepository.findByUserIdOrderByStartedAtDesc(userId)).thenReturn(List.of());

        List<SessionDto> result = sessionService.getUserSessions(userId);

        assertThat(result).isEmpty();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Session buildSession(Booking booking) {
        return Session.builder()
                .id(sessionId)
                .user(user)
                .charger(charger)
                .booking(booking)
                .startedAt(ZonedDateTime.now())
                .build();
    }

    private Booking buildBooking() {
        return Booking.builder()
                .id(bookingId)
                .user(user)
                .charger(charger)
                .slotStart(ZonedDateTime.now().minusHours(1))
                .slotEnd(ZonedDateTime.now().plusHours(1))
                .status("active")
                .build();
    }

    private StopSessionRequest buildStopRequest() {
        StopSessionRequest r = new StopSessionRequest();
        r.setEnergyDeliveredKwh(new BigDecimal("12.5"));
        r.setDurationMinutes(60);
        r.setAmountCharged(new BigDecimal("150.00"));
        return r;
    }
}
