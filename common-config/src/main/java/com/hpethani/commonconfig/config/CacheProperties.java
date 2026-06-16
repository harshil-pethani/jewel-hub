package com.hpethani.commonconfig.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "service.cache")
public class CacheProperties {

    /**
     * Default TTL in minutes.
     */
    private long ttlMinutes = 10;

    public long getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }
}