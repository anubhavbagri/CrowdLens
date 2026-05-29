package com.crowdlens.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

/**
 * WHAT IT DOES:
 * Central infrastructure configuration class for CrowdLens. Registers beans for HTTP WebClient clients
 * (both authenticated and public/scraper routes) and the AWS DynamoDB client.
 *
 * WHY IT'S NEEDED:
 * In standard Spring Boot applications, infrastructure objects like database clients and HTTP clients
 * need to be customized (e.g. timeout settings, custom headers, memory buffers) and shared globally as singletons.
 * This class isolates that initialization code.
 *
 * HOW IT WORKS:
 * - webClientBuilder(): Configures a reusable WebClient builder with a 10MB in-memory buffer limit to prevent DataBufferLimitException when fetching large JSON payloads from Reddit.
 * - redditWebClient(...): Constructs a builder pointing to official OAuth APIs.
 * - redditPublicWebClient(...): Points to old.reddit.com public endpoints for fallback scraping.
 * - dynamoDbClient(...): Initializes the AWS SDK DynamoDB client using standard local credentials providers, support for optional URI overrides for local LocalStack testing.
 *
 * WHERE IT'S USED:
 * Injected into {@link com.crowdlens.provider.reddit.RedditApiClient}, {@link com.crowdlens.provider.reddit.RedditJsonScraper}, and {@link com.crowdlens.service.CacheService}.
 *
 * SPRING ANNOTATIONS EXPLAINED:
 * - @Configuration: Indicates that the class declares @Bean methods.
 * - @EnableConfigurationProperties: Enables support for @ConfigurationProperties annotated classes, automatically registering RedditProperties, DynamoDbProperties, and RateLimitProperties as beans.
 * - @Bean: Defines instantiated services managed by the Spring IoC container.
 */
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


