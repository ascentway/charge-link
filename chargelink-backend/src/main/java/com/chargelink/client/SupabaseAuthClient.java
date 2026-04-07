package com.chargelink.client;

import com.chargelink.exception.AuthException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class SupabaseAuthClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String serviceRoleKey;

    public SupabaseAuthClient(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.anon.key}") String anonKey,
            @Value("${supabase.service.role.key:}") String serviceRoleKey,
            ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;
        this.serviceRoleKey = serviceRoleKey;
        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl)
                .defaultHeader("apikey", anonKey)
                .defaultHeader("Authorization", "Bearer " + anonKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public SupabaseAuthResponse login(String email, String password) {
        try {
            return restClient.post()
                    .uri("/auth/v1/token?grant_type=password")
                    .body(Map.of("email", email, "password", password))
                    .retrieve()
                    .body(SupabaseAuthResponse.class);
        } catch (HttpClientErrorException e) {
            handleClientError(e, "Login failed");
            return null; // unreachable
        } catch (RestClientException e) {
            log.error("Login failed unexpectedly: {}", e.getMessage());
            throw new AuthException("Unable to connect to authentication service", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public SupabaseAuthResponse signup(String email, String password, String fullName, Long phone) {
        try {
            Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("full_name", fullName);
            if (phone != null) {
                metadata.put("phone", phone);
            }
            return restClient.post()
                    .uri("/auth/v1/signup")
                    .body(Map.of(
                            "email", email,
                            "password", password,
                            "data", metadata
                    ))
                    .retrieve()
                    .body(SupabaseAuthResponse.class);
        } catch (HttpClientErrorException e) {
            handleClientError(e, "Registration failed");
            return null; // unreachable
        } catch (RestClientException e) {
            log.error("Signup failed unexpectedly: {}", e.getMessage());
            throw new AuthException("Unable to connect to authentication service", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public void deleteUser(UUID userId) {
        if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
            log.warn("Missing SUPABASE_SERVICE_ROLE_KEY. Skipping remote user deletion for ID: {}", userId);
            return;
        }

        try {
            restClient.delete()
                    .uri("/auth/v1/admin/users/{userId}", userId)
                    .header("apikey", serviceRoleKey)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Successfully deleted user from Supabase Auth: {}", userId);
        } catch (HttpClientErrorException e) {
            handleClientError(e, "Admin user deletion failed");
        } catch (RestClientException e) {
            log.error("Unexpected error during Supabase admin deletion: {}", e.getMessage());
            throw new AuthException("Unable to connect to authentication service", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void handleClientError(HttpClientErrorException e, String action) {
        String errorMsg = "Action failed";
        try {
            JsonNode errorNode = objectMapper.readTree(e.getResponseBodyAsString());
            if (errorNode.has("msg")) {
                errorMsg = errorNode.get("msg").asText();
            } else if (errorNode.has("message")) {
                errorMsg = errorNode.get("message").asText();
            }
        } catch (Exception ex) {
            log.warn("Could not parse error response from Supabase Provider", ex);
        }

        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        if (status == HttpStatus.BAD_REQUEST && errorMsg.toLowerCase().contains("credential")) {
            status = HttpStatus.UNAUTHORIZED;
        }

        log.warn("{}: {}", action, errorMsg);
        throw new AuthException(errorMsg, status);
    }

    @Data
    public static class SupabaseAuthResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("refresh_token")
        private String refreshToken;

        @JsonProperty("expires_in")
        private Integer expiresIn;

        private SupabaseUser user;
    }

    @Data
    public static class SupabaseUser {
        private UUID id;
        private String email;
        @JsonProperty("user_metadata")
        private Map<String, Object> userMetadata;
    }
}
