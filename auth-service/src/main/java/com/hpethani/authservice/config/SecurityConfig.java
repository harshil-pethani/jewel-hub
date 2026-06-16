package com.hpethani.authservice.config;

import com.hpethani.authservice.security.JwtFilter;
import com.hpethani.authservice.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for auth-service.
 *
 * Unlike other services (which use common-security's auto-config), auth-service
 * has its own SecurityFilterChain because it has unique requirements:
 *  - /auth/** must be PUBLIC (login/register — no token exists yet)
 *  - /api/user/** must be PROTECTED (requires authenticated user)
 *  - AuthenticationProvider is needed for login credential verification
 *  - AuthenticationManager is needed by AuthService to authenticate users
 *
 * Request flow for protected endpoints (/api/user/**):
 *   Client → API Gateway (validates JWT, adds "email" header)
 *          → JwtFilter (reads "email" header, loads user, sets SecurityContext)
 *          → SecurityFilterChain (checks authenticated())
 *          → Controller
 *
 * Direct port access (no gateway):
 *   Client → JwtFilter (no "email" header → SecurityContext empty)
 *          → SecurityFilterChain blocks → JwtAuthEntryPoint → 401 JSON
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final JwtAuthEntryPoint authEntryPoint;
    private final CustomUserDetailsService userDetailsService;
    // NOTE: PasswordEncoder is NOT injected here — it is defined as a @Bean in this
    // same class. Injecting it would create a circular dependency:
    //   SecurityConfig needs PasswordEncoder → PasswordEncoder is in SecurityConfig → cycle!
    // Solution: call passwordEncoder() method directly where needed.

    /**
     * Prevents JwtFilter from being registered as a plain servlet filter.
     * It is already wired into the Spring Security chain via addFilterBefore().
     * Without this, the filter runs TWICE per request.
     */
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistration(JwtFilter jwtFilter) {
        FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(jwtFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Wires together UserDetailsService + PasswordEncoder.
     * Spring uses this when AuthenticationManager.authenticate() is called in AuthService.login().
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder()); // call directly — no circular dependency
        return provider;
    }

    /**
     * Exposes AuthenticationManager as a bean so it can be injected into AuthService.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())

                // Disables HTTP Basic Authentication.
                .httpBasic(httpBasicConfigurer -> httpBasicConfigurer.disable())

                // Disables Spring Security’s default Form login page
                .formLogin(formLoginConfigurer -> formLoginConfigurer.disable())

                // IMPORTANT: disable session (as not needed when using JWT)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Handle unauthorized errors
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                )

                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        // Spring Boot error forwarding — must be public to avoid 401 hiding real errors
                        .requestMatchers("/error").permitAll()

                        // PUBLIC — login and register never carry a token
                        .requestMatchers("/auth/**").permitAll()

                        // PROTECTED — any authenticated user (USER or ADMIN)
                        .requestMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")

                        // EVERYTHING ELSE
                        .anyRequest().authenticated()
                )

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}


//Request
//   ↓
//JWT Filter
//   ↓
//SecurityContext empty
//   ↓
//Protected API requires authentication
//   ↓
//Spring throws AuthenticationException
//   ↓
//ExceptionTranslationFilter catches it
//   ↓
//AuthenticationEntryPoint's Commence method is called
//   ↓
//401 Response returned
