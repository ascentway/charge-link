package com.chargelink.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class SupabaseUserDetails implements UserDetails {

    private final UUID id;
    private final String email;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList(); // Add role mapping here if we use Supabase custom claims
    }

    @Override
    public String getPassword() {
        return null; // Stateless JWT, no password handled by backend
    }

    @Override
    public String getUsername() {
        return id.toString(); // We treat the UUID subject as the principal
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
