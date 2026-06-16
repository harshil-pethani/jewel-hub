package com.hpethani.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration — single place to configure ObjectMapper for the entire gateway.
 *
 * WHY a @Bean instead of new ObjectMapper() in each class?
 *  - Spring injects the SAME configured instance everywhere (singleton)
 *  - Any class that needs ObjectMapper just declares it in its constructor
 *  - Config changes (new modules, features) apply everywhere automatically
 *
 * WHY JavaTimeModule?
 *  - Jackson doesn't support Java 8 date/time types (LocalDateTime, Instant, etc.) by default
 *  - Without it: LocalDateTime serializes as [2026, 5, 31, 14, 36, 14] (array) ❌
 *  - With it + WRITE_DATES_AS_TIMESTAMPS=false: "2026-05-31T14:36:14.588" (ISO-8601 string) ✅
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register JavaTimeModule — adds support for LocalDateTime, LocalDate, Instant, etc.
        mapper.registerModule(new JavaTimeModule());

        // CRITICAL: disable timestamp format — without this, JavaTimeModule still outputs arrays
        // [2026, 5, 31, 14, 36, 14] → disabled → "2026-05-31T14:36:14.588" (ISO-8601)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}

