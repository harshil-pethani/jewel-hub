package com.hpethani.cart_service.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration for cart-service.
 *
 * 1. JavaTimeModule — support for java.time.* types (LocalDateTime etc.)
 * 2. WRITE_DATES_AS_TIMESTAMPS=false — serialize LocalDateTime as ISO-8601 string,
 *    not as array [2026, 5, 31, 14, 36, 2]
 * 3. FAIL_ON_UNKNOWN_PROPERTIES=false — Feign responses from other services may have
 *    extra fields not present in our local DTO subset; ignore them instead of throwing.
 *    (Spring Boot sets this by default, but a custom @Bean ObjectMapper overrides those defaults.)
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}

