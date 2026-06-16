package com.hpethani.authservice.service;

import com.hpethani.authservice.entity.CustomUserDetails;
import com.hpethani.authservice.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// If we don't write @Service or @Component annotation here than we have to mention UserDetailsService in SecurityConfig.
// http.userDetailsService(getUserDetailsService());
// getUserDetailsService with Bean annotation return instance of CustomUserDetailsService
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}