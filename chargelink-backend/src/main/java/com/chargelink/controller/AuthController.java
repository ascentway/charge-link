package com.chargelink.controller;

import com.chargelink.dto.AuthResponse;
import com.chargelink.dto.LoginRequest;
import com.chargelink.dto.SignupRequest;
import com.chargelink.security.CookieBearerTokenResolver;
import com.chargelink.security.SupabaseUserDetails;
import com.chargelink.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for User Login, Registration, and Session Management")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Log in a user", description = "Authenticates user against Supabase and issues an HTTP-Only secure cookie containing the access token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully authenticated"),
            @ApiResponse(responseCode = "400", description = "Invalid request format"),
            @ApiResponse(responseCode = "401", description = "Invalid login credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);

        ResponseCookie cookie = ResponseCookie.from(CookieBearerTokenResolver.ACCESS_TOKEN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(false) // Set to false for local HTTP development
                .path("/")
                .maxAge(3600) // 1 hr, matches Supabase default access_token lifespan
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(AuthResponse.builder()
                        .message("Successfully logged in")
                        .build());
    }

    @Operation(summary = "Register a new user", description = "Creates a new user profile via Supabase Auth and optionally auto-logs them in by setting the cookie.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully registered"),
            @ApiResponse(responseCode = "400", description = "Invalid form details or email already in use")
    })
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        String token = authService.signup(request);

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();

        if (token != null && !token.isBlank()) {
            ResponseCookie cookie = ResponseCookie.from(CookieBearerTokenResolver.ACCESS_TOKEN_COOKIE_NAME, token)
                    .httpOnly(true)
                    .secure(false) //set it to true in production
                    .path("/")
                    .maxAge(3600)
                    .sameSite("Lax")
                    .build();
            responseBuilder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return responseBuilder.body(AuthResponse.builder()
                .message("Successfully registered")
                .build());
    }

    @Operation(summary = "Log out user", description = "Immediately invalidates the HttpOnly cookie containing the access token.")
    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout() {
        ResponseCookie cookie = ResponseCookie.from(CookieBearerTokenResolver.ACCESS_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0) // Expire cookie instantly
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(AuthResponse.builder()
                        .message("Successfully logged out")
                        .build());
    }

    @Operation(summary = "Delete my account", description = "Permanently removes user profile from local database and Supabase Auth. Cascades to vehicles and bookings.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Account successfully deleted"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session")
    })
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal SupabaseUserDetails userDetails) {
        authService.deleteAccount(userDetails.getId());
    }
}
