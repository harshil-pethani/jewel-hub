package com.hpethani.commonsecurity.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hpethani.commonsecurity.entrypoint.ServiceAuthEntryPoint;
import com.hpethani.commonsecurity.feign.FeignHeaderPropagationInterceptor;
import com.hpethani.commonsecurity.filter.GatewayHeaderAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;


/**
 * CommonSecurityAutoConfig — Spring Boot Auto-Configuration for downstream microservices.
 *
 * Provides defense-in-depth security for services that sit behind the API Gateway.
 * Instead of validating JWTs themselves, these services trust the identity headers
 * set by the Gateway after it validates the token.
 *
 * What this auto-config provides:
 *   1. GatewayHeaderAuthFilter  — reads "email"/"roles" headers → sets SecurityContext
 *   2. ServiceAuthEntryPoint    — returns 401 JSON on direct port access
 *   3. SecurityFilterChain      — STATELESS, no sessions/forms/basic auth
 *
 * Activation:
 *   - Auto-applied to all services that have this dependency on their classpath
 *   - If a service provides its own SecurityFilterChain bean (e.g., auth-service),
 *     this auto-config's chain is SKIPPED via @ConditionalOnMissingBean
 *
 * Each service can customize public paths in application.properties:
 *   service.security.public-paths=/actuator/health,/actuator/info,/some/public/api
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, HttpSecurity.class})
@EnableConfigurationProperties(ServiceSecurityProperties.class)
public class CommonSecurityAutoConfig {

    /**
     * Shared ObjectMapper for the entry point — configured with JavaTimeModule
     * so timestamps in 401 responses are ISO-8601 strings, not arrays.
     *
     * @ConditionalOnMissingBean — if the service already has a custom ObjectMapper @Bean
     * (e.g., cart-service's JacksonConfig), use that one instead.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper commonSecurityObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * GatewayHeaderAuthFilter bean — not annotated with @Component to avoid
     * being auto-scanned. Managed solely by this auto-config.
     */
    @Bean
    @ConditionalOnMissingBean(GatewayHeaderAuthFilter.class)
    public GatewayHeaderAuthFilter gatewayHeaderAuthFilter(ServiceSecurityProperties props) {
        return new GatewayHeaderAuthFilter(props.getGatewaySecret());
    }

    /**
     * Prevent Spring Boot from auto-registering GatewayHeaderAuthFilter as a
     * plain servlet filter. It should ONLY run through the Spring Security filter chain
     * (added via addFilterBefore). Without this, the filter runs twice per request.
     */
    @Bean
    public FilterRegistrationBean<GatewayHeaderAuthFilter> gatewayHeaderFilterRegistration(
            GatewayHeaderAuthFilter filter) {
        FilterRegistrationBean<GatewayHeaderAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    /**
     * ServiceAuthEntryPoint bean — returns consistent JSON 401 on direct port access.
     */
    @Bean
    @ConditionalOnMissingBean(ServiceAuthEntryPoint.class)
    public ServiceAuthEntryPoint serviceAuthEntryPoint(ObjectMapper objectMapper) {
        return new ServiceAuthEntryPoint(objectMapper);
    }

    /**
     * FeignConfig — registers FeignHeaderPropagationInterceptor ONLY when
     * feign.RequestInterceptor is on the classpath (i.e., service uses Feign).
     *
     * This nested @Configuration class is the standard Spring Boot pattern for
     * conditional beans that depend on optional classpath entries.
     * The outer class is loaded first; this inner class is only processed if
     * the @ConditionalOnClass condition passes.
     *
     * Services with Feign (product, cart, order):      interceptor IS registered
     * Services without Feign (inventory):              interceptor is NOT registered
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class FeignSecurityConfig {

        /**
         * Propagates X-Gateway-Secret + email/roles/userid headers on ALL outgoing Feign calls.
         *
         * This solves the service-to-service auth problem:
         *   product-service → inventory-service (via Feign)
         *   cart-service    → product-service  (via Feign)
         *   order-service   → cart-service     (via Feign)
         *   order-service   → inventory-service (via Feign)
         *
         * Without this, receiving services see no X-Gateway-Secret → treat call as
         * unauthorized direct port access → 401.
         */
        @Bean
        @ConditionalOnMissingBean(FeignHeaderPropagationInterceptor.class)
        public FeignHeaderPropagationInterceptor feignHeaderPropagationInterceptor(
                ServiceSecurityProperties props,
                ObjectProvider<HttpServletRequest> requestProvider) {
            return new FeignHeaderPropagationInterceptor(props.getGatewaySecret(), requestProvider);
        }
    }

    private RequestMatcher toRequestMatcher(String config) {

        String[] parts = config.split(":", 2);

        if (parts.length == 2) {
            HttpMethod method = HttpMethod.valueOf(parts[0].trim());
            String pattern = parts[1].trim();

            return new AntPathRequestMatcher(pattern, method.name());
        }

        // Backward compatibility: "/public/**"
        return new AntPathRequestMatcher(config);
    }

    /**
     * Default SecurityFilterChain for downstream services.
     *
     * @ConditionalOnMissingBean(SecurityFilterChain.class) ensures:
     *   - auth-service (has its own SecurityFilterChain) → this bean is SKIPPED
     *   - product/cart/order/inventory (no own chain) → this bean is USED
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain serviceSecurityFilterChain(
            HttpSecurity http,
            GatewayHeaderAuthFilter gatewayHeaderAuthFilter,
            ServiceAuthEntryPoint serviceAuthEntryPoint,
            ServiceSecurityProperties props) throws Exception {

        List<RequestMatcher> publicMatchers = props.getPublicPaths()
                .stream()
                .map(this::toRequestMatcher)
                .toList();

        http
                // CSRF not needed — stateless REST API, no browser sessions
                .csrf(AbstractHttpConfigurer::disable)

                // No Spring form login page — this is a REST service
                .formLogin(AbstractHttpConfigurer::disable)

                // No HTTP Basic Auth popup
                .httpBasic(AbstractHttpConfigurer::disable)

                // Stateless — no HTTP sessions. Identity comes from request headers only.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Return JSON 401 instead of Spring's HTML redirect when unauthenticated
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(serviceAuthEntryPoint))

                .authorizeHttpRequests(auth -> auth
                        // Allow actuator health probes + any service-specific public paths
                        .requestMatchers(publicMatchers.toArray(new RequestMatcher[0])).permitAll()

                        // Everything else requires a valid identity (gateway must have set the "email" header)
                        .anyRequest().authenticated()
                )

                // Reads "email" + "roles" headers set by the API Gateway → populates SecurityContext
                .addFilterBefore(gatewayHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

