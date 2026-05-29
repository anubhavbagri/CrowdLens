package com.crowdlens.config;

import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolvers;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WHAT IT DOES:
 * Configures the Lettuce client connection settings for Redis by forcing it to use JVM-level DNS resolution.
 *
 * WHY IT'S NEEDED:
 * By default, Lettuce (Spring Boot's default Redis driver) uses Netty's asynchronous DNS resolver. Netty's resolver
 * targets DNS servers directly, which inside Docker containers defaults to querying cgroups/Docker internal DNS at 127.0.0.11:53.
 * Under high loads or specific Linux network structures, this leads to connection timeouts and "Redis connection refused" exceptions.
 * Forcing JVM DNS resolution resolves hosts using Java's standard lookup, reading `/etc/resolv.conf` natively.
 *
 * HOW IT WORKS:
 * It defines a {@link LettuceClientConfigurationBuilderCustomizer} Bean that overrides Lettuce client resources.
 * Specifically, it maps the `.dnsResolver` property to {@link DnsResolvers#JVM_DEFAULT}.
 *
 * WHERE IT'S USED:
 * Automatically loaded by Spring Boot Auto-Configuration to customize the {@code RedisConnectionFactory}.
 *
 * SPRING ANNOTATIONS EXPLAINED:
 * - @Configuration: Declares this class as a source of bean definitions for the Spring Application Context.
 * - @Bean: Tells Spring that this method returns an object that should be registered as a bean in the context.
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceJvmDnsResolver() {
        return builder -> builder.clientResources(
                DefaultClientResources.builder()
                        .dnsResolver(DnsResolvers.JVM_DEFAULT)
                        .build()
        );
    }
}


