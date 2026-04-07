package com.chargelink.service;

import com.chargelink.client.SupabaseAuthClient;
import com.chargelink.dto.LoginRequest;
import com.chargelink.dto.SignupRequest;
import com.chargelink.entity.User;
import com.chargelink.exception.AuthException;
import com.chargelink.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SupabaseAuthClient supabaseAuthClient;

    @InjectMocks
    private AuthService authService;

    private SupabaseAuthClient.SupabaseAuthResponse mockAuthResponse;
    private SupabaseAuthClient.SupabaseUser mockSupabaseUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        mockSupabaseUser = new SupabaseAuthClient.SupabaseUser();
        mockSupabaseUser.setId(userId);
        mockSupabaseUser.setEmail("test@example.com");
        mockSupabaseUser.setUserMetadata(Map.of("full_name", "Test User"));

        mockAuthResponse = new SupabaseAuthClient.SupabaseAuthResponse();
        mockAuthResponse.setAccessToken("test_access_token");
        mockAuthResponse.setUser(mockSupabaseUser);
    }

    // ─── Login Tests ────────────────────────────────────────────────────────────

    @Test
    void login_Success_WhenUserExistsLocally() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        User existingUser = User.builder().id(userId).email("test@example.com").build();

        when(supabaseAuthClient.login(request.getEmail(), request.getPassword())).thenReturn(mockAuthResponse);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String token = authService.login(request);

        assertNotNull(token);
        assertEquals("test_access_token", token);
        verify(userRepository, times(2)).findById(userId); // once for guard, once for refresh
    }

    @Test
    void login_Throws_WhenUserNotRegisteredLocally() {
        LoginRequest request = LoginRequest.builder()
                .email("ghost@example.com")
                .password("password123")
                .build();

        // Supabase credentials are valid, but user does NOT exist in local DB
        when(supabaseAuthClient.login(request.getEmail(), request.getPassword())).thenReturn(mockAuthResponse);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class, () -> authService.login(request));
        assertEquals("User not registered. Please sign up first.", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    // ─── Signup Tests ────────────────────────────────────────────────────────────

    @Test
    void signup_Success_CreatesNewLocalUser() {
        SignupRequest request = SignupRequest.builder()
                .email("test@example.com")
                .password("password123")
                .fullName("Test User")
                .phone(9876543210L)
                .build();

        when(supabaseAuthClient.signup(request.getEmail(), request.getPassword(), request.getFullName(), request.getPhone()))
                .thenReturn(mockAuthResponse);
        // User doesn't exist yet locally (new signup)
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String token = authService.signup(request);

        assertNotNull(token);
        assertEquals("test_access_token", token);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void signup_Success_UpdatesExistingLocalUser() {
        SignupRequest request = SignupRequest.builder()
                .email("test@example.com")
                .password("password123")
                .fullName("Test User")
                .build();

        User existingUser = User.builder().id(userId).build();

        when(supabaseAuthClient.signup(request.getEmail(), request.getPassword(), request.getFullName(), null))
                .thenReturn(mockAuthResponse);
        // User already exists locally (e.g. re-signup after bounce)
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.signup(request);

        verify(userRepository, times(1)).save(any(User.class));
    }
}
