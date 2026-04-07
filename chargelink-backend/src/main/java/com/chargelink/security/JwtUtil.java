package com.chargelink.security;

import com.chargelink.exception.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Utility class for extracting authenticated user information from the Spring Security context.
 *
 * <p>In this architecture, JWTs are issued by Supabase and validated by Spring Security
 * via the JWKS endpoint. This class provides a centralized, reusable API so that
 * services do not need to directly interact with the {@link SecurityContextHolder}.
 *
 * <p>The principal is a {@link SupabaseUserDetails} object populated by
 * {@link SupabaseJwtConverter} after successful JWT validation.
 */
@Component
public class JwtUtil {

    /**
     * Returns the full {@link SupabaseUserDetails} of the currently authenticated user.
     *
     * @throws AuthException with 401 if no authenticated user is found in the context.
     */
    public SupabaseUserDetails getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new AuthException("No authenticated user found in security context", HttpStatus.UNAUTHORIZED);
        }
        return userDetails;
    }

    /**
     * Returns the UUID of the currently authenticated user.
     *
     * @throws AuthException with 401 if no authenticated user is found.
     */
    public UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }

    /**
     * Returns the email of the currently authenticated user.
     *
     * @throws AuthException with 401 if no authenticated user is found.
     */
    public String getCurrentUserEmail() {
        return getCurrentUser().getEmail();
    }
}
