package com.crowdlens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reddit")
public record RedditProperties(
        String clientId,
        String clientSecret,
        String username,
        String password,
        String userAgent) {
}
