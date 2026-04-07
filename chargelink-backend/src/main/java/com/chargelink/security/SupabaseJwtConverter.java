package com.chargelink.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SupabaseJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {
        String userIdStr = source.getSubject();
        String email = source.getClaimAsString("email");

        UUID userId = UUID.fromString(userIdStr);
        SupabaseUserDetails userDetails = new SupabaseUserDetails(userId, email);

        return new UsernamePasswordAuthenticationToken(
                userDetails,
                source,  // The JWT token itself as credentials
                userDetails.getAuthorities()
        );
    }
}
