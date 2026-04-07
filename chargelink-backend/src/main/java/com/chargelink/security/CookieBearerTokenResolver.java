package com.chargelink.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
    public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

    @Override
    public String resolve(HttpServletRequest request) {
        // 1. Try to extract from Cookie
        if (request.getCookies() != null) {
            String cookieToken = Arrays.stream(request.getCookies())
                    .filter(cookie -> ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);

            if (cookieToken != null && !cookieToken.isBlank()) {
                // Log first 50 chars of token header for debugging
                String[] parts = cookieToken.split("\\.");
                if (parts.length >= 2) {
                    log.debug("JWT header from cookie (base64): {}", parts[0]);
                }
                log.debug("Token source: COOKIE, length={}", cookieToken.length());
                return cookieToken;
            }
        }

        // 2. Fallback to extracting from Authorization Header
        String headerToken = defaultResolver.resolve(request);
        if (headerToken != null) {
            log.debug("Token source: AUTHORIZATION_HEADER");
        }
        return headerToken;
    }
}
