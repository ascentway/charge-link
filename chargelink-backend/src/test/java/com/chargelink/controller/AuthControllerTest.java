package com.chargelink.controller;

import com.chargelink.dto.LoginRequest;
import com.chargelink.dto.SignupRequest;
import com.chargelink.security.CookieBearerTokenResolver;
import com.chargelink.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Disabling security filters for pure controller logic testing
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void testLogin_Success() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn("mocked_jwt_token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CookieBearerTokenResolver.ACCESS_TOKEN_COOKIE_NAME))
                .andExpect(cookie().value(CookieBearerTokenResolver.ACCESS_TOKEN_COOKIE_NAME, "mocked_jwt_token"))
                .andExpect(cookie().httpOnly(CookieBearerTokenResolver.ACCESS_TOKEN_COOKIE_NAME, true))
                .andExpect(jsonPath("$.message").value("Successfully logged in"));
    }

    @Test
    void testSignup_Success() throws Exception {
        SignupRequest request = SignupRequest.builder()
                .email("newuser@example.com")
                .password("password123")
                .fullName("New User")
                .build();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully registered"));
    }

    @Test
    void testLogin_ValidationFailure() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("invalid-email")
                .password("")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }



    @Test
    void testLogout_Success() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(CookieBearerTokenResolver.ACCESS_TOKEN_COOKIE_NAME, 0)) // Verifying cookie is destroyed
                .andExpect(jsonPath("$.message").value("Successfully logged out"));
    }
}
