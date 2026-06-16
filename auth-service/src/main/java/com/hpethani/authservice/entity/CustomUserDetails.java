package com.hpethani.authservice.entity;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    /** Exposes the user's DB id so AuthService can embed it in the JWT. */
    public Long getUserId() {
        return user.getId();
    }

    @Override
    public List<SimpleGrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override public boolean isAccountNonExpired() { return true; }

    // SUSPENDED accounts — Spring Security will throw LockedException on login
    @Override public boolean isAccountNonLocked() {
        return user.getStatus() != AccountStatus.SUSPENDED;
    }

    @Override public boolean isCredentialsNonExpired() { return true; }

    // DELETED accounts — Spring Security will throw DisabledException on login
    @Override public boolean isEnabled() {
        return user.getStatus() == AccountStatus.ACTIVE;
    }
}