package com.chargelink.service;

import com.chargelink.client.SupabaseAuthClient;
import com.chargelink.dto.LoginRequest;
import com.chargelink.dto.SignupRequest;
import com.chargelink.entity.User;
import com.chargelink.exception.AuthException;
import com.chargelink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final SupabaseAuthClient supabaseAuthClient;

    @Transactional
    public String login(LoginRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim() : "";
        log.info("Logging in user: {}", email);

        SupabaseAuthClient.SupabaseAuthResponse response = supabaseAuthClient.login(email, request.getPassword());

        // Guard: credentials were valid at Supabase, but the user must also exist
        // in our local DB (i.e. they went through our signup flow).
        SupabaseAuthClient.SupabaseUser supabaseUser = response.getUser();
        if (supabaseUser == null || supabaseUser.getId() == null) {
            throw new AuthException("Authentication failed: could not resolve user identity.", HttpStatus.UNAUTHORIZED);
        }

        userRepository.findById(supabaseUser.getId())
                .orElseThrow(() -> new AuthException(
                        "User not registered. Please sign up first.", HttpStatus.UNAUTHORIZED));

        // User exists locally — refresh their profile from the latest Supabase metadata
        refreshLocalUser(supabaseUser);

        return response.getAccessToken();
    }

    @Transactional
    public String signup(SignupRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim() : "";
        Long phone = request.getPhone();
        log.info("Registering user: {}", email);

        // 1. Pre-check: Ensure phone and email are unique in local DB to avoid Supabase ghost users
        if (userRepository.existsByEmail(email)) {
            throw new AuthException("An account with this email address already exists.", HttpStatus.CONFLICT);
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new AuthException("This phone number is already linked to another account.", HttpStatus.CONFLICT);
        }

        // 2. Register with Supabase Auth
        SupabaseAuthClient.SupabaseAuthResponse response = supabaseAuthClient.signup(
                email, request.getPassword(), request.getFullName(), phone);

        SupabaseAuthClient.SupabaseUser supabaseUser = response.getUser();

        try {
            // 3. Create the local user profile
            createLocalUser(supabaseUser, phone);
        } catch (Exception e) {
            // 4. Compensation: If local DB fails, cleanup Supabase Auth so we don't leave a ghost user
            log.error("Local database save failed for user {}. Cleaning up Supabase Auth...", email, e);
            if (supabaseUser != null && supabaseUser.getId() != null) {
                try {
                    supabaseAuthClient.deleteUser(supabaseUser.getId());
                } catch (Exception cleanupEx) {
                    log.error("Failed to cleanup Supabase user {}: {}", supabaseUser.getId(), cleanupEx.getMessage());
                }
            }
            throw e; // Re-throw the original error (GlobalExceptionHandler will handle)
        }

        return response.getAccessToken();
    }

    @Transactional
    public void deleteAccount(java.util.UUID userId) {
        log.info("Deleting user account: {}", userId);

        // 1. Delete from local DB (triggers CASCADE to vehicles, bookings, etc.)
        userRepository.findById(userId).ifPresentOrElse(
                user -> userRepository.delete(user),
                () -> { throw new AuthException("User not found", HttpStatus.NOT_FOUND); }
        );

        // 2. Delete from Supabase Auth (Remote)
        supabaseAuthClient.deleteUser(userId);
    }

    /**
     * Called during SIGNUP. Creates a new local user row, or updates if already exists
     * (e.g. re-signup after an email confirm bounce).
     */
    private User createLocalUser(SupabaseAuthClient.SupabaseUser supabaseUser, Long phone) {
        if (supabaseUser == null || supabaseUser.getId() == null) {
            throw new AuthException("Registration failed: could not resolve user identity.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        User user = userRepository.findById(supabaseUser.getId())
                .orElseGet(() -> User.builder().id(supabaseUser.getId()).build());

        applyMetadata(user, supabaseUser);

        // Phone comes directly from the request during signup (not metadata round-trip)
        if (phone != null) {
            user.setPhone(phone);
        }

        return userRepository.save(user);
    }

    /**
     * Called during LOGIN. Only updates an existing user's profile — never creates one.
     * This ensures deleted/unregistered users cannot log in.
     */
    private void refreshLocalUser(SupabaseAuthClient.SupabaseUser supabaseUser) {
        userRepository.findById(supabaseUser.getId()).ifPresent(user -> {
            applyMetadata(user, supabaseUser);
            userRepository.save(user);
        });
    }

    /**
     * Applies shared fields from Supabase user data to a local User entity.
     */
    private void applyMetadata(User user, SupabaseAuthClient.SupabaseUser supabaseUser) {
        if (supabaseUser.getEmail() != null && !supabaseUser.getEmail().isBlank()) {
            user.setEmail(supabaseUser.getEmail().trim());
        }

        if (supabaseUser.getUserMetadata() != null) {
            Object nameObj = supabaseUser.getUserMetadata().get("full_name");
            if (nameObj != null && !nameObj.toString().isBlank()) {
                user.setFullName(nameObj.toString());
            }
            // Phone in metadata is only used during signup; skip here to avoid overwriting
        }
    }
}
