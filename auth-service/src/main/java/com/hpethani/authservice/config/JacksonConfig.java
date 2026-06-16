package com.hpethani.authservice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Configures a single shared ObjectMapper:
     *  - JavaTimeModule: enables LocalDateTime / LocalDate serialization
     *  - WRITE_DATES_AS_TIMESTAMPS=false: produces ISO-8601 strings ("2026-06-01T12:00:00")
     *    instead of arrays ([2026, 6, 1, 12, 0, 0])
     *  - FAIL_ON_UNKNOWN_PROPERTIES=false: safely ignores unknown fields in responses
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}

