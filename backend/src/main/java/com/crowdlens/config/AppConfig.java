package com.crowdlens.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
@EnableConfigurationProperties({ RedditProperties.class, DynamoDbProperties.class, RateLimitProperties.class })
public class AppConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)); // 10MB for large Reddit responses
    }

    @Bean
    public WebClient redditWebClient(WebClient.Builder builder, RedditProperties redditProps) {
        return builder
                .baseUrl("https://oauth.reddit.com")
                .defaultHeader("User-Agent", redditProps.userAgent())
                .build();
    }

    @Bean
    public WebClient redditPublicWebClient(WebClient.Builder builder, RedditProperties redditProps) {
        return builder
                .baseUrl("https://old.reddit.com")
                .defaultHeader("User-Agent", redditProps.userAgent())
                .build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient(DynamoDbProperties dynamoProps) {
        var builder = DynamoDbClient.builder()
                .region(Region.of(dynamoProps.region()))
                .credentialsProvider(DefaultCredentialsProvider.create());

        // Only override endpoint for local DynamoDB (dev/testing)
        if (dynamoProps.endpoint() != null && !dynamoProps.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(dynamoProps.endpoint()));
        }

        return builder.build();
    }
}
