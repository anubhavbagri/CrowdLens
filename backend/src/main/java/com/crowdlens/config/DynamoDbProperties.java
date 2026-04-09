package com.crowdlens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dynamodb")
public record DynamoDbProperties(
        String endpoint,
        String region,
        String tableName,
        int ttlHours,
        double similarityThreshold) {
}
